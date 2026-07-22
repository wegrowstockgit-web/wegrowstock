package com.invsys.core.common;

import java.util.List;

public record PageResponse<T>(List<T> items, String nextCursor, boolean hasMore) {
}
