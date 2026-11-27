package com.emergencias.emergency_management.api;

import com.emergencias.emergency_management.api.dto.ResourceRequest;
import com.emergencias.emergency_management.dto.ResourceResponse;
import com.emergencias.emergency_management.dto.ResourceSummary;
import com.emergencias.emergency_management.service.ResourceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints to register emergency resources and to list them, filtered
 * by the municipality of their station ({@code zoneCode}), {@code type} and
 * {@code available}.
 */
@RestController
@RequestMapping("/api/resources")
@RequiredArgsConstructor
public class ResourceController {

    private final ResourceService resourceService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResourceResponse create(
            @Valid @RequestBody ResourceRequest request) {

        return resourceService.create(request);
    }

    @GetMapping
    public List<ResourceSummary> list(
            @RequestParam(required = false) String zoneCode,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean available) {

        return resourceService.list(zoneCode, type, available);
    }
}
