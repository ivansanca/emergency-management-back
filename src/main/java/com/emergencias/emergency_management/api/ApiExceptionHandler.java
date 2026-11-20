package com.emergencias.emergency_management.api;

import com.emergencias.emergency_management.exception.ConflictException;
import com.emergencias.emergency_management.exception.EventPublishingException;
import com.emergencias.emergency_management.exception.InfrastructureFailures;
import com.emergencias.emergency_management.exception.InvalidIncidentEventException;
import com.emergencias.emergency_management.exception.InvalidRequestException;
import com.emergencias.emergency_management.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.transaction.TransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates domain exceptions into HTTP responses in Problem Details format
 * (RFC 9457), so that services do not need to know about HTTP.
 */
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

    /**
     * Errors of the data layer. When Neo4j cannot be reached the answer is
     * 503, so that the client knows it can try again later; Spring reports it
     * as a {@code TransactionSystemException}, so the cause chain is checked.
     * Any other data error is a bug and stays a 500.
     */
    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ProblemDetail handleDataAccess(RuntimeException exception) {
        if (InfrastructureFailures.isInfrastructureFailure(exception)) {
            log.error("Neo4j no está disponible", exception);

            return ProblemDetail.forStatusAndDetail(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "La base de datos no está disponible. "
                            + "Inténtalo de nuevo más tarde.");
        }

        log.error("Error inesperado de acceso a datos", exception);

        return ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Se ha producido un error interno.");
    }
}
