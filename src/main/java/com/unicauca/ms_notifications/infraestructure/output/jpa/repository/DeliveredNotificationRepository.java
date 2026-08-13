package com.unicauca.ms_notifications.infraestructure.output.jpa.repository;

import com.unicauca.ms_notifications.infraestructure.output.jpa.entity.DeliveredNotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveredNotificationRepository extends JpaRepository<DeliveredNotificationEntity, Long> {
    boolean existsByEventIdAndRecipientId(String eventId, Long recipientId);
}
