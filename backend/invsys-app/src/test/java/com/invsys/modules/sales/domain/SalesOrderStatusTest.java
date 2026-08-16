package com.invsys.modules.sales.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderStatusTest {

    @Test
    void allocatableStatusesIncludeQuoteConversionAndPartials() {
        assertThat(SalesOrderStatus.canAllocate("UNALLOCATED")).isTrue();
        assertThat(SalesOrderStatus.canAllocate("PARTIALLY_ALLOCATED")).isTrue();
        assertThat(SalesOrderStatus.canAllocate("QUOTE_READY")).isFalse();
        assertThat(SalesOrderStatus.canEditQuote("PENDING_REP_APPROVAL")).isTrue();
        assertThat(SalesOrderStatus.isQuoteInbox("PENDING_REP_APPROVAL")).isTrue();
        assertThat(SalesOrderStatus.isBackorderVisible("BACKORDERED")).isTrue();
        assertThat(AllocationPolicy.fromString(null)).isEqualTo(AllocationPolicy.ALLOW_PARTIAL);
        assertThat(AllocationPolicy.fromString("ship_complete")).isEqualTo(AllocationPolicy.SHIP_COMPLETE);
    }
}
