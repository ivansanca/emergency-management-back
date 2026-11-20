package com.emergencias.emergency_management.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * Recommendation for an incident: the rule applied, the recommended resources
 * and the requirements that could not be covered. It is also the payload of
 * the {@code incident-recommendation} notification.
 */
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