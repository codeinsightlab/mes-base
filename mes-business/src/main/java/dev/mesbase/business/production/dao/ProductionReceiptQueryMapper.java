package dev.mesbase.business.production.dao;

import dev.mesbase.business.production.domain.ProductionReceiptModels.Query;
import dev.mesbase.business.production.domain.ProductionReceiptRow;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 跨执行、工单、物料及仓库的只读列表投影。
 */
public interface ProductionReceiptQueryMapper {
    List<ProductionReceiptRow> page(@Param("query") Query query);

    ProductionReceiptRow detail(@Param("id") String id);
}
