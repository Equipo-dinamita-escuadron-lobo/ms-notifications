package com.unicauca.ms_notifications.infraestructure.output.jpa.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.TenantId;
import java.time.Instant;

@Entity
@Table(name = "delivered_notifications", uniqueConstraints =
        @UniqueConstraint(name = "uk_delivered_notification_tenant_event_recipient", columnNames = {"tenant_id", "event_id", "recipient_id"}))
@Getter @Setter
public class DeliveredNotificationEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;
    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;
    @Column(name = "delivered_at", nullable = false)
    private Instant deliveredAt;
    @TenantId
    @Column(name = "tenant_id", nullable = false, length = 80)
    private String tenantId;
}
