package com.emergencias.emergency_management.exception;

public class EventPublishingException extends RuntimeException {

    public EventPublishingException(String topic, Throwable cause) {
        super("No se pudo publicar el evento en " + topic, cause);
    }
}
