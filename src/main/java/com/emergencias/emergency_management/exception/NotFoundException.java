package com.emergencias.emergency_management.exception;

/**
 * The requested incident or resource does not exist.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
