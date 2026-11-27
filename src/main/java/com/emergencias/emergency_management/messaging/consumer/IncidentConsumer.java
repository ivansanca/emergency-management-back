package com.emergencias.emergency_management.messaging.consumer;

import com.emergencias.emergency_management.messaging.config.KafkaTopics;
import com.emergencias.emergency_management.messaging.event.IncidentEvent;
import com.emergencias.emergency_management.service.IncidentProcessingService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumes incident events from Kafka and hands them over for processing.
 * <p>
 * There are two listeners, each with its own consumer group and thread:
 * automatic detections from {@code emergency-events} and operator reports
 * from {@code emergency-events-manual}. A report is processed at once even
 * when thousands of detections are waiting in the other topic. Both run the
 * same processing; registrations do not interfere with each other because
 * {@code IncidentGraphService} serialises them.
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
        process(message);
    }

    @KafkaListener(
            topics = KafkaTopics.EMERGENCY_EVENTS_MANUAL,
            groupId = "emergency-management-manual")
    public void consumeManual(String message) {
        process(message);
    }

    private void process(String message) {

        IncidentEvent event =
                jsonMapper.readValue(message, IncidentEvent.class);

        incidentProcessingService.process(event);
    }
}
