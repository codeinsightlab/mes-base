package dev.mesbase.business.quality.dao;

import dev.mesbase.business.quality.domain.MesProductionDefectEntity;
import dev.mesbase.business.quality.domain.MesProductionDefectExample;
import java.util.List;
import java.math.BigDecimal;
import dev.mesbase.business.quality.domain.ProductionDefectModels.NodeTotal;
import org.apache.ibatis.annotations.Param;

public interface MesProductionDefectMapper {
    boolean existsExecutionHistory(@Param("executionId") Long executionId);

    long countByExample(MesProductionDefectExample example);

    int insert(MesProductionDefectEntity row);

    List<MesProductionDefectEntity> selectByExample(MesProductionDefectExample example);

    MesProductionDefectEntity selectByPrimaryKey(Long id);

    List<MesProductionDefectEntity> selectLatest(@Param("executionId") Long executionId,
                                                 @Param("workorderId") Long workorderId,
                                                 @Param("affectedNodeId") Long affectedNodeId);


}
