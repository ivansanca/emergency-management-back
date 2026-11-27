package com.emergencias.emergency_management.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Request body to add a report of an operator to an existing incident. Every
 * field is optional: the id is generated when missing, and a report without
 * coordinates is stored without a position.
 * <p>
 * When the report was first sent as a new incident and the API answered that
 * there was one nearby, sending the same id here links both: if the original
 * report is published later, it is recognised and ignored.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DetectionRequest {

    private String id;
    private Double latitude;
    private Double longitude;
    private Instant occurredAt;
}
