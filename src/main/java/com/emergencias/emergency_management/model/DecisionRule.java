package com.emergencias.emergency_management.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.HashSet;
import java.util.Set;

/**
 * Rule that states which resource types an incident of a given type and
 * severity requires. Rules live in the graph and are loaded with the Cypher
 * scripts; the quantities are stored in the {@code ALLOWS_RESOURCE_TYPE}
 * relationships and are read with custom queries.
 */
@Node("DecisionRule")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DecisionRule {

    @Id
    private String id;

    private String name;
    private String incidentType;
    private int minSeverity;
    private int maxSeverity;
    private int priority;
    private boolean active;
    private String description;

    @Relationship(
            type = "ALLOWS_RESOURCE_TYPE",
            direction = Relationship.Direction.OUTGOING
    )
    private Set<ResourceType> allowedResourceTypes = new HashSet<>();
}