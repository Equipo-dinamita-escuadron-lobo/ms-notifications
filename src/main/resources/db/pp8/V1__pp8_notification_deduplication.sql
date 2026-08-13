ALTER TABLE delivered_notifications DROP CONSTRAINT IF EXISTS uk_delivered_notification_event_recipient;
CREATE UNIQUE INDEX IF NOT EXISTS uk_delivered_notification_tenant_event_recipient
    ON delivered_notifications(tenant_id,event_id,recipient_id);
