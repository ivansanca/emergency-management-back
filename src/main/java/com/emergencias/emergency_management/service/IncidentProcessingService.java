package com.emergencias.emergency_management.service;

import com.emergencias.emergency_management.messaging.event.IncidentEvent;
import com.emergencias.emergency_management.messaging.producer.IncidentNotificationProducer;
import com.emergencias.emergency_management.model.Incident;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class IncidentProcessingService {

    private final IncidentGraphService incidentGraphService;
    private final DecisionService decisionService;
    private final IncidentNotificationProducer incidentNotificationProducer;

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
