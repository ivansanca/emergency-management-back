package com.emergencias.emergency_management.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AssignmentResponse {

    private String incidentId;
    private String resourceId;
    private String resourceName;
    private String resourceType;
    private String assignedAt;
}
