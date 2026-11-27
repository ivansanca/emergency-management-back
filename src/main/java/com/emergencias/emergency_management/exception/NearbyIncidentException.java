package com.emergencias.emergency_management.exception;

import com.emergencias.emergency_management.dto.NearbyIncident;
import lombok.Getter;

/**
 * A new report is close to an open incident of the same type. The operator
 * has to confirm whether it is a new emergency or the same one, so the report
 * is not registered yet.
 * <p>
 * It carries the id of the report, which the API generates when the client
 * does not send one: sending it again when confirming, or when adding the
 * report to the incident, links both requests to the same report.
 */
@Getter
public class NearbyIncidentException extends ConflictException {

    private final transient NearbyIncident nearbyIncident;
    private final String reportId;

    public NearbyIncidentException(NearbyIncident nearbyIncident, String reportId) {
        super("Ya hay un incidente " + nearbyIncident.getType()
                + " abierto a " + nearbyIncident.getDistanceMeters() + " m ("
                + nearbyIncident.getIncidentId() + ", "
                + detections(nearbyIncident.getDetectionCount()) + "). "
                + "Si es otra emergencia, repite el alta con confirmNew=true; "
                + "si es la misma, añade el aviso con POST /api/incidents/"
                + nearbyIncident.getIncidentId() + "/detections.");
        this.nearbyIncident = nearbyIncident;
        this.reportId = reportId;
    }

    private static String detections(long count) {
        return count == 1 ? "1 detección" : count + " detecciones";
    }
}
