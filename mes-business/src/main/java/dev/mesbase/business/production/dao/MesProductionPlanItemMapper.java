// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.production.dao;

import dev.mesbase.business.production.domain.MesProductionPlanItemEntity;
import dev.mesbase.business.production.domain.MesProductionPlanItemExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface MesProductionPlanItemMapper {
    long countByExample(MesProductionPlanItemExample example);

    int deleteByExample(MesProductionPlanItemExample example);

    int deleteByPrimaryKey(Long id);

    int insert(MesProductionPlanItemEntity row);

    int insertSelective(MesProductionPlanItemEntity row);

    List<MesProductionPlanItemEntity> selectByExample(MesProductionPlanItemExample example);

    MesProductionPlanItemEntity selectByPrimaryKeyForUpdate(Long id);

    MesProductionPlanItemEntity selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") MesProductionPlanItemEntity row, @Param("example") MesProductionPlanItemExample example);

    int updateByExample(@Param("row") MesProductionPlanItemEntity row, @Param("example") MesProductionPlanItemExample example);

    int updateByPrimaryKeySelective(MesProductionPlanItemEntity row);

    int updateByPrimaryKey(MesProductionPlanItemEntity row);
}