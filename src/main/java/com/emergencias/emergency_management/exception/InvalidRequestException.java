package com.emergencias.emergency_management.exception;

/**
 * A request refers to data that does not exist or is not valid.
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
