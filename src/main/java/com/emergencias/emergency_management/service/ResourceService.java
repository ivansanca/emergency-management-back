package com.emergencias.emergency_management.service;

import com.emergencias.emergency_management.api.dto.ResourceRequest;
import com.emergencias.emergency_management.exception.ConflictException;
import com.emergencias.emergency_management.exception.InvalidRequestException;
import com.emergencias.emergency_management.model.EmergencyResource;
import com.emergencias.emergency_management.model.Zone;
import com.emergencias.emergency_management.repository.ResourceRepository;
import com.emergencias.emergency_management.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ResourceService {

    private final ResourceRepository resourceRepository;
    private final ZoneRepository zoneRepository;

    public EmergencyResource create(ResourceRequest request) {

        if (resourceRepository.existsById(request.getId())) {
            throw new ConflictException(
                    "Ya existe el recurso " + request.getId());
        }

        Zone zone = zoneRepository.findById(request.getZoneCode())
                .orElseThrow(() -> new InvalidRequestException(
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
