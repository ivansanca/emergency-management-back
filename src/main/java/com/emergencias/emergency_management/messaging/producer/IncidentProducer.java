package com.emergencias.emergency_management.messaging.producer;

import com.emergencias.emergency_management.messaging.config.KafkaTopics;
import com.emergencias.emergency_management.messaging.event.IncidentEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publishes the incidents reported through the API to the
 * {@code emergency-events-manual} topic and waits until Kafka confirms them.
 * Automatic detections arrive through {@code emergency-events} instead, so a
 * report from an operator never waits behind them.
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
                KafkaTopics.EMERGENCY_EVENTS_MANUAL,
                event.getId(),
                message);
    }
}
