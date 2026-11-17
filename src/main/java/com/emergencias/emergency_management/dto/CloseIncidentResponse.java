package com.emergencias.emergency_management.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Result of closing an incident. It is also the payload of the {@code
 * incident-closed} notification.
 */
@Getter
@Builder
public class CloseIncidentResponse {

    private String incidentId;
    private String status;
    private long releasedResources;
}
