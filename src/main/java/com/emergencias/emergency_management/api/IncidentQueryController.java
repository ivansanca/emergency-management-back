package com.emergencias.emergency_management.api;

import com.emergencias.emergency_management.dto.IncidentDetail;
import com.emergencias.emergency_management.dto.IncidentFilter;
import com.emergencias.emergency_management.dto.IncidentSummary;
import com.emergencias.emergency_management.dto.PageResponse;
import com.emergencias.emergency_management.service.IncidentQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST queries over incidents: the list, with optional filters
 * ({@code status}, {@code type}, {@code source}, {@code zoneCode},
 * {@code from}, {@code to}) and pages, and the detail of one incident with its
 * detections and assignments. They only read; nothing is published.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/incidents")
public class IncidentQueryController {

    private final IncidentQueryService incidentQueryService;

    /** The filters are bound from the query string into the record. */
    @GetMapping
    public PageResponse<IncidentSummary> search(
            IncidentFilter filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return incidentQueryService.search(filter, page, size);
    }

    @GetMapping("/{incidentId}")
    public IncidentDetail detail(@PathVariable String incidentId) {
        return incidentQueryService.detail(incidentId);
    }
}
