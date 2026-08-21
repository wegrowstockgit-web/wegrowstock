package com.invsys.core.common;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class OffsetPagingTest {

    private static final Set<String> ALLOWED = Set.of("createdAt", "number", "name");

    @Test
    void mapsOneBasedPageAndClampsSize() {
        var page = OffsetPaging.of(2, 200, "createdAt,desc", "createdAt", Sort.Direction.DESC, ALLOWED);
        assertThat(page.getPageNumber()).isEqualTo(1);
        assertThat(page.getPageSize()).isEqualTo(OffsetPaging.MAX_SIZE);
        assertThat(page.getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void defaultsBlankSizeAndInvalidSort() {
        var page = OffsetPaging.of(0, 0, "injected,asc", "createdAt", Sort.Direction.DESC, ALLOWED);
        assertThat(page.getPageNumber()).isZero();
        assertThat(page.getPageSize()).isEqualTo(OffsetPaging.DEFAULT_SIZE);
        assertThat(page.getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void parsesAscendingNameSort() {
        var page = OffsetPaging.of(1, 25, "name,asc", "createdAt", Sort.Direction.DESC, ALLOWED);
        assertThat(page.getPageSize()).isEqualTo(25);
        assertThat(page.getSort().getOrderFor("name").getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void blankSortAndUnknownDirectionKeepDefaults() {
        var blank = OffsetPaging.of(1, 10, "  ", "createdAt", Sort.Direction.DESC, ALLOWED);
        assertThat(blank.getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);

        var unknownDir = OffsetPaging.of(1, 10, "number,sideways", "createdAt", Sort.Direction.DESC, ALLOWED);
        assertThat(unknownDir.getSort().getOrderFor("number").getDirection()).isEqualTo(Sort.Direction.DESC);

        var desc = OffsetPaging.of(1, 10, "number,desc", "createdAt", Sort.Direction.ASC, ALLOWED);
        assertThat(desc.getSort().getOrderFor("number").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void pageResponseOfMappedItems() {
        org.springframework.data.domain.Page<Integer> spring =
                new org.springframework.data.domain.PageImpl<>(
                        List.of(1, 2),
                        org.springframework.data.domain.PageRequest.of(1, 2),
                        4);
        PageResponse<String> response = PageResponse.of(spring, List.of("one", "two"));
        assertThat(response.items()).containsExactly("one", "two");
        assertThat(response.page()).isEqualTo(2);
        assertThat(response.hasMore()).isFalse();
        assertThat(response.totalPages()).isEqualTo(2);
    }

    @Test
    void blankKeywordIsNull() {
        assertThat(OffsetPaging.keyword(null)).isEmpty();
        assertThat(OffsetPaging.keyword("   ")).isEmpty();
        assertThat(OffsetPaging.keyword(" Acme ")).isEqualTo("Acme");
    }

    @Test
    void pageResponseOfUsesOneBasedPage() {
        org.springframework.data.domain.Page<String> spring =
                new org.springframework.data.domain.PageImpl<>(
                        List.of("a", "b"),
                        org.springframework.data.domain.PageRequest.of(0, 2),
                        5);
        PageResponse<String> response = PageResponse.of(spring);
        assertThat(response.items()).containsExactly("a", "b");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.hasMore()).isTrue();
        assertThat(response.nextCursor()).isNull();
    }

    @Test
    void cursorConstructorLeavesOffsetFieldsNull() {
        PageResponse<String> cursor = new PageResponse<>(List.of("x"), "cursor-1", true);
        assertThat(cursor.hasMore()).isTrue();
        assertThat(cursor.nextCursor()).isEqualTo("cursor-1");
        assertThat(cursor.totalElements()).isNull();
        assertThat(cursor.page()).isNull();
    }
}
