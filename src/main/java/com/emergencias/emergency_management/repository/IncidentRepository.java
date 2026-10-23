package com.emergencias.emergency_management.repository;

import com.emergencias.emergency_management.model.Incident;
import org.springframework.data.neo4j.repository.Neo4jRepository;

public interface IncidentRepository
        extends Neo4jRepository<Incident, String> {
}