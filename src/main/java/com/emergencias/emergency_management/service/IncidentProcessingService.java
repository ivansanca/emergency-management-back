package com.emergencias.emergency_management.service;

import com.emergencias.emergency_management.messaging.event.IncidentEvent;
import com.emergencias.emergency_management.messaging.producer.IncidentNotificationProducer;
import com.emergencias.emergency_management.model.Incident;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Orchestrates what happens when an incident event arrives: registering the
 * incident, notifying it and recommending resources.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IncidentProcessingService {

    private final IncidentGraphService incidentGraphService;
    private final DecisionService decisionService;
    private final IncidentNotificationProducer incidentNotificationProducer;

    /**
     * The incident is marked as notified only after Kafka has confirmed the
     * notification. If the service stops in between, the event is delivered
     * again, the incident is found pending and the notification is sent then.
     * Subscribers may therefore receive it twice, but never miss it.
     * <p>
     * The recommendation is best effort: if it fails, the error is logged and
     * the incident stays registered.
     */
    public void process(IncidentEvent event) {
        IncidentSaveResult result = incidentGraphService.save(event);
        Incident incident = result.incident();

        if (!result.created() && !pendingNotification(incident)) {
            log.info("Incidente {} ya registrado: se ignora el duplicado",
                    event.getId());
            return;
        }

        incidentNotificationProducer.incidentCreated(incident);
        incidentGraphService.markNotified(incident.getId());

        if (result.created()) {
            log.info("Incidente registrado y notificado: {} ({}, gravedad {})",
                    incident.getId(), incident.getType(),
                    incident.getSeverity());
        } else {
            log.info("Incidente {} ya registrado: se envía la notificación "
                    + "pendiente", incident.getId());
        }

        try {
            incidentNotificationProducer.incidentRecommendation(
                    decisionService.recommend(event.getId()));
        } catch (RuntimeException exception) {
            log.error("Incidente {} registrado, pero no se pudo calcular "
                    + "su recomendación", event.getId(), exception);
        }
    }

    private boolean pendingNotification(Incident incident) {
        return Boolean.FALSE.equals(incident.getNotified());
    }
}
