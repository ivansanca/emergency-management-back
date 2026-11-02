package com.emergencias.emergency_management.controller;

import com.emergencias.emergency_management.dto.AssignmentResponse;
import com.emergencias.emergency_management.dto.CloseIncidentResponse;
import com.emergencias.emergency_management.model.AssignmentRequest;
import com.emergencias.emergency_management.service.IncidentLifecycleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/{incidentId}/close")
    public CloseIncidentResponse close(
            @PathVariable String incidentId) {

        return incidentLifecycleService.close(incidentId);
    }
}
