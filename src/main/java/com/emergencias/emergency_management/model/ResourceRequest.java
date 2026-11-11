package com.emergencias.emergency_management.model;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

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