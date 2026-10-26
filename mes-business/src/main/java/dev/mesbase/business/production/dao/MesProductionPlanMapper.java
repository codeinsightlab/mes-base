// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.production.dao;

import dev.mesbase.business.production.domain.MesProductionPlanEntity;
import dev.mesbase.business.production.domain.MesProductionPlanExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface MesProductionPlanMapper {
    long countByExample(MesProductionPlanExample example);

    int deleteByExample(MesProductionPlanExample example);

    int deleteByPrimaryKey(Long id);

    int insert(MesProductionPlanEntity row);

    int insertSelective(MesProductionPlanEntity row);

    List<MesProductionPlanEntity> selectByExample(MesProductionPlanExample example);

    MesProductionPlanEntity selectByPrimaryKeyForUpdate(Long id);

    MesProductionPlanEntity selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") MesProductionPlanEntity row, @Param("example") MesProductionPlanExample example);

    int updateByExample(@Param("row") MesProductionPlanEntity row, @Param("example") MesProductionPlanExample example);

    int updateByPrimaryKeySelective(MesProductionPlanEntity row);

    int updateByPrimaryKey(MesProductionPlanEntity row);
}