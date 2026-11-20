package com.emergencias.emergency_management.messaging.consumer;

import com.emergencias.emergency_management.messaging.config.KafkaTopics;
import com.emergencias.emergency_management.messaging.event.IncidentEvent;
import com.emergencias.emergency_management.service.IncidentProcessingService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumes incident events from Kafka, whatever their source, and hands them
 * over for processing.
 * <p>
 * Exceptions are deliberately not caught here: the error handler decides
 * whether to retry the event or to send it to the dead letter topic.
 */
@Component
@RequiredArgsConstructor
public class IncidentConsumer {

    private final IncidentProcessingService incidentProcessingService;
    private final JsonMapper jsonMapper;

    @KafkaListener(topics = KafkaTopics.EMERGENCY_EVENTS)
    public void consume(String message) {

        IncidentEvent event =
                jsonMapper.readValue(message, IncidentEvent.class);

        incidentProcessingService.process(event);
    }
}
