package com.emergencias.emergency_management.api;

import com.emergencias.emergency_management.api.dto.AssignmentRequest;
import com.emergencias.emergency_management.api.dto.DetectionRequest;
import com.emergencias.emergency_management.dto.AssignmentResponse;
import com.emergencias.emergency_management.dto.CloseIncidentResponse;
import com.emergencias.emergency_management.dto.DetectionAddedResponse;
import com.emergencias.emergency_management.service.IncidentLifecycleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for the decisions an operator takes on an incident: assigning
 * resources, adding a report to it and closing it.
 */
@RestController
@RequestMapping("/api/incidents")
@RequiredArgsConstructor
public class IncidentLifecycleController {

    private final IncidentLifecycleService incidentLifecycleService;

    @PostMapping("/{incidentId}/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    public AssignmentResponse assign(
            @PathVariable String incidentId,
            @Valid @RequestBody AssignmentRequest request) {

        return incidentLifecycleService.assign(
                incidentId, request.getResourceId());
    }

    /**
     * Adds a report to the incident. Answers 201 when it is added and 200
     * when the same report had already been added.
     */
    @PostMapping("/{incidentId}/detections")
    public ResponseEntity<DetectionAddedResponse> addReport(
            @PathVariable String incidentId,
            @RequestBody DetectionRequest request) {

        DetectionAddedResponse response = incidentLifecycleService.addReport(
                incidentId,
                request.getId(),
                request.getLatitude(),
                request.getLongitude(),
                request.getOccurredAt());

        return ResponseEntity
                .status(response.isAdded() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(response);
    }

    @PostMapping("/{incidentId}/close")
    public CloseIncidentResponse close(
            @PathVariable String incidentId) {

        return incidentLifecycleService.close(incidentId);
    }
}
