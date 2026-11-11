package com.emergencias.emergency_management.config;

import jakarta.validation.constraints.Positive;

import lombok.Getter;
import lombok.Setter;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

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