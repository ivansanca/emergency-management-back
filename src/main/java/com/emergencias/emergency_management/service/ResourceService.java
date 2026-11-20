package com.emergencias.emergency_management.service;

import com.emergencias.emergency_management.api.dto.ResourceRequest;
import com.emergencias.emergency_management.dto.ResourceResponse;
import com.emergencias.emergency_management.exception.ConflictException;
import com.emergencias.emergency_management.exception.InvalidRequestException;
import com.emergencias.emergency_management.model.EmergencyResource;
import com.emergencias.emergency_management.model.ResourceType;
import com.emergencias.emergency_management.model.Zone;
import com.emergencias.emergency_management.repository.ResourceRepository;
import com.emergencias.emergency_management.repository.ResourceTypeRepository;
import com.emergencias.emergency_management.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Registration of emergency resources.
 */
@Service
@RequiredArgsConstructor
public class ResourceService {

    private final ResourceRepository resourceRepository;
    private final ResourceTypeRepository resourceTypeRepository;
    private final ZoneRepository zoneRepository;

    /**
     * The type is taken from the graph instead of being created from its
     * name. Saving a new type node would let a typo create a type that no
     * rule uses, and could overwrite properties of an existing type such as
     * {@code aerial}.
     */
    public ResourceResponse create(ResourceRequest request) {

        if (resourceRepository.existsById(request.getId())) {
            throw new ConflictException(
                    "Ya existe el recurso " + request.getId());
        }

        Zone zone = zoneRepository.findById(request.getZoneCode())
                .orElseThrow(() -> new InvalidRequestException(
                        "No existe la zona " + request.getZoneCode()));

        ResourceType type = resourceTypeRepository.findById(request.getType())
                .orElseThrow(() -> new InvalidRequestException(
                        "No existe el tipo de recurso " + request.getType()));

        EmergencyResource resource = new EmergencyResource(
                request.getId(),
                request.getName(),
                type,
                !Boolean.FALSE.equals(request.getAvailable()),
                zone);

        return ResourceResponse.from(resourceRepository.save(resource));
    }
}
