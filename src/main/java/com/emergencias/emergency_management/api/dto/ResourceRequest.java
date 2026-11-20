package com.emergencias.emergency_management.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Request body to register an emergency resource.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ResourceRequest {

    @NotBlank
    private String id;

    @NotBlank
    private String name;

    @NotBlank
    private String type;

    private boolean available;

    @NotBlank
    private String zoneCode;

}