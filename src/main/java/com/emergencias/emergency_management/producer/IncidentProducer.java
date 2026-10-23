package com.emergencias.emergency_management.producer;

import com.emergencias.emergency_management.model.IncidentEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
public class IncidentProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;

    public IncidentProducer(
            KafkaTemplate<String, String> kafkaTemplate,
            JsonMapper jsonMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.jsonMapper = jsonMapper;
    }

    public void publish(IncidentEvent event) {

        String message = jsonMapper.writeValueAsString(event);

        kafkaTemplate.send(
                "emergency-events",
                event.getId(),
                message
        );
    }
}