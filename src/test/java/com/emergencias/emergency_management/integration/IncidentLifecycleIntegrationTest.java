package com.emergencias.emergency_management.integration;

import com.emergencias.emergency_management.dto.AssignmentResponse;
import com.emergencias.emergency_management.dto.CloseIncidentResponse;
import com.emergencias.emergency_management.exception.ConflictException;
import com.emergencias.emergency_management.service.IncidentLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.neo4j.core.Neo4jClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Assigning and closing against the real graph, including simultaneous
 * assignments of the same resource.
 */
@IntegrationTest
class IncidentLifecycleIntegrationTest {

    private static final String RESOURCE = "ENGINE-ORIGIN";

    @Autowired
    private Neo4jClient neo4jClient;

    @Autowired
    private IncidentLifecycleService incidentLifecycleService;

    private TestGraph graph;

    @BeforeEach
    void setUp() {
        graph = new TestGraph(neo4jClient);
        graph.reset();
        graph.incident(TestGraph.INCIDENT_ID, 2);
    }

    private boolean available(String resourceId) {
        return graph.flag(
                "MATCH (r:Resource {id: $id}) RETURN r.available",
                Map.of("id", resourceId));
    }

    private long assignments() {
        return graph.count("""
                MATCH (:Resource {id: $id})-[a:ASSIGNED_TO]->(:Incident)
                RETURN count(a)
                """, Map.of("id", RESOURCE));
    }

    @Test
    void assignMarksResourceAsBusyAndRejectsASecondAssignment() {
        AssignmentResponse response =
                incidentLifecycleService.assign(TestGraph.INCIDENT_ID, RESOURCE);

        assertEquals(RESOURCE, response.getResourceId());
        assertFalse(available(RESOURCE));
        assertEquals(1, assignments());
        assertThrows(
                ConflictException.class,
                () -> incidentLifecycleService.assign(
                        TestGraph.INCIDENT_ID, RESOURCE));
    }

    @Test
    void closeReleasesResourcesAndKeepsTheHistory() {
        incidentLifecycleService.assign(TestGraph.INCIDENT_ID, RESOURCE);

        CloseIncidentResponse response =
                incidentLifecycleService.close(TestGraph.INCIDENT_ID);

        assertEquals(1, response.getReleasedResources());
        assertTrue(available(RESOURCE));
        assertEquals(1, graph.count("""
                MATCH (:Resource {id: $id})-[a:ASSIGNED_TO]->(i:Incident)
                WHERE a.releasedAt IS NOT NULL AND i.status = 'CLOSED'
                RETURN count(a)
                """, Map.of("id", RESOURCE)));
        assertThrows(
                ConflictException.class,
                () -> incidentLifecycleService.assign(
                        TestGraph.INCIDENT_ID, "TENDER-PASS"));
    }

    @Test
    void simultaneousClosesOnlySucceedOnce() throws Exception {
        int requests = 8;
        ExecutorService executor = Executors.newFixedThreadPool(requests);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<CloseIncidentResponse>> results = new ArrayList<>();

        try {
            for (int i = 0; i < requests; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return incidentLifecycleService.close(
                            TestGraph.INCIDENT_ID);
                }));
            }
            start.countDown();

            int succeeded = 0;
            for (Future<CloseIncidentResponse> result : results) {
                try {
                    result.get();
                    succeeded++;
                } catch (ExecutionException exception) {
                    assertInstanceOf(
                            ConflictException.class, exception.getCause());
                }
            }

            assertEquals(1, succeeded);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void simultaneousAssignmentsOfTheSameResourceOnlySucceedOnce()
            throws Exception {

        int requests = 8;
        ExecutorService executor = Executors.newFixedThreadPool(requests);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<AssignmentResponse>> results = new ArrayList<>();

        try {
            for (int i = 0; i < requests; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    return incidentLifecycleService.assign(
                            TestGraph.INCIDENT_ID, RESOURCE);
                }));
            }
            start.countDown();

            int succeeded = 0;
            for (Future<AssignmentResponse> result : results) {
                try {
                    result.get();
                    succeeded++;
                } catch (ExecutionException exception) {
                    assertInstanceOf(
                            ConflictException.class, exception.getCause());
                }
            }

            assertEquals(1, succeeded);
            assertEquals(1, assignments());
        } finally {
            executor.shutdownNow();
        }
    }
}
