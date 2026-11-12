package com.emergencias.emergency_management.controller;

import com.emergencias.emergency_management.dto.IncidentAcceptedResponse;
import com.emergencias.emergency_management.model.IncidentEvent;
import com.emergencias.emergency_management.model.IncidentSource;
import com.emergencias.emergency_management.producer.IncidentProducer;
import com.emergencias.emergency_management.service.IncidentEventValidator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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

        if (event.getSource() == null) {
            event.setSource(IncidentSource.MANUAL);
        }

        try {
            IncidentEventValidator.validate(event);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, exception.getMessage());
        }

        incidentProducer.publish(event);

        return ResponseEntity.accepted()
                .body(IncidentAcceptedResponse.builder()
                        .incidentId(event.getId())
                        .message("Evento enviado a Kafka")
                        .build());
    }
}
