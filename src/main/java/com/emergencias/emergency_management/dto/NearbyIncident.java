package com.emergencias.emergency_management.dto;

import com.emergencias.emergency_management.model.IncidentSource;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

/**
 * Open incident close to a new report, returned to the operator so that they
 * can decide whether the report is the same emergency or a new one.
 */
@Getter
@Builder
public class NearbyIncident {

    private String incidentId;
    private String type;
    private int severity;
    private IncidentSource source;
    private long detectionCount;
    private Instant lastDetectedAt;

    /** Distance from the report to the nearest detection of the incident. */
    private long distanceMeters;
}
