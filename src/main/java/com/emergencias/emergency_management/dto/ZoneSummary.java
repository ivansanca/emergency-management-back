package com.emergencias.emergency_management.dto;

import com.emergencias.emergency_management.model.Zone;

/**
 * A municipality, with the coordinates of its capital.
 */
public record ZoneSummary(
        String ineCode,
        String name,
        Double latitude,
        Double longitude
) {

    public static ZoneSummary from(Zone zone) {
        return new ZoneSummary(
                zone.getIneCode(), zone.getName(),
                zone.getLatitude(), zone.getLongitude());
    }
}
