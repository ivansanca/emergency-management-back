package com.emergencias.emergency_management.controller;

import com.emergencias.emergency_management.model.IncidentEvent;
import com.emergencias.emergency_management.producer.IncidentProducer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/incidents")
public class IncidentController {

    private final IncidentProducer incidentProducer;

    @PostMapping
    public ResponseEntity<String> create(
            @Valid @RequestBody IncidentEvent event) {

        incidentProducer.publish(event);

        return ResponseEntity.accepted()
                .body("Evento enviado a Kafka");
    }
}