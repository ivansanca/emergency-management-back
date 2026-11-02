package com.emergencias.emergency_management.consumer;

import com.emergencias.emergency_management.config.KafkaTopics;
import com.emergencias.emergency_management.model.IncidentEvent;
import com.emergencias.emergency_management.service.IncidentGraphService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class IncidentConsumer {

    private final IncidentGraphService incidentGraphService;
    private final JsonMapper jsonMapper;

    @KafkaListener(topics = KafkaTopics.EMERGENCY_EVENTS)
    public void consume(String message) {

        IncidentEvent event =
                jsonMapper.readValue(message, IncidentEvent.class);

        incidentGraphService.save(event);

        log.info("Incidente procesado: {} ({}, gravedad {})",
                event.getId(), event.getType(), event.getSeverity());
    }
}
