package com.emergencias.emergency_management.service;

import com.emergencias.emergency_management.dto.ZoneSummary;
import com.emergencias.emergency_management.model.Zone;
import com.emergencias.emergency_management.repository.ZoneRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ZoneServiceTest {

    @Mock
    private ZoneRepository zoneRepository;

    @InjectMocks
    private ZoneService zoneService;

    @Test
    void zonesAreListedByName() {
        when(zoneRepository.findAll(Sort.by("name"))).thenReturn(List.of(
                new Zone("39001", "Alfoz de Lloredo", 43.3657, -4.1785),
                new Zone("39014", "Cabuérniga", 43.2265, -4.3016)));

        List<ZoneSummary> zones = zoneService.list();

        assertEquals(List.of(
                new ZoneSummary("39001", "Alfoz de Lloredo", 43.3657, -4.1785),
                new ZoneSummary("39014", "Cabuérniga", 43.2265, -4.3016)), zones);
    }
}
