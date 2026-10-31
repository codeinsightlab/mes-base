// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.production.dao;

import dev.mesbase.business.production.domain.MesProductionCombinationEntity;
import dev.mesbase.business.production.domain.MesProductionCombinationExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface MesProductionCombinationMapper {
    long countByExample(MesProductionCombinationExample example);

    int deleteByExample(MesProductionCombinationExample example);

    int deleteByPrimaryKey(Long id);

    int insert(MesProductionCombinationEntity row);

    int insertSelective(MesProductionCombinationEntity row);

    List<MesProductionCombinationEntity> selectByExample(MesProductionCombinationExample example);

    MesProductionCombinationEntity selectByPrimaryKeyForUpdate(Long id);

    MesProductionCombinationEntity selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") MesProductionCombinationEntity row,
                                 @Param("example") MesProductionCombinationExample example);

    int updateByExample(@Param("row") MesProductionCombinationEntity row,
                        @Param("example") MesProductionCombinationExample example);

    int updateByPrimaryKeySelective(MesProductionCombinationEntity row);

    int updateByPrimaryKey(MesProductionCombinationEntity row);
}