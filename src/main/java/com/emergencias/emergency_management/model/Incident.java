package com.emergencias.emergency_management.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

@Node("Incident")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Incident {

    @Id
    private String id;

    private String type;
    private int severity;
    private String description;
    private IncidentStatus status;

    @Relationship(
            type = "OCCURRED_IN",
            direction = Relationship.Direction.OUTGOING
    )
    private Zone zone;

    public Incident(IncidentEvent event, Zone zone) {
        this.id = event.getId();
        this.type = event.getType();
        this.severity = event.getSeverity();
        this.description = event.getDescription();
        this.status = IncidentStatus.ACTIVE;
        this.zone = zone;
    }

    public boolean isClosed() {
        return status == IncidentStatus.CLOSED;
    }
}
