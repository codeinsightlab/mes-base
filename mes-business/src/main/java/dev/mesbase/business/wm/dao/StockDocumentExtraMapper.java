package dev.mesbase.business.wm.dao;

import java.math.BigDecimal;
import org.apache.ibatis.annotations.Param;

public interface StockDocumentExtraMapper {
    Long pairedDocumentId(@Param("id") Long id);

    String latestInboundAuditId(@Param("executionId") Long executionId);

    BigDecimal formalProductionInbound(@Param("executionId") Long executionId);

    long invalidProductionInboundLines(@Param("executionId") Long executionId);

    BigDecimal netProductionInboundForOrder(@Param("workorderId") Long workorderId);

    BigDecimal netProductionInbound(@Param("executionId") Long executionId);
}
