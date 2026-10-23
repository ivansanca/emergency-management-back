package com.emergencias.emergency_management.consumer;

import com.emergencias.emergency_management.model.IncidentEvent;
import com.emergencias.emergency_management.service.IncidentGraphService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class IncidentConsumer {

    private final IncidentGraphService incidentGraphService;
    private final JsonMapper jsonMapper;

    public IncidentConsumer(
            IncidentGraphService incidentGraphService,
            JsonMapper jsonMapper) {
        this.incidentGraphService = incidentGraphService;
        this.jsonMapper = jsonMapper;
    }

    @KafkaListener(topics = "emergency-events")
    public void consume(String message) {

        IncidentEvent event =
                jsonMapper.readValue(message, IncidentEvent.class);

        incidentGraphService.save(event);

        System.out.println(
                "Incidente procesado: " + event.getId());
    }
}