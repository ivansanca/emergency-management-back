package com.emergencias.emergency_management.service;

import com.emergencias.emergency_management.model.EmergencyResource;
import com.emergencias.emergency_management.model.ResourceRequest;
import com.emergencias.emergency_management.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ResourceService {

    private final ResourceRepository resourceRepository;

    public EmergencyResource create(ResourceRequest request) {

        EmergencyResource resource = new EmergencyResource(
                request.getId(),
                request.getName(),
                request.getType(),
                request.isAvailable(),
                request.getZoneName()
        );

        return resourceRepository.save(resource);
    }
}
