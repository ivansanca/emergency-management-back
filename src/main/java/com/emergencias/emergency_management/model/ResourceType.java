package com.emergencias.emergency_management.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

/**
 * Type of resource, such as {@code FOREST_ENGINE}. Types flagged as aerial in
 * the graph are dispatched without road routing.
 */
@Node("ResourceType")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ResourceType {

    @Id
    private String name;
}