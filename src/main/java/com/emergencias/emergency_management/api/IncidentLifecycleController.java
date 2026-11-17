package com.emergencias.emergency_management.api;

import com.emergencias.emergency_management.api.dto.AssignmentRequest;
import com.emergencias.emergency_management.dto.AssignmentResponse;
import com.emergencias.emergency_management.dto.CloseIncidentResponse;
import com.emergencias.emergency_management.service.IncidentLifecycleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoints for the decisions an operator takes on an incident: assigning
 * resources and closing it.
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

    @PostMapping("/{incidentId}/close")
    public CloseIncidentResponse close(
            @PathVariable String incidentId) {

        return incidentLifecycleService.close(incidentId);
    }
}
