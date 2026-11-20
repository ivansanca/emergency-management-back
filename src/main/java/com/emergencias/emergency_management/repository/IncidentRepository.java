package com.emergencias.emergency_management.repository;

import com.emergencias.emergency_management.model.Incident;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Persistence and lifecycle queries of incidents.
 */
public interface IncidentRepository
        extends Neo4jRepository<Incident, String> {

    /**
     * Closes the incident and releases its resources in a single query.
     * Assignments are kept, with their release time, as history.
     * <p>
     * Like the assignment, it takes the write lock before checking the status,
     * so that two simultaneous closes cannot both succeed. Returns empty when
     * the incident was already closed.
     */
    @Query("""
        MATCH (i:Incident {id: $incidentId})
        SET i._lock = true
        REMOVE i._lock

        WITH i
        WHERE coalesce(i.status, 'ACTIVE') <> 'CLOSED'
        SET i.status = 'CLOSED'

        WITH i
        OPTIONAL MATCH (r:Resource)-[a:ASSIGNED_TO]->(i)
        WHERE a.releasedAt IS NULL
        SET r.available = true,
            a.releasedAt = datetime()

        WITH i, count(r) AS releasedResources
        RETURN releasedResources
        """)
    Optional<Long> closeAndReleaseResources(
            @Param("incidentId") String incidentId);

    /**
     * Records that Kafka has confirmed the {@code incident-created}
     * notification of the incident.
     */
    @Query("""
        MATCH (i:Incident {id: $incidentId})
        SET i.notified = true

        RETURN count(i)
        """)
    Long markNotified(
            @Param("incidentId") String incidentId);
}
