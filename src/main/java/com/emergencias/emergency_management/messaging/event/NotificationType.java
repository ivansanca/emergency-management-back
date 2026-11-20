package com.emergencias.emergency_management.messaging.event;

/**
 * Types of outgoing notifications, one per output topic.
 */
public enum NotificationType {
    INCIDENT_CREATED,
    INCIDENT_RECOMMENDATION,
    RESOURCE_ASSIGNED,
    INCIDENT_CLOSED
}
