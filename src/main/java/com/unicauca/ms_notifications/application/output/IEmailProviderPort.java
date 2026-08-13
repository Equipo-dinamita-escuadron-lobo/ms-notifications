package com.unicauca.ms_notifications.application.output;

/**
 * @brief Port interface for sending emails.
 * Defines the contract for email notification services.
 */

public interface IEmailProviderPort {
    void sendInvoiceReminderEmail(String recipientName, String recipientEmail, String htmlContent);
    void sendPaymentConfirmationEmail(String recipientName, String recipientEmail, String htmlContent);
}
