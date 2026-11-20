package com.emergencias.emergency_management.repository;

import com.emergencias.emergency_management.model.EmergencyResource;
import com.emergencias.emergency_management.repository.projection.ResourceCandidateRow;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Queries that search, route and assign resources. They hold the core of the
 * recommendation logic.
 */
public interface ResourceRepository
        extends Neo4jRepository<EmergencyResource, String> {


    /**
     * Ground resources that can reach the incident within the given limits.
     * <p>
     * Every road path of up to five hops between the zone of the incident and
     * the zone of each resource is explored. Paths through a closed road are
     * discarded and the fastest remaining one is kept. A resource based in the
     * zone of the incident is at distance zero.
     * <p>
     * Known limitation: routes that need more than five hops are not found. A
     * weighted shortest path algorithm (Dijkstra) would remove it.
     */
    @Query("""
            MATCH (i:Incident {id: $incidentId})-[:OCCURRED_IN]->(origin:Zone)
            MATCH (r:Resource)-[:HAS_TYPE]->(rt:ResourceType)
            MATCH (r)-[:LOCATED_IN]->(resourceZone:Zone)
            
            WHERE r.available = true
              AND rt.name IN $eligibleTypes
              AND coalesce(rt.aerial, false) = false
            
            OPTIONAL MATCH p =
                (origin)-[:CONNECTS_TO*1..5]-(resourceZone)
            
            WITH r, origin, resourceZone, p,
                 CASE
                     WHEN origin = resourceZone THEN 0.0
                     WHEN p IS NOT NULL
                          AND all(connection IN relationships(p)
                              WHERE connection.active = true
                                AND connection.distanceKm IS NOT NULL
                                AND connection.travelTimeMinutes IS NOT NULL)
                     THEN reduce(
                         total = 0.0,
                         connection IN relationships(p) |
                         total + toFloat(connection.distanceKm)
                     )
                     ELSE null
                 END AS candidateDistanceKm,
            
                 CASE
                     WHEN origin = resourceZone THEN 0.0
                     WHEN p IS NOT NULL
                          AND all(connection IN relationships(p)
                              WHERE connection.active = true
                                AND connection.distanceKm IS NOT NULL
                                AND connection.travelTimeMinutes IS NOT NULL)
                     THEN reduce(
                         total = 0.0,
                         connection IN relationships(p) |
                         total + toFloat(connection.travelTimeMinutes)
                     )
                     ELSE null
                 END AS candidateTravelTimeMinutes,
            
                 CASE
                     WHEN origin = resourceZone THEN [origin.name]
                     WHEN p IS NOT NULL
                          AND all(connection IN relationships(p)
                              WHERE connection.active = true
                                AND connection.distanceKm IS NOT NULL
                                AND connection.travelTimeMinutes IS NOT NULL)
                     THEN [zone IN nodes(p) | zone.name]
                     ELSE []
                 END AS candidateRoute
            
            WHERE candidateDistanceKm IS NOT NULL
              AND candidateDistanceKm <= $maxDistanceKm
              AND candidateTravelTimeMinutes IS NOT NULL
              AND candidateTravelTimeMinutes <= $maxTravelTimeMinutes
            
            WITH r, resourceZone,
                 candidateDistanceKm,
                 candidateTravelTimeMinutes,
                 candidateRoute
            
            ORDER BY r.id,
                     candidateTravelTimeMinutes ASC,
                     candidateDistanceKm ASC
            
            WITH r, resourceZone,
                 collect({
                     distanceKm: candidateDistanceKm,
                     travelTimeMinutes: candidateTravelTimeMinutes,
                     route: candidateRoute
                 })[0] AS best
            
            RETURN r,
                   resourceZone.name AS zoneName,
                   best.distanceKm AS distanceKm,
                   best.travelTimeMinutes AS travelTimeMinutes,
                   best.route AS route
            
            ORDER BY best.travelTimeMinutes ASC,
                     best.distanceKm ASC,
                     r.name ASC
            """)
    List<ResourceCandidateRow> findAvailableForIncident(
            @Param("incidentId") String incidentId,
            @Param("eligibleTypes") List<String> eligibleTypes,
            @Param("maxDistanceKm") double maxDistanceKm,
            @Param("maxTravelTimeMinutes") double maxTravelTimeMinutes);

    /**
     * Available aerial resources. They are dispatched without road routing, so
     * they come without distance, travel time or route.
     */
    @Query("""
        MATCH (r:Resource)-[:HAS_TYPE]->(rt:ResourceType)
        MATCH (r)-[:LOCATED_IN]->(z:Zone)
        WHERE r.available = true
          AND rt.name IN $eligibleTypes
          AND rt.aerial = true

        RETURN r,
               z.name AS zoneName,
               null AS distanceKm,
               null AS travelTimeMinutes,
               [] AS route

        ORDER BY r.name ASC
        """)
    List<ResourceCandidateRow> findAvailableAerialResources(
            @Param("eligibleTypes") List<String> eligibleTypes);

    /**
     * Assigns the resource if it is still available and the incident is still
     * open. Returns the assignment time, or empty when the assignment is no
     * longer possible.
     * <p>
     * The write on {@code _lock} takes the write lock of both nodes before the
     * conditions are read. Without it, two simultaneous requests could both
     * see the resource as available and assign it twice.
     */
    @Query("""
        MATCH (i:Incident {id: $incidentId})
        MATCH (r:Resource {id: $resourceId})
        SET i._lock = true, r._lock = true
        REMOVE i._lock, r._lock

        WITH i, r
        WHERE r.available = true
          AND coalesce(i.status, 'ACTIVE') <> 'CLOSED'
        SET r.available = false
        CREATE (r)-[a:ASSIGNED_TO {assignedAt: datetime()}]->(i)
        RETURN a.assignedAt
        """)
    Optional<Instant> assignToIncident(
            @Param("resourceId") String resourceId,
            @Param("incidentId") String incidentId);
}
