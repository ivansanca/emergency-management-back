package com.emergencias.emergency_management.exception;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.neo4j.driver.exceptions.ServiceUnavailableException;
import org.neo4j.driver.exceptions.SessionExpiredException;
import org.neo4j.driver.exceptions.TransientException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Tells apart failures of a dependency (Neo4j or Kafka not reachable) from
 * any other error. They are temporary: the Kafka consumer retries them and the
 * REST API answers 503 so that the client can try again.
 * <p>
 * The whole cause chain is inspected because Spring wraps the original error.
 * For instance, when Neo4j is down a {@code TransactionSystemException} is
 * thrown with the driver's {@code ServiceUnavailableException} as its cause.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class InfrastructureFailures {

    private static final List<Class<? extends Throwable>> TYPES = List.of(
            ServiceUnavailableException.class,
            SessionExpiredException.class,
            TransientException.class,
            TransientDataAccessException.class,
            DataAccessResourceFailureException.class,
            EventPublishingException.class);

    public static boolean isInfrastructureFailure(Throwable exception) {
        Set<Throwable> visited =
                Collections.newSetFromMap(new IdentityHashMap<>());

        for (Throwable cause = exception;
                cause != null && visited.add(cause);
                cause = cause.getCause()) {

            Throwable current = cause;
            if (TYPES.stream().anyMatch(type -> type.isInstance(current))) {
                return true;
            }
        }
        return false;
    }
}
