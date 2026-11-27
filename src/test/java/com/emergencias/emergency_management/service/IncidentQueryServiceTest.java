package com.emergencias.emergency_management.service;

import com.emergencias.emergency_management.dto.AssignmentSummary;
import com.emergencias.emergency_management.dto.DetectionSummary;
import com.emergencias.emergency_management.dto.IncidentDetail;
import com.emergencias.emergency_management.dto.IncidentFilter;
import com.emergencias.emergency_management.dto.IncidentSummary;
import com.emergencias.emergency_management.dto.PageResponse;
import com.emergencias.emergency_management.exception.InvalidRequestException;
import com.emergencias.emergency_management.exception.NotFoundException;
import com.emergencias.emergency_management.model.IncidentSource;
import com.emergencias.emergency_management.model.IncidentStatus;
import com.emergencias.emergency_management.repository.IncidentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentQueryServiceTest {

    private static final Instant FROM = Instant.parse("2026-03-21T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-03-22T00:00:00Z");
    private static final IncidentFilter NO_FILTER =
            new IncidentFilter(null, null, null, null, null, null);

    @Mock
    private IncidentRepository incidentRepository;

    @InjectMocks
    private IncidentQueryService incidentQueryService;

    private static IncidentSummary summary(String id) {
        return new IncidentSummary(id, "FOREST_FIRE", 0,
                IncidentStatus.ACTIVE, IncidentSource.FIRMS, null,
                "39014", "Cabuérniga", 43.2265, -4.3016, FROM, FROM, FROM, null, 1, 0);
    }

    @Test
    void searchPassesTheFiltersAndCountsThePages() {
        IncidentFilter filter = new IncidentFilter(IncidentStatus.ACTIVE, "FOREST_FIRE",
                IncidentSource.FIRMS, "39014", FROM, TO);
        List<IncidentSummary> items = List.of(summary("H-3"), summary("H-4"));
        when(incidentRepository.search("ACTIVE", "FOREST_FIRE", "FIRMS", "39014",
                FROM, TO, 2L, 2L)).thenReturn(items);
        when(incidentRepository.countSearch("ACTIVE", "FOREST_FIRE", "FIRMS", "39014",
                FROM, TO)).thenReturn(5L);

        PageResponse<IncidentSummary> page = incidentQueryService.search(filter, 1, 2);

        assertSame(items, page.items());
        assertEquals(1, page.page());
        assertEquals(2, page.size());
        assertEquals(5, page.totalElements());
        assertEquals(3, page.totalPages());
    }

    @Test
    void searchWithoutFiltersPassesNulls() {
        when(incidentRepository.search(null, null, null, null, null, null, 0L, 20L))
                .thenReturn(List.of());
        when(incidentRepository.countSearch(null, null, null, null, null, null))
                .thenReturn(0L);

        PageResponse<IncidentSummary> page = incidentQueryService.search(NO_FILTER, 0, 20);

        assertEquals(0, page.totalElements());
        assertEquals(0, page.totalPages());
    }

    @Test
    void invalidPagesAndIntervalsAreRejected() {
        IncidentFilter reversed = new IncidentFilter(null, null, null, null, TO, FROM);

        assertThrows(InvalidRequestException.class,
                () -> incidentQueryService.search(NO_FILTER, -1, 20));
        assertThrows(InvalidRequestException.class,
                () -> incidentQueryService.search(NO_FILTER, 0, 0));
        assertThrows(InvalidRequestException.class,
                () -> incidentQueryService.search(NO_FILTER, 0,
                        IncidentQueryService.MAX_PAGE_SIZE + 1));
        assertThrows(InvalidRequestException.class,
                () -> incidentQueryService.search(reversed, 0, 20));

        verifyNoInteractions(incidentRepository);
    }

    @Test
    void detailJoinsTheIncidentItsDetectionsAndItsAssignments() {
        IncidentSummary incident = summary("H-1");
        List<DetectionSummary> detections = List.of(
                new DetectionSummary("H-1", 43.2265, -4.3016, FROM, FROM, IncidentSource.FIRMS));
        List<AssignmentSummary> assignments = List.of(
                new AssignmentSummary("BRP-21", "Autobomba", "RURAL_ENGINE", TO, null));
        when(incidentRepository.findSummary("H-1")).thenReturn(Optional.of(incident));
        when(incidentRepository.findDetections("H-1")).thenReturn(detections);
        when(incidentRepository.findAssignments("H-1")).thenReturn(assignments);

        IncidentDetail detail = incidentQueryService.detail("H-1");

        assertSame(incident, detail.incident());
        assertSame(detections, detail.detections());
        assertSame(assignments, detail.assignments());
    }

    @Test
    void detailOfAnUnknownIncidentIsNotFound() {
        when(incidentRepository.findSummary("NO-EXISTE")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> incidentQueryService.detail("NO-EXISTE"));
    }
}
