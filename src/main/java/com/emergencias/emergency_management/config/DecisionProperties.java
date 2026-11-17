package com.emergencias.emergency_management.config;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Limits of the recommendation engine ({@code app.decision}). Ground resources
 * farther away by road than these values are not recommended.
 */
@Component
@ConfigurationProperties(prefix = "app.decision")
@Getter
@Setter
@Validated
public class DecisionProperties {

    @Positive
    private double maxDistanceKm = 60.0;

    @Positive
    private double maxTravelTimeMinutes = 60.0;
}