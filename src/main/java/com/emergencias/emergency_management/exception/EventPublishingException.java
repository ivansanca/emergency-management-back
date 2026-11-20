package com.emergencias.emergency_management.exception;

/**
 * Kafka did not confirm an event in time. It is a temporary failure, so the
 * consumer retries it.
 */
public class EventPublishingException extends RuntimeException {

    public EventPublishingException(String topic, Throwable cause) {
        super("No se pudo publicar el evento en " + topic, cause);
    }
}
