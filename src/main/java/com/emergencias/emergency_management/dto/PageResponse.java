package com.emergencias.emergency_management.dto;

import java.util.List;

/**
 * One page of a list. Pages are numbered from 0.
 */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    public static <T> PageResponse<T> of(List<T> items, int page, int size, long totalElements) {
        int totalPages = (int) ((totalElements + size - 1) / size);
        return new PageResponse<>(items, page, size, totalElements, totalPages);
    }
}
