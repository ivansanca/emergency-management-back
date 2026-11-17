package com.emergencias.emergency_management.messaging.producer;

import com.emergencias.emergency_management.dto.AssignmentResponse;
import com.emergencias.emergency_management.dto.CloseIncidentResponse;
import com.emergencias.emergency_management.dto.DecisionResponse;
import com.emergencias.emergency_management.messaging.config.KafkaTopics;
import com.emergencias.emergency_management.messaging.event.IncidentCreatedPayload;
import com.emergencias.emergency_management.messaging.event.NotificationEnvelope;
import com.emergencias.emergency_management.messaging.event.NotificationType;
import com.emergencias.emergency_management.model.Incident;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

@Service
@RequiredArgsConstructor
@Slf4j
public class IncidentNotificationProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final JsonMapper jsonMapper;

    public void incidentCreated(Incident incident) {
        SynchronousSend.sendAndWait(
                kafkaTemplate,
                KafkaTopics.INCIDENT_CREATED,
                incident.getId(),
                toMessage(NotificationType.INCIDENT_CREATED,
                        incident.getId(),
                        IncidentCreatedPayload.from(incident)));
    }

    public void incidentRecommendation(DecisionResponse decision) {
        publish(KafkaTopics.INCIDENT_RECOMMENDATION,
                NotificationType.INCIDENT_RECOMMENDATION,
                decision.getIncidentId(),
                decision);
    }

    public void resourceAssigned(AssignmentResponse assignment) {
        publish(KafkaTopics.RESOURCE_ASSIGNED,
                NotificationType.RESOURCE_ASSIGNED,
                assignment.getIncidentId(),
                assignment);
    }

    public void incidentClosed(CloseIncidentResponse closure) {
        publish(KafkaTopics.INCIDENT_CLOSED,
                NotificationType.INCIDENT_CLOSED,
                closure.getIncidentId(),
                closure);
    }

    private void publish(
            String topic,
            NotificationType eventType,
            String incidentId,
            Object payload) {

        String message = toMessage(eventType, incidentId, payload);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            send(topic, incidentId, message);
                        }
                    });
            return;
        }

        send(topic, incidentId, message);
    }

    private String toMessage(
            NotificationType eventType,
            String incidentId,
            Object payload) {

        return jsonMapper.writeValueAsString(
                NotificationEnvelope.of(eventType, incidentId, payload));
    }

    private void send(String topic, String incidentId, String message) {
        kafkaTemplate.send(topic, incidentId, message)
                .whenComplete((result, exception) -> {
                    if (exception != null) {
                        log.error("No se pudo publicar en {} la notificación "
                                        + "del incidente {}",
                                topic, incidentId, exception);
                    }
                });
    }
}
