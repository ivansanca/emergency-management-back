package com.emergencias.emergency_management.service;

import com.emergencias.emergency_management.messaging.event.IncidentEvent;
import com.emergencias.emergency_management.messaging.producer.IncidentNotificationProducer;
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

        if (!result.created()) {
            log.info("Incidente {} ya registrado: se actualiza sin "
                    + "volver a notificar", event.getId());
            return;
        }

        incidentNotificationProducer.incidentCreated(result.incident());

        try {
            incidentNotificationProducer.incidentRecommendation(
                    decisionService.recommend(event.getId()));
        } catch (RuntimeException exception) {
            log.error("Incidente {} registrado, pero no se pudo calcular "
                    + "su recomendación", event.getId(), exception);
        }
    }
}
