package com.emergencias.emergency_management.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class DecisionResponse {

    private String incidentId;
    private String incidentType;
    private int severity;
    private String ruleApplied;
    private List<ResourceRecommendation> recommendations;
    private List<ResourceShortfall> shortfalls;
}