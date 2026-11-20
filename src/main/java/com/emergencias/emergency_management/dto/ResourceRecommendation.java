package com.emergencias.emergency_management.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * A recommended resource with its route, distance, travel time and the reason
 * why it is recommended. Distance and travel time are null for aerial
 * resources.
 */
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