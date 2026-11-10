package com.emergencias.emergency_management.service;

import com.emergencias.emergency_management.model.EmergencyResource;
import com.emergencias.emergency_management.model.ResourceRequest;
import com.emergencias.emergency_management.repository.ResourceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceServiceTest {

    @Mock
    private ResourceRepository resourceRepository;

    @InjectMocks
    private ResourceService resourceService;

    @Test
    void createMapsRequestAndReturnsPersistedResource() {
        ResourceRequest request = new ResourceRequest(
                "resource-1", "Fire engine", "Fire", true, "North");
        EmergencyResource persistedResource = new EmergencyResource(
                "resource-1", "Fire engine", "Fire", true, "North");
        when(resourceRepository.save(any(EmergencyResource.class)))
                .thenReturn(persistedResource);

        EmergencyResource result = resourceService.create(request);

        ArgumentCaptor<EmergencyResource> resourceCaptor =
                ArgumentCaptor.forClass(EmergencyResource.class);
        verify(resourceRepository).save(resourceCaptor.capture());
        EmergencyResource savedResource = resourceCaptor.getValue();
        assertEquals("resource-1", savedResource.getId());
        assertEquals("Fire engine", savedResource.getName());
        assertEquals("Fire", savedResource.getType());
        assertTrue(savedResource.isAvailable());
        assertEquals("North", savedResource.getZone().getName());
        assertSame(persistedResource, result);
    }
}
