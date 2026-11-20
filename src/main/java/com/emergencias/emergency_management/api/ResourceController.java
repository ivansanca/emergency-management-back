package com.emergencias.emergency_management.api;

import com.emergencias.emergency_management.api.dto.ResourceRequest;
import com.emergencias.emergency_management.dto.ResourceResponse;
import com.emergencias.emergency_management.service.ResourceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoint to register emergency resources.
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
}
