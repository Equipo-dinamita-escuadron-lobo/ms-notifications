package com.unicauca.ms_notifications.infraestructure;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.unicauca.ms_notifications.application.output.IEmailProviderPort;
import com.unicauca.ms_notifications.infraestructure.output.jpa.entity.ThirdReplicaEntity;
import com.unicauca.ms_notifications.infraestructure.output.jpa.repository.DeliveredNotificationRepository;
import com.unicauca.ms_notifications.infraestructure.output.jpa.repository.IThirdReplicaRepository;
import com.unicauca.ms_notifications.infraestructure.output.messageBroker.dto.PaymentVoucherPostedEventDto;
import com.unicauca.ms_notifications.infraestructure.output.messageBroker.dto.PaymentVoucherPostedEnvelopeDto;
import com.unicauca.ms_notifications.infraestructure.output.messageBroker.listener.PaymentNotificationListener;
import com.unicauca.ms_notifications.infraestructure.output.multitenancy.utils.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

class PaymentNotificationListenerTest {
    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final IThirdReplicaRepository thirds = mock(IThirdReplicaRepository.class);
    private final DeliveredNotificationRepository deliveries = mock(DeliveredNotificationRepository.class);
    private final IEmailProviderPort email = mock(IEmailProviderPort.class);
    private final PaymentNotificationListener listener =
            new PaymentNotificationListener(mapper, thirds, deliveries, email);

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void recordsEventBeforeSendingAndDoesNotSendDuplicateEmail() throws Exception {
        ThirdReplicaEntity supplier = new ThirdReplicaEntity();
        supplier.setThirdId(77L);
        supplier.setFullName("Proveedor Uno");
        supplier.setEmail("proveedor@example.test");
        supplier.setActive(true);
        when(thirds.findByThirdId(77L)).thenReturn(Optional.of(supplier));
        when(deliveries.existsByEventIdAndRecipientId("event-1", 77L)).thenReturn(false, true);
        Message message = message();

        TenantContext.setTenantId("tenant-a");
        listener.receive(message);
        TenantContext.setTenantId("tenant-a");
        listener.receive(message);

        verify(email, times(1)).sendPaymentConfirmationEmail(
                eq("Proveedor Uno"), eq("proveedor@example.test"), contains("CE-100"));
        var order = inOrder(deliveries, email);
        order.verify(deliveries).saveAndFlush(org.mockito.ArgumentMatchers.any());
        order.verify(email).sendPaymentConfirmationEmail(
                eq("Proveedor Uno"), eq("proveedor@example.test"), org.mockito.ArgumentMatchers.anyString());
    }

    private Message message() throws Exception {
        var event = new PaymentVoucherPostedEventDto(100L, "CE-100", "enterprise-a",
                LocalDate.of(2026, 8, 10), new BigDecimal("35.00"),
                "tenant-a",
                List.of(new PaymentVoucherPostedEventDto.Detail(
                        77L, 900L, "FC-900", new BigDecimal("35.00"))));
        var envelope = new PaymentVoucherPostedEnvelopeDto("event-1", "PAYMENT_VOUCHER_POSTED", 1,
                Instant.parse("2026-08-10T12:00:00Z"), "tenant-a", "enterprise-a", "correlation-1",
                new PaymentVoucherPostedEnvelopeDto.SourceDocument("PAYMENT_VOUCHER", 100L), event);
        MessageProperties properties = new MessageProperties();
        properties.setHeader("tenantId", "tenant-a");
        properties.setHeader("eventId", "event-1");
        return new Message(mapper.writeValueAsBytes(envelope), properties);
    }
}
