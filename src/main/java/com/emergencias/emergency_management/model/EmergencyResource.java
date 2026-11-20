package com.emergencias.emergency_management.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

/**
 * A vehicle or piece of equipment based in a zone. It is not available while
 * it is assigned to an open incident.
 */
@Node("Resource")
@Getter
@Setter
@NoArgsConstructor
public class EmergencyResource {

    @Id
    private String id;

    private String name;
    private String type;
    private boolean available;

    @Relationship(
            type = "LOCATED_IN",
            direction = Relationship.Direction.OUTGOING
    )
    private Zone zone;

    @Relationship(
            type = "HAS_TYPE",
            direction = Relationship.Direction.OUTGOING
    )
    private ResourceType resourceType;

    public EmergencyResource(
            String id,
            String name,
            ResourceType resourceType,
            boolean available,
            Zone zone) {

        this.id = id;
        this.name = name;
        this.type = resourceType.getName();
        this.available = available;
        this.zone = zone;
        this.resourceType = resourceType;
    }
}