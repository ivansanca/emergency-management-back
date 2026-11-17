package com.emergencias.emergency_management.messaging.producer;

import com.emergencias.emergency_management.messaging.config.KafkaTopics;
import com.emergencias.emergency_management.messaging.event.IncidentEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
public class IncidentProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;

    public void publish(IncidentEvent event) {

        String message = jsonMapper.writeValueAsString(event);

        kafkaTemplate.send(
                KafkaTopics.EMERGENCY_EVENTS,
                event.getId(),
                message
        );
    }
}
