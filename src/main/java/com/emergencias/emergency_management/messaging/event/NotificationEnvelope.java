package com.emergencias.emergency_management.messaging.event;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Common envelope of every outgoing notification.
 * <p>
 * {@code eventId} identifies the notification itself. {@code incidentId} is
 * also used as the Kafka key, which keeps all the notifications of an incident
 * in order within a topic.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class NotificationEnvelope<T> {

    public static final int CURRENT_SCHEMA_VERSION = 1;

    private final int schemaVersion;
    private final String eventId;
    private final NotificationType eventType;
    private final Instant emittedAt;
    private final String incidentId;
    private final T payload;

    public static <T> NotificationEnvelope<T> of(
            NotificationType eventType,
            String incidentId,
            T payload) {

        return new NotificationEnvelope<>(
                CURRENT_SCHEMA_VERSION,
                UUID.randomUUID().toString(),
                eventType,
                Instant.now(),
                incidentId,
                payload);
    }
}
