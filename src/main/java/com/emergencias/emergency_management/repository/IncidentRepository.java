package com.emergencias.emergency_management.repository;

import com.emergencias.emergency_management.model.Incident;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * Persistence and lifecycle queries of incidents and of their detections.
 * <p>
 * Detections are written and searched with Cypher instead of being mapped as
 * a relationship of {@code Incident}: an incident may collect many of them and
 * loading it should not load them all.
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

    /**
     * Incident that holds the detection with the given id, if any.
     */
    @Query("""
        MATCH (:Detection {id: $detectionId})-[:DETECTION_OF]->(i:Incident)
        RETURN i.id
        LIMIT 1
        """)
    Optional<String> findIncidentIdByDetectionId(
            @Param("detectionId") String detectionId);

    /**
     * Open incident of the given type with a detection within the radius and
     * the time window, the nearest one if there are several. Distances are
     * measured on the WGS-84 ellipsoid, in metres. Comparing with every
     * detection, not only the first one, follows a fire that spreads.
     */
    @Query("""
        WITH point({latitude: $latitude, longitude: $longitude}) AS here
        MATCH (d:Detection)-[:DETECTION_OF]->(i:Incident {type: $type})
        WHERE coalesce(i.status, 'ACTIVE') <> 'CLOSED'
          AND d.location IS NOT NULL
          AND d.observedAt >= $from
          AND d.observedAt <= $to
          AND point.distance(d.location, here) <= $radiusMeters

        WITH i, min(point.distance(d.location, here)) AS distance
        RETURN i.id
        ORDER BY distance ASC, i.id ASC
        LIMIT 1
        """)
    Optional<String> findIncidentToJoin(
            @Param("type") String type,
            @Param("latitude") double latitude,
            @Param("longitude") double longitude,
            @Param("radiusMeters") double radiusMeters,
            @Param("from") Instant from,
            @Param("to") Instant to);

    /**
     * Stores a detection of the incident and updates its count and the time
     * of its latest detection. A detection without coordinates is kept too,
     * so that its id is known, but it has no location and cannot be joined.
     * Returns the number of detections of the incident.
     */
    @Query("""
        MATCH (i:Incident {id: $incidentId})
        CREATE (d:Detection {
            id: $detectionId,
            latitude: $latitude,
            longitude: $longitude,
            location: point({latitude: $latitude, longitude: $longitude}),
            observedAt: $observedAt,
            receivedAt: $receivedAt,
            source: $source
        })
        CREATE (d)-[:DETECTION_OF]->(i)
        SET i.detectionCount = coalesce(i.detectionCount, 0) + 1,
            i.lastDetectedAt = CASE
                WHEN i.lastDetectedAt IS NULL OR $observedAt > i.lastDetectedAt
                THEN $observedAt
                ELSE i.lastDetectedAt
            END

        RETURN i.detectionCount
        """)
    Long addDetection(
            @Param("incidentId") String incidentId,
            @Param("detectionId") String detectionId,
            @Param("latitude") Double latitude,
            @Param("longitude") Double longitude,
            @Param("observedAt") Instant observedAt,
            @Param("receivedAt") Instant receivedAt,
            @Param("source") String source);
}
