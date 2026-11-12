package com.emergencias.emergency_management.repository;

import com.emergencias.emergency_management.model.Zone;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ZoneRepository
        extends Neo4jRepository<Zone, String> {

    @Query("""
        MATCH (z:Zone)
        WHERE z.latitude IS NOT NULL
          AND z.longitude IS NOT NULL

        RETURN z

        ORDER BY point.distance(
            point({latitude: z.latitude, longitude: z.longitude}),
            point({latitude: $latitude, longitude: $longitude})
        ) ASC

        LIMIT 1
        """)
    Optional<Zone> findNearest(
            @Param("latitude") double latitude,
            @Param("longitude") double longitude);
}
