package com.emergencias.emergency_management.repository;

import com.emergencias.emergency_management.model.ResourceType;
import org.springframework.data.neo4j.repository.Neo4jRepository;

/**
 * Queries over resource types.
 */
public interface ResourceTypeRepository
        extends Neo4jRepository<ResourceType, String> {
}
