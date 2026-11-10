package com.emergencias.emergency_management.controller;

import com.emergencias.emergency_management.dto.DecisionResponse;
import com.emergencias.emergency_management.service.DecisionService;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

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