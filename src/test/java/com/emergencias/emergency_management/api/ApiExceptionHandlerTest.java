package com.emergencias.emergency_management.api;

import com.emergencias.emergency_management.exception.ConflictException;
import com.emergencias.emergency_management.exception.EventPublishingException;
import com.emergencias.emergency_management.exception.InvalidIncidentEventException;
import com.emergencias.emergency_management.exception.InvalidRequestException;
import com.emergencias.emergency_management.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.neo4j.driver.exceptions.ServiceUnavailableException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.transaction.TransactionSystemException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void notFoundIsReportedAs404WithItsMessage() {
        ProblemDetail problem = handler.handleNotFound(
                new NotFoundException("No existe el incidente X"));

        assertEquals(HttpStatus.NOT_FOUND.value(), problem.getStatus());
        assertEquals("No existe el incidente X", problem.getDetail());
    }

    @Test
    void conflictIsReportedAs409WithItsMessage() {
        ProblemDetail problem = handler.handleConflict(
                new ConflictException("El recurso X no está disponible"));

        assertEquals(HttpStatus.CONFLICT.value(), problem.getStatus());
        assertEquals("El recurso X no está disponible", problem.getDetail());
    }

    @Test
    void invalidDataIsReportedAs400WithItsMessage() {
        ProblemDetail invalidEvent = handler.handleInvalid(
                new InvalidIncidentEventException("Evento no válido"));
        ProblemDetail invalidRequest = handler.handleInvalid(
                new InvalidRequestException("No existe la zona 99999"));

        assertEquals(HttpStatus.BAD_REQUEST.value(), invalidEvent.getStatus());
        assertEquals("Evento no válido", invalidEvent.getDetail());
        assertEquals(HttpStatus.BAD_REQUEST.value(), invalidRequest.getStatus());
        assertEquals("No existe la zona 99999", invalidRequest.getDetail());
    }

    @Test
    void publishingFailureIsReportedAs503WithoutInternalDetails() {
        ProblemDetail problem = handler.handlePublishing(
                new EventPublishingException(
                        "emergency-events",
                        new IllegalStateException("broker down")));

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE.value(), problem.getStatus());
        assertFalse(problem.getDetail().contains("broker down"));
    }

    @Test
    void databaseDownIsReportedAs503WithoutInternalDetails() {
        ProblemDetail problem = handler.handleDataAccess(
                new TransactionSystemException(
                        "Could not open a new Neo4j session",
                        new ServiceUnavailableException("localhost:7687")));

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE.value(), problem.getStatus());
        assertFalse(problem.getDetail().contains("7687"));
    }

    @Test
    void otherDataErrorsAreReportedAs500() {
        ProblemDetail problem = handler.handleDataAccess(
                new InvalidDataAccessApiUsageException("bad query"));

        assertEquals(
                HttpStatus.INTERNAL_SERVER_ERROR.value(), problem.getStatus());
        assertFalse(problem.getDetail().contains("bad query"));
    }
}
