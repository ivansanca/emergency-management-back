package com.emergencias.emergency_management.service;

import com.emergencias.emergency_management.dto.IncidentDetail;
import com.emergencias.emergency_management.dto.IncidentFilter;
import com.emergencias.emergency_management.dto.IncidentSummary;
import com.emergencias.emergency_management.dto.PageResponse;
import com.emergencias.emergency_management.exception.InvalidRequestException;
import com.emergencias.emergency_management.exception.NotFoundException;
import com.emergencias.emergency_management.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Read-only queries over incidents for the operator: the list, with filters
 * and pages, and the detail of one incident.
 * <p>
 * Each query runs in one read-only transaction, so a page and its count see
 * the same data even if incidents are being registered meanwhile.
 */
@Service
@RequiredArgsConstructor
public class IncidentQueryService {

    /** Larger pages are refused: the list is meant to be browsed. */
    static final int MAX_PAGE_SIZE = 100;

    private final IncidentRepository incidentRepository;

    @Transactional(readOnly = true)
    public PageResponse<IncidentSummary> search(IncidentFilter filter, int page, int size) {

        validatePage(page, size);
        validateInterval(filter);

        String status = filter.status() != null ? filter.status().name() : null;
        String source = filter.source() != null ? filter.source().name() : null;

        List<IncidentSummary> items = incidentRepository.search(
                status, filter.type(), source, filter.zoneCode(),
                filter.from(), filter.to(),
                (long) page * size, size);

        long total = incidentRepository.countSearch(
                status, filter.type(), source, filter.zoneCode(),
                filter.from(), filter.to());

        return PageResponse.of(items, page, size, total);
    }

    @Transactional(readOnly = true)
    public IncidentDetail detail(String incidentId) {

        IncidentSummary incident = incidentRepository.findSummary(incidentId)
                .orElseThrow(() -> new NotFoundException(
                        "No existe el incidente " + incidentId));

        return new IncidentDetail(
                incident,
                incidentRepository.findDetections(incidentId),
                incidentRepository.findAssignments(incidentId));
    }

    private static void validatePage(int page, int size) {
        if (page < 0) {
            throw new InvalidRequestException("La página no puede ser negativa");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException(
                    "El tamaño de página debe estar entre 1 y " + MAX_PAGE_SIZE);
        }
    }

    private static void validateInterval(IncidentFilter filter) {
        if (filter.from() != null && filter.to() != null
                && filter.from().isAfter(filter.to())) {
            throw new InvalidRequestException(
                    "La fecha inicial (from) es posterior a la final (to)");
        }
    }
}
