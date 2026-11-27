package com.emergencias.emergency_management.messaging.config;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Names of the Kafka topics used by the service.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class KafkaTopics {

    public static final String EMERGENCY_EVENTS = "emergency-events";
    public static final String EMERGENCY_EVENTS_DLT = "emergency-events-dlt";

    /**
     * Incidents reported by an operator. They have their own topic and
     * consumer so that they never wait behind thousands of automatic
     * detections: Kafka does not prioritise records, so priority comes from
     * isolation.
     */
    public static final String EMERGENCY_EVENTS_MANUAL = "emergency-events-manual";
    public static final String EMERGENCY_EVENTS_MANUAL_DLT = "emergency-events-manual-dlt";

    public static final String INCIDENT_CREATED = "incident-created";
    public static final String INCIDENT_RECOMMENDATION = "incident-recommendation";
    public static final String RESOURCE_ASSIGNED = "resource-assigned";
    public static final String INCIDENT_CLOSED = "incident-closed";
}
