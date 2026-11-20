package com.emergencias.emergency_management.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * A requirement of the rule that the available resources could not fully
 * cover.
 */
@Getter
@Builder
public class ResourceShortfall {

    private String requirement;
    private int requested;
    private int found;
}
