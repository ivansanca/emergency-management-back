package com.emergencias.emergency_management.messaging.event;

import com.emergencias.emergency_management.model.IncidentSource;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Contract of the events of the {@code emergency-events} topic and body of
 * {@code POST /api/incidents}. The location is given by the zone code, by the
 * coordinates or by both.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentEvent {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    private Integer schemaVersion;

    private String id;

    @NotBlank
    private String type;

    @NotNull
    @Min(0)
    @Max(4)
    private Integer severity;

    private String zoneCode;

    private Double latitude;

    private Double longitude;

    private Instant occurredAt;

    private IncidentSource source;

    private String description;

    public boolean hasZoneCode() {
        return zoneCode != null && !zoneCode.isBlank();
    }

    public boolean hasCoordinates() {
        return latitude != null && longitude != null;
    }
}
