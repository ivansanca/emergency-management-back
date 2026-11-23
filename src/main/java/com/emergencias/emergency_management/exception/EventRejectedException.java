package com.emergencias.emergency_management.exception;

/**
 * Kafka rejected the event itself, for instance because it is too large, so
 * sending it again can never work. Unlike {@link EventPublishingException} it
 * is not a temporary failure: the consumer sends the event to the dead letter
 * topic and the REST API answers 400.
 * <p>
 * It is a different class on purpose: Spring Kafka chooses how to retry a
 * record on its first failure and only chooses again when the type of the
 * exception changes.
 */
public class EventRejectedException extends RuntimeException {

    public EventRejectedException(String topic, Throwable cause) {
        super("Kafka ha rechazado el evento para " + topic, cause);
    }
}
