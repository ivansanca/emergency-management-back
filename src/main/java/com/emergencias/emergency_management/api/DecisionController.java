package com.emergencias.emergency_management.api;

import com.emergencias.emergency_management.dto.DecisionResponse;
import com.emergencias.emergency_management.service.DecisionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoint to query the resource recommendation of an incident. It is a
 * read-only query: it never publishes notifications.
 */
@RestController
@RequestMapping("/api/incidents")
@RequiredArgsConstructor
public class DecisionController {

    private final DecisionService decisionService;

    @GetMapping("/{incidentId}/recommendations")
    public DecisionResponse recommend(
            @PathVariable String incidentId) {

        return decisionService.recommend(incidentId);
    }
}