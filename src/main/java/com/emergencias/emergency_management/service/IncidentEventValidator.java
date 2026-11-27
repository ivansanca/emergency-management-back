package com.emergencias.emergency_management.service;

import com.emergencias.emergency_management.exception.InvalidIncidentEventException;
import com.emergencias.emergency_management.messaging.event.IncidentEvent;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Validation shared by the REST adapter and the Kafka consumer. Other
 * producers can write to the topic, so the consumer cannot rely on the checks
 * made by the REST API.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class IncidentEventValidator {

    static final double MIN_LATITUDE = 42.65;
    static final double MAX_LATITUDE = 43.60;
    static final double MIN_LONGITUDE = -4.95;
    static final double MAX_LONGITUDE = -3.05;

    public static void validate(IncidentEvent event) {

        if (event == null
                || isBlank(event.getId())
                || isBlank(event.getType())) {

            throw new InvalidIncidentEventException(
                    "Los datos del incidente no son válidos");
        }

        if (event.getSeverity() == null) {
            throw new InvalidIncidentEventException(
                    "Hay que indicar la gravedad del incidente");
        }

        if (event.getSeverity() < 0 || event.getSeverity() > 4) {
            throw new InvalidIncidentEventException(
                    "La gravedad debe estar entre 0 y 4");
        }

        if (event.getSchemaVersion() != null
                && event.getSchemaVersion() != IncidentEvent.CURRENT_SCHEMA_VERSION) {

            throw new InvalidIncidentEventException(
                    "Versión de esquema no soportada: "
                            + event.getSchemaVersion());
        }

        if ((event.getLatitude() == null) != (event.getLongitude() == null)) {
            throw new InvalidIncidentEventException(
                    "Hay que indicar latitud y longitud a la vez");
        }

        if (!event.hasZoneCode() && !event.hasCoordinates()) {
            throw new InvalidIncidentEventException(
                    "Hay que indicar el código de zona o las coordenadas");
        }

        if (event.hasCoordinates() && !insideCantabria(
                event.getLatitude(), event.getLongitude())) {
            throw new InvalidIncidentEventException(
                    "Las coordenadas están fuera de Cantabria");
        }
    }

    /**
     * Bounding box around Cantabria with some margin. It is a coarse filter:
     * it also contains border areas of the neighbouring provinces.
     */
    static boolean insideCantabria(double latitude, double longitude) {
        return latitude >= MIN_LATITUDE && latitude <= MAX_LATITUDE
                && longitude >= MIN_LONGITUDE && longitude <= MAX_LONGITUDE;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
