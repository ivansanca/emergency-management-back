package com.emergencias.emergency_management.exception;

/**
 * The operation is not allowed in the current state, for instance because the
 * incident is closed or the resource is not available.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
