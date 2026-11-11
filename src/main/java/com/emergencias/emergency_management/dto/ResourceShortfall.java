package com.emergencias.emergency_management.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ResourceShortfall {

    private String requirement;
    private int requested;
    private int found;
}
