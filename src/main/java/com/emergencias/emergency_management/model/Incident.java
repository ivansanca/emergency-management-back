package com.emergencias.emergency_management.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.time.Instant;

/**
 * An emergency located in a zone.
 * <p>
 * {@code occurredAt} is when the emergency happened according to its source
 * and {@code receivedAt} is when this service registered it. {@code notified}
 * tells whether Kafka has confirmed the {@code incident-created} notification.
 * <p>
 * Each event that reports the incident is stored as a {@code Detection} node
 * linked to it, written with Cypher in {@code IncidentRepository}.
 * {@code detectionCount} and {@code lastDetectedAt} summarise them, so that
 * the incident does not load its detections.
 */
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
    private Boolean notified;
    private Long detectionCount;
    private Instant lastDetectedAt;

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
