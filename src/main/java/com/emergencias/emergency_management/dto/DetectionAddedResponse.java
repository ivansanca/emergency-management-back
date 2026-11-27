package com.emergencias.emergency_management.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Result of adding a report of an operator to an existing incident.
 */
@Getter
@Builder
public class DetectionAddedResponse {

    private String incidentId;
    private String detectionId;
    private long detectionCount;

    /** {@code false} when the report had already been added. */
    private boolean added;
}
