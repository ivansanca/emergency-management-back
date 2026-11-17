package com.emergencias.emergency_management.exception;

/**
 * The incident event is not valid. Retrying would not fix it, so the consumer
 * sends it straight to the dead letter topic.
 */
public class InvalidIncidentEventException extends RuntimeException {

    public InvalidIncidentEventException(String message) {
        super(message);
    }
}
