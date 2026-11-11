package com.emergencias.emergency_management.service;

import com.emergencias.emergency_management.model.EmergencyResource;
import com.emergencias.emergency_management.model.ResourceRequest;
import com.emergencias.emergency_management.model.Zone;
import com.emergencias.emergency_management.repository.ResourceRepository;
import com.emergencias.emergency_management.repository.ZoneRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceServiceTest {

    private static final Zone TAMA =
            new Zone("39022", "Cillorigo de Liébana", 43.17608, -4.60108);

    @Mock
    private ResourceRepository resourceRepository;

    @Mock
    private ZoneRepository zoneRepository;

    @InjectMocks
    private ResourceService resourceService;

    @Test
    void createMapsRequestAndReturnsPersistedResource() {
        when(zoneRepository.findById("39022"))
                .thenReturn(Optional.of(TAMA));
        ResourceRequest request = new ResourceRequest(
                "BRP-41", "Autobomba de primera salida", "RURAL_ENGINE",
                true, "39022");
        EmergencyResource persistedResource = new EmergencyResource(
                "BRP-41", "Autobomba de primera salida", "RURAL_ENGINE",
                true, TAMA);
        when(resourceRepository.save(any(EmergencyResource.class)))
                .thenReturn(persistedResource);

        EmergencyResource result = resourceService.create(request);

        ArgumentCaptor<EmergencyResource> resourceCaptor =
                ArgumentCaptor.forClass(EmergencyResource.class);
        verify(resourceRepository).save(resourceCaptor.capture());
        EmergencyResource savedResource = resourceCaptor.getValue();
        assertEquals("BRP-41", savedResource.getId());
        assertEquals("Autobomba de primera salida", savedResource.getName());
        assertEquals("RURAL_ENGINE", savedResource.getType());
        assertTrue(savedResource.isAvailable());
        assertSame(TAMA, savedResource.getZone());
        assertSame(persistedResource, result);
    }

    @Test
    void createRejectsUnknownZone() {
        when(zoneRepository.findById("99999"))
                .thenReturn(Optional.empty());
        ResourceRequest request = new ResourceRequest(
                "BRP-41", "Autobomba de primera salida", "RURAL_ENGINE",
                true, "99999");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> resourceService.create(request));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(resourceRepository, never()).save(any());
    }
}
