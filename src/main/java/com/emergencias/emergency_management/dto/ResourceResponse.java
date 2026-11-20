package com.emergencias.emergency_management.dto;

import com.emergencias.emergency_management.model.EmergencyResource;
import lombok.Builder;
import lombok.Getter;

/**
 * A registered resource, without exposing the graph entity.
 */
@Getter
@Builder
public class ResourceResponse {

    private String id;
    private String name;
    private String type;
    private boolean available;
    private String zoneCode;
    private String zoneName;

    public static ResourceResponse from(EmergencyResource resource) {
        return ResourceResponse.builder()
                .id(resource.getId())
                .name(resource.getName())
                .type(resource.getType())
                .available(resource.isAvailable())
                .zoneCode(resource.getZone() != null
                        ? resource.getZone().getIneCode()
                        : null)
                .zoneName(resource.getZone() != null
                        ? resource.getZone().getName()
                        : null)
                .build();
    }
}
