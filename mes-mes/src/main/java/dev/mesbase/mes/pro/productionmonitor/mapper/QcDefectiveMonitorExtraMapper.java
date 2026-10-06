package dev.mesbase.mes.pro.productionmonitor.mapper;

import dev.mesbase.mes.pro.productionmonitor.domain.row.ProductionMonitorDefectiveAggregateRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
@dev.mesbase.infrastructure.persistence.PersistenceOwner(dev.mesbase.infrastructure.persistence.PersistenceOwner.Kind.BUSINESS)
public interface QcDefectiveMonitorExtraMapper {
    List<ProductionMonitorDefectiveAggregateRow> selectWorkorderAggregates(@Param("workorderCodes") List<String> workorderCodes);
}
