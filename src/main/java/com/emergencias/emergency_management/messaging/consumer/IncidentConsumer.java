package com.emergencias.emergency_management.messaging.consumer;

import com.emergencias.emergency_management.messaging.config.KafkaTopics;
import com.emergencias.emergency_management.messaging.event.IncidentEvent;
import com.emergencias.emergency_management.service.IncidentProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class IncidentConsumer {

    private final IncidentProcessingService incidentProcessingService;
    private final JsonMapper jsonMapper;

    @KafkaListener(topics = KafkaTopics.EMERGENCY_EVENTS)
    public void consume(String message) {

        IncidentEvent event =
                jsonMapper.readValue(message, IncidentEvent.class);

        incidentProcessingService.process(event);

        log.info("Incidente procesado: {} ({}, gravedad {})",
                event.getId(), event.getType(), event.getSeverity());
    }
}
