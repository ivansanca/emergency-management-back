package com.emergencias.emergency_management.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.time.Instant;

@Node("Incident")
@Getter
@Setter
@NoArgsConstructor
public class Incident {

    @Id
    private String id;

    private String type;
    private int severity;
    private String description;
    private IncidentStatus status;
    private Double latitude;
    private Double longitude;
    private Instant occurredAt;
    private Instant receivedAt;
    private IncidentSource source;

    @Relationship(
            type = "OCCURRED_IN",
            direction = Relationship.Direction.OUTGOING
    )
    private Zone zone;

    public Incident(
            String id,
            String type,
            int severity,
            String description,
            IncidentStatus status,
            Zone zone) {

        this.id = id;
        this.type = type;
        this.severity = severity;
        this.description = description;
        this.status = status;
        this.zone = zone;
    }

    public boolean isClosed() {
        return status == IncidentStatus.CLOSED;
    }
}
