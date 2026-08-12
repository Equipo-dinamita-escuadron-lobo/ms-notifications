package com.unicauca.ms_notifications.infraestructure.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import lombok.extern.slf4j.Slf4j;

/**
 * @brief Configuration class for RabbitMQ notifications.
 * Defines exchanges, queues, and bindings for notification messages.
 * This configuration is excluded from the 'test' profile.
 */

@Configuration
@Slf4j
@Profile("!test")
public class RabbitNotificationsConfig {
    public static final String NOTIFICATIONS_EXCHANGE = "notifications.exchange";
    public static final String NOTIFICATIONS_QUEUE = "notifications.queue";
    public static final String PAYMENT_EXCHANGE = "payment.notification.exchange";
    public static final String PAYMENT_QUEUE = "payment.notification.queue";
    public static final String PAYMENT_DLX = "payment.notification.dlx";
    public static final String PAYMENT_DLQ = "payment.notification.dlq";

    // STATEMENT EXCHANGES
    @Bean
    FanoutExchange notificationsExchange() {
        return new FanoutExchange(NOTIFICATIONS_EXCHANGE, true, false);
    }

    // STATEMENT OF QUEUES AND BINDINGS
    @Bean
    Queue notificationsQueue() {
        return QueueBuilder.durable(NOTIFICATIONS_QUEUE).build();
    }

    @Bean
    Binding notificationsBinding(FanoutExchange notificationsExchange, Queue notificationsQueue) {
        return BindingBuilder.bind(notificationsQueue).to(notificationsExchange);
    }

    @Bean
    FanoutExchange paymentNotificationExchange() {
        return new FanoutExchange(PAYMENT_EXCHANGE, true, false);
    }

    @Bean
    FanoutExchange paymentNotificationDlx() {
        return new FanoutExchange(PAYMENT_DLX, true, false);
    }

    @Bean
    Queue paymentNotificationQueue() {
        return QueueBuilder.durable(PAYMENT_QUEUE).deadLetterExchange(PAYMENT_DLX).build();
    }

    @Bean
    Binding paymentNotificationBinding(FanoutExchange paymentNotificationExchange, Queue paymentNotificationQueue) {
        return BindingBuilder.bind(paymentNotificationQueue).to(paymentNotificationExchange);
    }

    @Bean
    Queue paymentNotificationDlq() {
        return QueueBuilder.durable(PAYMENT_DLQ).build();
    }

    @Bean
    Binding paymentNotificationDlqBinding(FanoutExchange paymentNotificationDlx, Queue paymentNotificationDlq) {
        return BindingBuilder.bind(paymentNotificationDlq).to(paymentNotificationDlx);
    }

    @Bean
    SimpleRabbitListenerContainerFactory paymentRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            SimpleRabbitListenerContainerFactoryConfigurer configurer) {
        var factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(500, 2.0, 5000)
                .recoverer(new RejectAndDontRequeueRecoverer())
                .build());
        return factory;
    }
}
