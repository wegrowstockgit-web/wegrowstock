package com.invsys.core.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PageResponse<T>(
        List<T> items,
        String nextCursor,
        boolean hasMore,
        Long totalElements,
        Integer totalPages,
        Integer page,
        Integer size
) {
    /** Cursor-style page used by variant catalog listing. */
    public PageResponse(List<T> items, String nextCursor, boolean hasMore) {
        this(items, nextCursor, hasMore, null, null, null, null);
    }

    public static <T> PageResponse<T> of(org.springframework.data.domain.Page<T> springPage) {
        return of(springPage, springPage.getContent());
    }

    public static <T, S> PageResponse<T> of(org.springframework.data.domain.Page<S> springPage, List<T> items) {
        return new PageResponse<>(
                items,
                null,
                springPage.hasNext(),
                springPage.getTotalElements(),
                springPage.getTotalPages(),
                springPage.getNumber() + 1,
                springPage.getSize()
        );
    }
}
