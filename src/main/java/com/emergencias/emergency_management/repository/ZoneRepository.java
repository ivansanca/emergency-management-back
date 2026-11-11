package com.emergencias.emergency_management.repository;

import com.emergencias.emergency_management.model.Zone;
import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface ZoneRepository
        extends Neo4jRepository<Zone, String> {
}
