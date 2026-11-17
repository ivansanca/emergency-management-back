package com.emergencias.emergency_management.messaging.event;

import com.emergencias.emergency_management.model.IncidentSource;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentEvent {

    private String id;

    @NotBlank
    private String type;

    @Min(0)
    @Max(4)
    private int severity;

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
