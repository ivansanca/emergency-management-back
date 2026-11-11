package com.emergencias.emergency_management.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ResourceRecommendation {

    private String resourceId;
    private String name;
    private String type;
    private String reason;
    private String zoneName;
    private Double distanceKm;
    private Double travelTimeMinutes;
    private List<String> route;
}