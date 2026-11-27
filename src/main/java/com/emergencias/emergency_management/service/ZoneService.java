package com.emergencias.emergency_management.service;

import com.emergencias.emergency_management.dto.ZoneSummary;
import com.emergencias.emergency_management.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The municipalities, for the lists and the map of a client.
 */
@Service
@RequiredArgsConstructor
public class ZoneService {

    private final ZoneRepository zoneRepository;

    @Transactional(readOnly = true)
    public List<ZoneSummary> list() {
        return zoneRepository.findAll(Sort.by("name")).stream()
                .map(ZoneSummary::from)
                .toList();
    }
}
