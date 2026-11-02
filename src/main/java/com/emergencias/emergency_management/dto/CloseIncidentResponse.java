package com.emergencias.emergency_management.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CloseIncidentResponse {

    private String incidentId;
    private String status;
    private long releasedResources;
}
