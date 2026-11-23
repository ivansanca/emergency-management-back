package com.emergencias.emergency_management.exception;

/**
 * Kafka did not confirm an event: the broker is down, the topic is missing,
 * the service is not authorised or the wait ran out. The event itself is
 * fine and will go through once the cause is fixed, so it is a temporary
 * failure: the consumer retries it without limit and the REST API answers
 * 503. When Kafka rejects the event itself, {@link EventRejectedException} is
 * thrown instead.
 */
public class EventPublishingException extends RuntimeException {

    public EventPublishingException(String topic, Throwable cause) {
        super("No se pudo publicar el evento en " + topic, cause);
    }
}
