package com.emergencias.emergency_management.api;

import com.emergencias.emergency_management.exception.ConflictException;
import com.emergencias.emergency_management.exception.EventPublishingException;
import com.emergencias.emergency_management.exception.InvalidIncidentEventException;
import com.emergencias.emergency_management.exception.InvalidRequestException;
import com.emergencias.emergency_management.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler({
            InvalidIncidentEventException.class,
            InvalidRequestException.class
    })
    public ProblemDetail handleInvalid(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(EventPublishingException.class)
    public ProblemDetail handlePublishing(EventPublishingException exception) {
        log.error("Kafka no ha confirmado el evento", exception);

        return ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "No se ha podido registrar el evento. Inténtalo de nuevo.");
    }
}
