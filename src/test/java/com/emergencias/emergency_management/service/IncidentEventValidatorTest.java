package com.emergencias.emergency_management.service;

import com.emergencias.emergency_management.exception.InvalidIncidentEventException;
import com.emergencias.emergency_management.messaging.event.IncidentEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IncidentEventValidatorTest {

    private static IncidentEvent.IncidentEventBuilder validEvent() {
        return IncidentEvent.builder()
                .id("incident-1")
                .type("FOREST_FIRE")
                .severity(2);
    }

    @Test
    void acceptsEventWithZoneCode() {
        assertDoesNotThrow(() -> IncidentEventValidator.validate(
                validEvent().zoneCode("39014").build()));
    }

    @Test
    void acceptsEventWithCoordinatesInsideCantabria() {
        assertDoesNotThrow(() -> IncidentEventValidator.validate(
                validEvent().latitude(43.2265).longitude(-4.3016).build()));
    }

    @Test
    void acceptsEventWithZoneCodeAndCoordinates() {
        assertDoesNotThrow(() -> IncidentEventValidator.validate(
                validEvent().zoneCode("39014")
                        .latitude(43.2265).longitude(-4.3016).build()));
    }

    @Test
    void acceptsEventWithCurrentSchemaVersion() {
        assertDoesNotThrow(() -> IncidentEventValidator.validate(
                validEvent().zoneCode("39014")
                        .schemaVersion(IncidentEvent.CURRENT_SCHEMA_VERSION)
                        .build()));
    }

    @Test
    void rejectsInvalidEvents() {
        List<IncidentEvent> invalidEvents = List.of(
                validEvent().id(null).zoneCode("39014").build(),
                validEvent().id(" ").zoneCode("39014").build(),
                validEvent().type(null).zoneCode("39014").build(),
                validEvent().type(" ").zoneCode("39014").build(),
                validEvent().severity(null).zoneCode("39014").build(),
                validEvent().severity(-1).zoneCode("39014").build(),
                validEvent().severity(5).zoneCode("39014").build(),
                validEvent().zoneCode("39014").schemaVersion(2).build(),
                validEvent().build(),
                validEvent().zoneCode(" ").build(),
                validEvent().latitude(43.2265).build(),
                validEvent().longitude(-4.3016).build(),
                validEvent().latitude(40.4168).longitude(-3.7038).build(),
                validEvent().zoneCode("39014")
                        .latitude(43.2265).longitude(-2.9350).build());

        assertThrows(
                InvalidIncidentEventException.class,
                () -> IncidentEventValidator.validate(null));
        for (IncidentEvent event : invalidEvents) {
            assertThrows(
                    InvalidIncidentEventException.class,
                    () -> IncidentEventValidator.validate(event));
        }
    }
}
