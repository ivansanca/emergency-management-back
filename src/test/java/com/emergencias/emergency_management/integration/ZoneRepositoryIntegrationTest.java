package com.emergencias.emergency_management.integration;

import com.emergencias.emergency_management.model.Zone;
import com.emergencias.emergency_management.repository.ZoneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.neo4j.core.Neo4jClient;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Resolution of the zone of an incident from its coordinates.
 */
@IntegrationTest
class ZoneRepositoryIntegrationTest {

    @Autowired
    private Neo4jClient neo4jClient;

    @Autowired
    private ZoneRepository zoneRepository;

    @BeforeEach
    void setUp() {
        new TestGraph(neo4jClient).reset();
    }

    @Test
    void findNearestReturnsTheZoneWithTheClosestCapital() {
        Zone zone = zoneRepository.findNearest(43.41, -3.91).orElseThrow();

        assertEquals("00002", zone.getIneCode());
        assertEquals("Paso", zone.getName());
    }
}
