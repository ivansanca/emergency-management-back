package com.emergencias.emergency_management.api;

import com.emergencias.emergency_management.api.dto.IncidentAcceptedResponse;
import com.emergencias.emergency_management.exception.NearbyIncidentException;
import com.emergencias.emergency_management.messaging.event.IncidentEvent;
import com.emergencias.emergency_management.messaging.producer.IncidentProducer;
import com.emergencias.emergency_management.model.IncidentSource;
import com.emergencias.emergency_management.service.IncidentEventValidator;
import com.emergencias.emergency_management.service.IncidentGraphService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST entry point for incidents reported by an operator.
 * <p>
 * It does not process the incident. It validates the event, publishes it to
 * Kafka and answers 202 once the broker has confirmed it, exactly as any other
 * event source would do. Events received here are always marked as manual.
 * <p>
 * Reports are never grouped with other incidents. Instead, when a report with
 * coordinates is close to an open incident of the same type, the API answers
 * 409 with that incident and registers nothing: the operator decides whether
 * it is a new emergency ({@code confirmNew=true}) or the same one, which is
 * added to it with {@code POST /api/incidents/{id}/detections}. The rule lives
 * here and not in a front end so that every client follows it and the radius
 * of each type is configured in one place.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/incidents")
public class IncidentController {

    private final IncidentProducer incidentProducer;
    private final IncidentGraphService incidentGraphService;

    @PostMapping
    public ResponseEntity<IncidentAcceptedResponse> create(
            @Valid @RequestBody IncidentEvent event,
            @RequestParam(defaultValue = "false") boolean confirmNew) {

        if (event.getId() == null || event.getId().isBlank()) {
            event.setId(UUID.randomUUID().toString());
        }

        event.setSource(IncidentSource.MANUAL);

        if (event.getSchemaVersion() == null) {
            event.setSchemaVersion(IncidentEvent.CURRENT_SCHEMA_VERSION);
        }

        IncidentEventValidator.validate(event);
        incidentGraphService.requireApplicableRule(event);

        if (!confirmNew) {
            incidentGraphService.findNearbyOpenIncident(event)
                    .ifPresent(nearby -> {
                        throw new NearbyIncidentException(nearby, event.getId());
                    });
        }

        incidentProducer.publish(event);

        return ResponseEntity.accepted()
                .body(IncidentAcceptedResponse.builder()
                        .incidentId(event.getId())
                        .message("Evento enviado a Kafka")
                        .build());
    }
}
