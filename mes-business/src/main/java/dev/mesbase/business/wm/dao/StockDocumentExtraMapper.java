package dev.mesbase.business.wm.dao;

import java.math.BigDecimal;
import org.apache.ibatis.annotations.Param;

public interface StockDocumentExtraMapper {
    BigDecimal netProductionInboundForOrder(@Param("workorderId") Long workorderId);

    BigDecimal netProductionInbound(@Param("executionId") Long executionId);
}
