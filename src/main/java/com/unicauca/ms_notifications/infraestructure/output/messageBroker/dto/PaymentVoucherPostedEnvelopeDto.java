package com.unicauca.ms_notifications.infraestructure.output.messageBroker.dto;

import java.time.Instant;

public record PaymentVoucherPostedEnvelopeDto(String eventId,String eventType,int eventVersion,Instant occurredAt,
        String tenantId,String enterpriseId,String correlationId,SourceDocument sourceDocument,
        PaymentVoucherPostedEventDto payload){public record SourceDocument(String type,Long id){}}
