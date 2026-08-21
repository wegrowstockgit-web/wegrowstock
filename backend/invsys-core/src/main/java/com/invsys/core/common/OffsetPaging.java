package com.invsys.core.common;

import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * Maps 1-based API page/size/sort query params onto Spring Data {@link org.springframework.data.domain.PageRequest}.
 */
public final class OffsetPaging {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_SIZE = 50;
    public static final int MAX_SIZE = 100;

    private OffsetPaging() {
    }

    public static String keyword(String search) {
        if (search == null || search.isBlank()) {
            return "";
        }
        return search.trim();
    }

    public static org.springframework.data.domain.PageRequest of(
            int page,
            int size,
            String sort,
            String defaultProperty,
            Sort.Direction defaultDirection,
            Set<String> allowedProperties
    ) {
        int pageIndex = Math.max(page, 1) - 1;
        int pageSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        return org.springframework.data.domain.PageRequest.of(
                pageIndex,
                pageSize,
                parseSort(sort, defaultProperty, defaultDirection, allowedProperties));
    }

    static Sort parseSort(
            String sort,
            String defaultProperty,
            Sort.Direction defaultDirection,
            Set<String> allowedProperties
    ) {
        String raw = sort == null || sort.isBlank() ? defaultProperty : sort.trim();
        String[] parts = raw.split(",", 2);
        String property = parts[0].trim();
        boolean allowed = allowedProperties != null && allowedProperties.contains(property);
        if (!allowed) {
            return Sort.by(defaultDirection, defaultProperty);
        }
        Sort.Direction direction = defaultDirection;
        if (parts.length > 1) {
            String dir = parts[1].trim().toLowerCase();
            if ("asc".equals(dir)) {
                direction = Sort.Direction.ASC;
            } else if ("desc".equals(dir)) {
                direction = Sort.Direction.DESC;
            }
        }
        return Sort.by(direction, property);
    }
}
