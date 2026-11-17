package com.emergencias.emergency_management.messaging.event;

import com.emergencias.emergency_management.model.Incident;
import com.emergencias.emergency_management.model.IncidentSource;
import com.emergencias.emergency_management.model.IncidentStatus;
import com.emergencias.emergency_management.model.Zone;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
public class IncidentCreatedPayload {

    private String type;
    private int severity;
    private IncidentStatus status;
    private String zoneCode;
    private String zoneName;
    private Double latitude;
    private Double longitude;
    private Instant occurredAt;
    private Instant receivedAt;
    private IncidentSource source;
    private String description;

    public static IncidentCreatedPayload from(Incident incident) {
        Zone zone = incident.getZone();

        return IncidentCreatedPayload.builder()
                .type(incident.getType())
                .severity(incident.getSeverity())
                .status(incident.getStatus())
                .zoneCode(zone != null ? zone.getIneCode() : null)
                .zoneName(zone != null ? zone.getName() : null)
                .latitude(incident.getLatitude())
                .longitude(incident.getLongitude())
                .occurredAt(incident.getOccurredAt())
                .receivedAt(incident.getReceivedAt())
                .source(incident.getSource())
                .description(incident.getDescription())
                .build();
    }
}
