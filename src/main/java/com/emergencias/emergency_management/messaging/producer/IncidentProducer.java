package com.emergencias.emergency_management.messaging.producer;

import com.emergencias.emergency_management.messaging.config.KafkaTopics;
import com.emergencias.emergency_management.messaging.event.IncidentEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publishes incoming incident events to the {@code emergency-events} topic and
 * waits until Kafka confirms them.
 */
@Service
@RequiredArgsConstructor
public class IncidentProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;

    public void publish(IncidentEvent event) {

        String message = jsonMapper.writeValueAsString(event);

        SynchronousSend.sendAndWait(
                kafkaTemplate,
                KafkaTopics.EMERGENCY_EVENTS,
                event.getId(),
                message);
    }
}
