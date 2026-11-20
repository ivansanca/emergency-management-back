package com.emergencias.emergency_management.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Request body to assign a resource to an incident.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentRequest {

    @NotBlank
    private String resourceId;
}
