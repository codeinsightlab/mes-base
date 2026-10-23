// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.production.dao;

import dev.mesbase.business.production.domain.MesProductionExecutionEntity;
import dev.mesbase.business.production.domain.MesProductionExecutionExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface MesProductionExecutionMapper {
    long countByExample(MesProductionExecutionExample example);

    int deleteByExample(MesProductionExecutionExample example);

    int deleteByPrimaryKey(Long id);

    int insert(MesProductionExecutionEntity row);

    int insertSelective(MesProductionExecutionEntity row);

    List<MesProductionExecutionEntity> selectByExample(MesProductionExecutionExample example);

    MesProductionExecutionEntity selectByPrimaryKeyForUpdate(Long id);

    MesProductionExecutionEntity selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") MesProductionExecutionEntity row,
                                 @Param("example") MesProductionExecutionExample example);

    int updateByExample(@Param("row") MesProductionExecutionEntity row,
                        @Param("example") MesProductionExecutionExample example);

    int updateByPrimaryKeySelective(MesProductionExecutionEntity row);

    int updateByPrimaryKey(MesProductionExecutionEntity row);
}