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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    private static final ResourceType RURAL_ENGINE =
            new ResourceType("RURAL_ENGINE", false);

    @Mock
    private ResourceRepository resourceRepository;

    @Mock
    private ResourceTypeRepository resourceTypeRepository;

    @Mock
    private ZoneRepository zoneRepository;

    @InjectMocks
    private ResourceService resourceService;

    private static ResourceRequest request(Boolean available, String zone) {
        return new ResourceRequest(
                "BRP-41", "Autobomba de primera salida", "RURAL_ENGINE",
                available, zone);
    }

    private void givenValidRequestIsSaved() {
        when(zoneRepository.findById("39022")).thenReturn(Optional.of(TAMA));
        when(resourceTypeRepository.findById("RURAL_ENGINE"))
                .thenReturn(Optional.of(RURAL_ENGINE));
        when(resourceRepository.save(any(EmergencyResource.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private EmergencyResource savedResource() {
        ArgumentCaptor<EmergencyResource> captor =
                ArgumentCaptor.forClass(EmergencyResource.class);
        verify(resourceRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void createUsesTheExistingTypeAndReturnsAResponse() {
        givenValidRequestIsSaved();

        ResourceResponse response = resourceService.create(request(true, "39022"));

        EmergencyResource saved = savedResource();
        assertEquals("BRP-41", saved.getId());
        assertEquals("Autobomba de primera salida", saved.getName());
        assertEquals("RURAL_ENGINE", saved.getType());
        assertSame(RURAL_ENGINE, saved.getResourceType());
        assertSame(TAMA, saved.getZone());
        assertTrue(saved.isAvailable());

        assertEquals("BRP-41", response.getId());
        assertEquals("RURAL_ENGINE", response.getType());
        assertEquals("39022", response.getZoneCode());
        assertEquals("Cillorigo de Liébana", response.getZoneName());
        assertTrue(response.isAvailable());
    }

    @Test
    void resourceIsAvailableWhenAvailabilityIsMissing() {
        givenValidRequestIsSaved();

        resourceService.create(request(null, "39022"));

        assertTrue(savedResource().isAvailable());
    }

    @Test
    void resourceCanBeRegisteredAsNotAvailable() {
        givenValidRequestIsSaved();

        resourceService.create(request(false, "39022"));

        assertFalse(savedResource().isAvailable());
    }

    @Test
    void createRejectsExistingResource() {
        when(resourceRepository.existsById("BRP-41")).thenReturn(true);

        assertThrows(
                ConflictException.class,
                () -> resourceService.create(request(true, "39022")));

        verify(resourceRepository, never()).save(any());
    }

    @Test
    void createRejectsUnknownZone() {
        when(zoneRepository.findById("99999")).thenReturn(Optional.empty());

        assertThrows(
                InvalidRequestException.class,
                () -> resourceService.create(request(true, "99999")));

        verify(resourceRepository, never()).save(any());
    }

    @Test
    void createRejectsUnknownType() {
        when(zoneRepository.findById("39022")).thenReturn(Optional.of(TAMA));
        when(resourceTypeRepository.findById("RURAL_ENGINE"))
                .thenReturn(Optional.empty());

        assertThrows(
                InvalidRequestException.class,
                () -> resourceService.create(request(true, "39022")));

        verify(resourceRepository, never()).save(any());
    }
}
