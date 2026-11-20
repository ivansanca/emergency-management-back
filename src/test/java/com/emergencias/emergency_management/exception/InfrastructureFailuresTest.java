package com.emergencias.emergency_management.exception;

import org.junit.jupiter.api.Test;
import org.neo4j.driver.exceptions.ServiceUnavailableException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.TransactionSystemException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfrastructureFailuresTest {

    @Test
    void neo4jDownWrappedBySpringIsDetected() {
        assertTrue(InfrastructureFailures.isInfrastructureFailure(
                new TransactionSystemException("Could not open a new Neo4j session",
                        new ServiceUnavailableException("Unable to connect"))));
    }

    @Test
    void translatedDataAccessFailureIsDetected() {
        assertTrue(InfrastructureFailures.isInfrastructureFailure(
                new DataAccessResourceFailureException("Neo4j down")));
    }

    @Test
    void kafkaNotConfirmingIsDetected() {
        assertTrue(InfrastructureFailures.isInfrastructureFailure(
                new EventPublishingException("incident-created",
                        new IllegalStateException("broker down"))));
    }

    @Test
    void otherErrorsAreNotInfrastructureFailures() {
        assertFalse(InfrastructureFailures.isInfrastructureFailure(
                new IllegalStateException("bug",
                        new NullPointerException())));
        assertFalse(InfrastructureFailures.isInfrastructureFailure(null));
    }

    @Test
    void cyclicCauseChainDoesNotLoopForever() {
        IllegalStateException first = new IllegalStateException("first");
        IllegalStateException second = new IllegalStateException("second", first);
        first.initCause(second);

        assertFalse(InfrastructureFailures.isInfrastructureFailure(first));
    }
}
