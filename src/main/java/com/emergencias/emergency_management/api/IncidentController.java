package com.emergencias.emergency_management.api;

import com.emergencias.emergency_management.api.dto.IncidentAcceptedResponse;
import com.emergencias.emergency_management.messaging.event.IncidentEvent;
import com.emergencias.emergency_management.messaging.producer.IncidentProducer;
import com.emergencias.emergency_management.model.IncidentSource;
import com.emergencias.emergency_management.service.IncidentEventValidator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/incidents")
public class IncidentController {

    private final IncidentProducer incidentProducer;

    @PostMapping
    public ResponseEntity<IncidentAcceptedResponse> create(
            @Valid @RequestBody IncidentEvent event) {

        if (event.getId() == null || event.getId().isBlank()) {
            event.setId(UUID.randomUUID().toString());
        }

        event.setSource(IncidentSource.MANUAL);

        if (event.getSchemaVersion() == null) {
            event.setSchemaVersion(IncidentEvent.CURRENT_SCHEMA_VERSION);
        }

        IncidentEventValidator.validate(event);

        incidentProducer.publish(event);

        return ResponseEntity.accepted()
                .body(IncidentAcceptedResponse.builder()
                        .incidentId(event.getId())
                        .message("Evento enviado a Kafka")
                        .build());
    }
}
