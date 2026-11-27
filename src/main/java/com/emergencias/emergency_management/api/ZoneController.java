package com.emergencias.emergency_management.api;

import com.emergencias.emergency_management.dto.ZoneSummary;
import com.emergencias.emergency_management.service.ZoneService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST query of the municipalities, ordered by name.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/zones")
public class ZoneController {

    private final ZoneService zoneService;

    @GetMapping
    public List<ZoneSummary> list() {
        return zoneService.list();
    }
}
