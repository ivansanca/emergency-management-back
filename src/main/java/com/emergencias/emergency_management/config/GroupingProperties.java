package com.emergencias.emergency_management.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * How automatic detections are grouped into incidents ({@code app.grouping}).
 * <p>
 * The radius depends on the incident type: a forest fire seen from a
 * satellite spreads over kilometres and its pixels measure 375 m to 1 km,
 * while two structure fires in neighbouring streets are different
 * emergencies. Types without a radius are never grouped.
 */
@Component
@ConfigurationProperties(prefix = "app.grouping")
@Getter
@Setter
@Validated
public class GroupingProperties {

    /** Maximum distance to any detection of the incident, by incident type. */
    private Map<String, @Positive Double> radiusMeters = new HashMap<>();

    /** Maximum time between the new detection and one of the incident. */
    @NotNull
    private Duration window = Duration.ofHours(24);

    public Optional<Double> radiusFor(String incidentType) {
        return Optional.ofNullable(radiusMeters.get(incidentType));
    }
}
