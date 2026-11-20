package com.emergencias.emergency_management.api.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Response returned when an incident event has been accepted for asynchronous
 * processing.
 */
@Getter
@Builder
public class IncidentAcceptedResponse {

    private String incidentId;
    private String message;
}
