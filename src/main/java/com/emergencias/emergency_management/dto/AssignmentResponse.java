package com.emergencias.emergency_management.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Result of assigning a resource to an incident. It is also the payload of the
 * {@code resource-assigned} notification.
 */
@Getter
@Builder
public class AssignmentResponse {

    private String incidentId;
    private String resourceId;
    private String resourceName;
    private String resourceType;
    private String assignedAt;
}
