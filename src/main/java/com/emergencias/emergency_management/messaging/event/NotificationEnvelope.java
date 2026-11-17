package com.emergencias.emergency_management.messaging.event;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class NotificationEnvelope<T> {

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
                UUID.randomUUID().toString(),
                eventType,
                Instant.now(),
                incidentId,
                payload);
    }
}
