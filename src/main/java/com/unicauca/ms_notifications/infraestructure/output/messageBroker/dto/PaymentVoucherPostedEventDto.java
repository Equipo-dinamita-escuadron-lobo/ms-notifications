package com.unicauca.ms_notifications.infraestructure.output.messageBroker.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PaymentVoucherPostedEventDto(Long id, String voucherNumber, String enterpriseId,
        LocalDate issueDate, BigDecimal total, String tenantId, List<Detail> details) {
    public record Detail(Long supplierId, Long invoiceId, String invoiceReference, BigDecimal amountPaid) {}
}
