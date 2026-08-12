package com.unicauca.ms_notifications.infraestructure.output.messageBroker.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unicauca.ms_notifications.application.output.IEmailProviderPort;
import com.unicauca.ms_notifications.infraestructure.config.RabbitNotificationsConfig;
import com.unicauca.ms_notifications.infraestructure.output.jpa.entity.DeliveredNotificationEntity;
import com.unicauca.ms_notifications.infraestructure.output.jpa.repository.DeliveredNotificationRepository;
import com.unicauca.ms_notifications.infraestructure.output.jpa.repository.IThirdReplicaRepository;
import com.unicauca.ms_notifications.infraestructure.output.messageBroker.dto.PaymentVoucherPostedEventDto;
import com.unicauca.ms_notifications.infraestructure.output.messageBroker.dto.PaymentVoucherPostedEnvelopeDto;
import com.unicauca.ms_notifications.infraestructure.output.multitenancy.utils.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;
import java.time.Instant;
import java.util.stream.Collectors;

@Component
@Profile("!test")
@RequiredArgsConstructor
public class PaymentNotificationListener {
    private final ObjectMapper objectMapper;
    private final IThirdReplicaRepository thirds;
    private final DeliveredNotificationRepository deliveries;
    private final IEmailProviderPort emailProvider;

    @RabbitListener(queues = RabbitNotificationsConfig.PAYMENT_QUEUE,
            containerFactory = "paymentRabbitListenerContainerFactory")
    @Transactional
    public void receive(Message message) throws Exception {
        String tenantId = TenantContext.getTenantId();
        String eventId = String.valueOf(message.getMessageProperties().getHeaders().get("eventId"));
        if (tenantId == null || tenantId.isBlank() || "null".equals(tenantId) ||
                eventId == null || eventId.isBlank() || "null".equals(eventId)) {
            throw new IllegalArgumentException("El evento de pago no contiene eventId/tenantId");
        }
        try {
            var envelope = objectMapper.readValue(message.getBody(), PaymentVoucherPostedEnvelopeDto.class);
            if (!"PAYMENT_VOUCHER_POSTED".equals(envelope.eventType())) throw new SecurityException("Notificaciones solo consume comprobantes confirmados");
            if (!tenantId.equals(envelope.tenantId()) || envelope.payload() == null || !tenantId.equals(envelope.payload().tenantId())) throw new SecurityException("Tenant inconsistente en evento de notificacion");
            if (!eventId.equals(envelope.eventId())) throw new SecurityException("eventId inconsistente en evento de notificacion");
            var event = envelope.payload();
            var bySupplier = event.details().stream().collect(Collectors.groupingBy(PaymentVoucherPostedEventDto.Detail::supplierId));
            for (var group : bySupplier.entrySet()) {
                if (deliveries.existsByEventIdAndRecipientId(eventId, group.getKey())) continue;
                var third = thirds.findByThirdId(group.getKey())
                        .filter(t -> Boolean.TRUE.equals(t.getIsActive()) && t.getEmail() != null && !t.getEmail().isBlank())
                        .orElseThrow(() -> new IllegalArgumentException("Proveedor sin correo activo: " + group.getKey()));
                String rows = group.getValue().stream().map(d -> "<li>" + HtmlUtils.htmlEscape(d.invoiceReference()) +
                        ": " + d.amountPaid() + "</li>").collect(Collectors.joining());
                String html = "<h2>Pago confirmado</h2><p>" + HtmlUtils.htmlEscape(third.getFullName()) +
                        ", se confirmó el comprobante <strong>" + HtmlUtils.htmlEscape(event.voucherNumber()) +
                        "</strong> del " + event.issueDate() + " por " + event.total() + ".</p><ul>" + rows + "</ul>";
                var delivery = new DeliveredNotificationEntity();
                delivery.setEventId(eventId); delivery.setRecipientId(group.getKey());
                delivery.setDeliveredAt(Instant.now()); delivery.setTenantId(tenantId);
                deliveries.saveAndFlush(delivery);
                emailProvider.sendPaymentConfirmationEmail(third.getFullName(), third.getEmail(), html);
            }
        } finally {
            TenantContext.clear();
        }
    }
}
