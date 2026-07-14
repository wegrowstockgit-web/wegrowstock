package com.invsys;

import com.invsys.service.PickingService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PathOptimizationHeuristicTest {

    private final PickingService pickingService = new PickingService(null);

    @Test
    void groupsNearbyBinsBeforeDistantAisles() {
        List<String> paths = List.of(
                "WH-01/Z-A/A-2/B-10",
                "WH-01/Z-B/A-1/B-01",
                "WH-01/Z-A/A-2/B-01",
                "WH-01/Z-A/A-1/B-01"
        );
        Map<String, Integer> seq = Map.of(
                "WH-01/Z-A/A-1/B-01", 1,
                "WH-01/Z-A/A-2/B-01", 2,
                "WH-01/Z-A/A-2/B-10", 3,
                "WH-01/Z-B/A-1/B-01", 10
        );

        List<String> optimized = pickingService.optimizePendingPaths(paths, seq);

        assertThat(optimized.getFirst()).isEqualTo("WH-01/Z-A/A-1/B-01");
        assertThat(optimized.getLast()).isEqualTo("WH-01/Z-B/A-1/B-01");
        assertThat(optimized).containsExactly(
                "WH-01/Z-A/A-1/B-01",
                "WH-01/Z-A/A-2/B-01",
                "WH-01/Z-A/A-2/B-10",
                "WH-01/Z-B/A-1/B-01"
        );
    }
}
