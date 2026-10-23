package com.emergencias.emergency_management.controller;

import com.emergencias.emergency_management.model.EmergencyResource;
import com.emergencias.emergency_management.model.ResourceRequest;
import com.emergencias.emergency_management.service.ResourceService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/resources")
public class ResourceController {

    private final ResourceService resourceService;

    public ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmergencyResource create(
            @Valid @RequestBody ResourceRequest request) {

        return resourceService.create(request);
    }
}