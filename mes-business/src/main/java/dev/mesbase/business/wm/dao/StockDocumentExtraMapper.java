package dev.mesbase.business.wm.dao;

import java.math.BigDecimal;
import org.apache.ibatis.annotations.Param;

public interface StockDocumentExtraMapper {
    Long pairedDocumentId(@Param("id") Long id);

    String latestInboundAuditId(@Param("executionCode") String executionCode);

    BigDecimal formalProductionInbound(@Param("executionCode") String executionCode);

    long invalidProductionInboundLines(@Param("executionCode") String executionCode);

    BigDecimal netProductionInboundForOrder(@Param("workorderId") Long workorderId);

    BigDecimal netProductionInbound(@Param("executionCode") String executionCode);
}
