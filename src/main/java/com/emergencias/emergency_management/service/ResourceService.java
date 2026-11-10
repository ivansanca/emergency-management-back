package com.emergencias.emergency_management.service;

import com.emergencias.emergency_management.model.EmergencyResource;
import com.emergencias.emergency_management.model.ResourceRequest;
import com.emergencias.emergency_management.model.Zone;
import com.emergencias.emergency_management.repository.ResourceRepository;
import com.emergencias.emergency_management.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ResourceService {

    private final ResourceRepository resourceRepository;
    private final ZoneRepository zoneRepository;

    public EmergencyResource create(ResourceRequest request) {

        Zone zone = zoneRepository.findById(request.getZoneCode())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "No existe la zona " + request.getZoneCode()));

        EmergencyResource resource = new EmergencyResource(
                request.getId(),
                request.getName(),
                request.getType(),
                request.isAvailable(),
                zone
        );

        return resourceRepository.save(resource);
    }
}
