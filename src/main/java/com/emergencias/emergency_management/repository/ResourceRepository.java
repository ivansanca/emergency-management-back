package com.emergencias.emergency_management.repository;

import com.emergencias.emergency_management.model.EmergencyResource;
import com.emergencias.emergency_management.repository.projection.ResourceCandidateRow;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ResourceRepository
        extends Neo4jRepository<EmergencyResource, String> {


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
        RETURN toString(a.assignedAt)
        """)
    Optional<String> assignToIncident(
            @Param("resourceId") String resourceId,
            @Param("incidentId") String incidentId);
}
