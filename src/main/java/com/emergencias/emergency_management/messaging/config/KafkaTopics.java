package com.emergencias.emergency_management.messaging.config;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class KafkaTopics {

    public static final String EMERGENCY_EVENTS = "emergency-events";
    public static final String EMERGENCY_EVENTS_DLT = "emergency-events-dlt";

    public static final String INCIDENT_CREATED = "incident-created";
    public static final String INCIDENT_RECOMMENDATION = "incident-recommendation";
    public static final String RESOURCE_ASSIGNED = "resource-assigned";
    public static final String INCIDENT_CLOSED = "incident-closed";
}
