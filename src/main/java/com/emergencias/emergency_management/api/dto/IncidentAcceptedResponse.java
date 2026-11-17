package com.emergencias.emergency_management.api.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class IncidentAcceptedResponse {

    private String incidentId;
    private String message;
}
