package com.unicauca.ms_notifications.infraestructure.output.messageBroker.listener;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;
import com.unicauca.ms_notifications.domain.exception.ValidationException;
import com.unicauca.ms_notifications.domain.ports.IMessageErrorHandlingPort;
import com.unicauca.ms_notifications.infraestructure.config.RabbitThirdsEventsConfig;
import com.unicauca.ms_notifications.infraestructure.output.jpa.entity.ThirdReplicaEntity;
import com.unicauca.ms_notifications.infraestructure.output.jpa.repository.IThirdReplicaRepository;
import com.unicauca.ms_notifications.infraestructure.output.messageBroker.base.AbstractMessageListener;
import com.unicauca.ms_notifications.infraestructure.output.messageBroker.dto.EventDtoThird;
import com.unicauca.ms_notifications.infraestructure.output.messageBroker.dto.ThirdUpdatedEventDto;
import com.unicauca.ms_notifications.infraestructure.output.messageBroker.utils.JsonUtils;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * @brief Listener to handle third party events from RabbitMQ.
 * It processes events related to third party updates.
 * It extends AbstractMessageListener to leverage common message handling logic.
 */

@Component
@RequiredArgsConstructor
@Slf4j
public class ThirdEventListener extends AbstractMessageListener<EventDtoThird<ThirdUpdatedEventDto, String>> {

    private final IThirdReplicaRepository thirdReplicaRepository;

    private final IMessageErrorHandlingPort messageErrorHandlingPortImpl;

    @PostConstruct
    private void init() {
        this.messageErrorHandlingPort = messageErrorHandlingPortImpl;
    }

    /**
     * @brief Listens to the thirds queue and processes incoming third party events.
     * @param event The third party event received.
     * @param channel The RabbitMQ channel.
     * @param deliveryTag The delivery tag for message acknowledgment.
     */
    @RabbitListener(queues = RabbitThirdsEventsConfig.THIRD_UPDATED_QUEUE, containerFactory = "rabbitListenerContainerFactory")
    public void listenToThirdsQueue(
            Message message,
            EventDtoThird<ThirdUpdatedEventDto, String> event,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {

        log.info("Received event: '{}' for thirdId: {}",
                event.getType(),
                event.getData() != null ? event.getData().getThirdId() : "N/A");
        handleMessage(event, channel, deliveryTag);
    }

    /**
     * @brief Processes the third party event based on its type.
     * @param event The third party event to process.
     * @throws Exception if any error occurs during processing.
     */
    @Override
    protected void processEvent(EventDtoThird<ThirdUpdatedEventDto, String> event) {
        ThirdUpdatedEventDto thirdUpdatedDTO = event.getData();
        Long thirdId = thirdUpdatedDTO != null ? thirdUpdatedDTO.getThirdId() : null;

        try {
            switch (event.getType()) {
                case "THIRD_UPDATED":
                    ThirdReplicaEntity replica = thirdReplicaRepository.findByThirdId(thirdUpdatedDTO.getThirdId())
                            .orElse(new ThirdReplicaEntity());

                    replica.setThirdId(thirdUpdatedDTO.getThirdId());
                    replica.setEnterpriseId(thirdUpdatedDTO.getEntId());
                    replica.setFullName(thirdUpdatedDTO.getFullName());
                    replica.setEmail(thirdUpdatedDTO.getEmail());
                    replica.setIsActive(thirdUpdatedDTO.getState());

                    thirdReplicaRepository.save(replica);
                    log.info("Third replica for thirdId: {} synchronized successfully.", thirdUpdatedDTO.getThirdId());
                    break;
                default:
                    log.warn("Unhandled event type: {}. The message will be discarded.", event.getType());
                    break;
            }
            log.info("Event processing completed for thirdId: {}", thirdId);

        } catch (Exception e) {
            log.error("Database operation failed for thirdId: {}. Error: {}", thirdId, e.getMessage());
            throw e;
        }
    }

    /**
     * @brief Validates the structure and content of the third party event.
     * @param event The third party event to validate.
     * @throws ValidationException if validation fails.
     */
    @Override
    protected void validateEvent(EventDtoThird<ThirdUpdatedEventDto, String> event) throws ValidationException {
        if (event == null) {
            throw new ValidationException("Validation failed: Event is null");
        }
        if (event.getData() == null) {
            throw new ValidationException("Validation failed: Event data is null");
        }
        if (event.getType() == null) {
            throw new ValidationException("Validation failed: Event type is null");
        }

        ThirdUpdatedEventDto data = event.getData();
        if (data.getThirdId() == null) {
            throw new ValidationException("Validation failed: thirdId is null");
        }
        if (data.getEntId() == null) {
            throw new ValidationException("Validation failed: entId is null");
        }
        if (data.getFullName() == null || data.getFullName().isBlank()) {
            throw new ValidationException("Validation failed: fullName is null or blank");
        }
        if (data.getEmail() == null || data.getEmail().isBlank()) {
            throw new ValidationException("Validation failed: email is null or blank");
        }
        if (data.getState() == null) {
            throw new ValidationException("Validation failed: state is null");
        }
        // Si todo está bien, el método termina sin lanzar una excepción.
    }

    /**
     * @brief Returns the entity type for this listener.
     * @return The entity type as a string.
     */
    @Override
    protected String getEntityType() {
        return "ThirdParty.Sync";
    }

    /**
     * @brief Extracts the event type from the third party event.
     * @param event The third party event.
     * @return The event type as a string.
     */
    @Override
    protected String extractEventType(EventDtoThird<ThirdUpdatedEventDto, String> event) {
        return event != null ? event.getType() : "unknown";
    }

    @Override
    protected String convertEventToJson(EventDtoThird<ThirdUpdatedEventDto, String> event) {
        if (event == null) {
            return "{\"error\": \"Event is null\"}";
        }

        if (event.getData() == null) {
            return "{\"error\": \"Event data is null\", \"eventType\": \"" +
                    (event.getType() != null ? event.getType() : "null") + "\"}";
        }

        return JsonUtils.thirdDtoToJsonWithNullHandling(event.getData());
    }
}
