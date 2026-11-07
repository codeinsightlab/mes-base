package dev.mesbase.business.wm.dao;

import java.math.BigDecimal;
import org.apache.ibatis.annotations.Param;

public interface StockDocumentExtraMapper {
    java.util.List<dev.mesbase.business.wm.domain.WmStockDocumentEntity> selectByLineFilters(
            @Param("query") dev.mesbase.business.wm.domain.StockDocumentModels.Query query);

    record LineLabel(Long lineId, String materialCode, String materialName, String warehouseCode,
                     String warehouseName, String warehouseSource, String unitName) {
    }

    java.util.List<LineLabel> lineLabels(@Param("id") Long id);

    Long pairedDocumentId(@Param("id") Long id);

    String latestInboundAuditId(@Param("executionCode") String executionCode);

    BigDecimal formalProductionInbound(@Param("executionCode") String executionCode);

    long invalidProductionInboundLines(@Param("executionCode") String executionCode);

    BigDecimal netProductionInboundForOrder(@Param("workorderId") Long workorderId);

    BigDecimal netProductionInbound(@Param("executionCode") String executionCode);
}
