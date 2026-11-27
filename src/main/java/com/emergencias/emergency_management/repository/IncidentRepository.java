package com.emergencias.emergency_management.repository;

import com.emergencias.emergency_management.dto.AssignmentSummary;
import com.emergencias.emergency_management.dto.DetectionSummary;
import com.emergencias.emergency_management.dto.IncidentSummary;
import com.emergencias.emergency_management.model.Incident;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
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
     * Assignments are kept, with their release time, as history, and the
     * incident keeps the time it was closed.
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
        SET i.status = 'CLOSED',
            i.closedAt = datetime()

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
     * Takes the write lock of a single lock node until the transaction ends,
     * so that incidents are registered one at a time.
     * <p>
     * Grouping reads the open incidents and then writes. Two consumers doing
     * it at once could both miss each other and register the same fire twice.
     * Setting a property takes the write lock even if the node already exists;
     * the second transaction waits for the first to commit and then sees its
     * incident. The uniqueness constraint on the lock node makes the
     * {@code MERGE} safe when the node does not exist yet.
     */
    @Query("""
        MERGE (l:IncidentRegistrationLock {id: 'incident-registration'})
        SET l.lockedAt = datetime()

        RETURN count(l)
        """)
    Long lockRegistrations();

    /**
     * Distance in metres from a position to the nearest located detection of
     * the incident, empty when none of its detections has a position.
     */
    @Query("""
        WITH point({latitude: $latitude, longitude: $longitude}) AS here
        MATCH (d:Detection)-[:DETECTION_OF]->(:Incident {id: $incidentId})
        WHERE d.location IS NOT NULL

        RETURN min(point.distance(d.location, here))
        """)
    Optional<Double> distanceToIncident(
            @Param("incidentId") String incidentId,
            @Param("latitude") double latitude,
            @Param("longitude") double longitude);

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

    // ---- Queries of the query API ----

    /**
     * Incidents that match the optional filters of {@link #search}; a null
     * parameter does not filter. Shared by the page and its count so that
     * both always select the same incidents.
     * <p>
     * The interval keeps the incidents active at some point of it: started
     * before {@code to} and not finished before {@code from}. An open
     * incident has not finished, however long ago it was last detected. A
     * closed one finished when it was closed; incidents closed before
     * {@code closedAt} existed fall back to their last detection.
     */
    String SEARCH_FILTER = """
        MATCH (i:Incident)
        OPTIONAL MATCH (i)-[:OCCURRED_IN]->(z:Zone)
        WITH i, z
        WHERE ($status IS NULL OR coalesce(i.status, 'ACTIVE') = $status)
          AND ($type IS NULL OR i.type = $type)
          AND ($source IS NULL OR coalesce(i.source, 'MANUAL') = $source)
          AND ($zoneCode IS NULL OR z.ineCode = $zoneCode)
          AND ($from IS NULL
               OR coalesce(i.status, 'ACTIVE') <> 'CLOSED'
               OR coalesce(i.closedAt, i.lastDetectedAt, i.occurredAt) >= $from)
          AND ($to IS NULL OR i.occurredAt <= $to)
        """;

    /** Columns of {@link IncidentSummary}, for incident {@code i} in zone {@code z}. */
    String SUMMARY_COLUMNS = """
        RETURN i.id AS id,
               i.type AS type,
               i.severity AS severity,
               coalesce(i.status, 'ACTIVE') AS status,
               coalesce(i.source, 'MANUAL') AS source,
               i.description AS description,
               z.ineCode AS zoneCode,
               z.name AS zoneName,
               i.latitude AS latitude,
               i.longitude AS longitude,
               i.occurredAt AS occurredAt,
               i.receivedAt AS receivedAt,
               coalesce(i.lastDetectedAt, i.occurredAt) AS lastDetectedAt,
               i.closedAt AS closedAt,
               coalesce(i.detectionCount, 0) AS detectionCount,
               COUNT { (:Resource)-[a:ASSIGNED_TO]->(i) WHERE a.releasedAt IS NULL }
                   AS assignedResources
        """;

    /**
     * One page of incidents, the most recently active first. The order uses
     * the latest detection, so a fire that is still being detected stays at
     * the top even if it started days ago.
     */
    @Query(SEARCH_FILTER + SUMMARY_COLUMNS + """
        ORDER BY lastDetectedAt DESC, id ASC
        SKIP $skip
        LIMIT $limit
        """)
    List<IncidentSummary> search(
            @Param("status") String status,
            @Param("type") String type,
            @Param("source") String source,
            @Param("zoneCode") String zoneCode,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("skip") long skip,
            @Param("limit") long limit);

    @Query(SEARCH_FILTER + """
        RETURN count(i)
        """)
    long countSearch(
            @Param("status") String status,
            @Param("type") String type,
            @Param("source") String source,
            @Param("zoneCode") String zoneCode,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
        MATCH (i:Incident {id: $incidentId})
        OPTIONAL MATCH (i)-[:OCCURRED_IN]->(z:Zone)
        """ + SUMMARY_COLUMNS)
    Optional<IncidentSummary> findSummary(
            @Param("incidentId") String incidentId);

    /** Detections of the incident in the order they were observed. */
    @Query("""
        MATCH (d:Detection)-[:DETECTION_OF]->(:Incident {id: $incidentId})
        RETURN d.id AS id,
               d.latitude AS latitude,
               d.longitude AS longitude,
               d.observedAt AS observedAt,
               d.receivedAt AS receivedAt,
               d.source AS source
        ORDER BY observedAt ASC, id ASC
        """)
    List<DetectionSummary> findDetections(
            @Param("incidentId") String incidentId);

    /** Current and past assignments of the incident, oldest first. */
    @Query("""
        MATCH (r:Resource)-[a:ASSIGNED_TO]->(:Incident {id: $incidentId})
        OPTIONAL MATCH (r)-[:HAS_TYPE]->(rt:ResourceType)
        RETURN r.id AS resourceId,
               r.name AS resourceName,
               coalesce(rt.name, r.type) AS resourceType,
               a.assignedAt AS assignedAt,
               a.releasedAt AS releasedAt
        ORDER BY assignedAt ASC, resourceId ASC
        """)
    List<AssignmentSummary> findAssignments(
            @Param("incidentId") String incidentId);
}
