package com.emergencias.emergency_management.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IncidentEvent {

    @NotBlank
    private String id;

    @NotBlank
    private String type;

    @Min(1)
    @Max(5)
    private int severity;

    @NotBlank
    private String zone;

    private String description;

}