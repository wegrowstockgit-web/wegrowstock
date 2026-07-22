package com.invsys.repository;

import com.invsys.domain.ReturnLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import com.invsys.domain.ReturnOrder;

public interface ReturnLineRepository extends JpaRepository<ReturnLine, UUID> {
    List<ReturnLine> findByReturnId(UUID returnId);

    @Query("""
            SELECT COALESCE(SUM(rl.quantityExpected), 0)
            FROM ReturnLine rl
            JOIN ReturnOrder r ON r.id = rl.returnId
            WHERE rl.salesOrderLineId = :lineId AND r.status NOT IN ('REJECTED', 'CLOSED')
            """)
    BigDecimal sumExpectedForLine(@Param("lineId") UUID salesOrderLineId);
}
