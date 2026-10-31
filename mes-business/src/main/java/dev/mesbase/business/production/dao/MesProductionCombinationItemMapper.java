// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.production.dao;

import dev.mesbase.business.production.domain.MesProductionCombinationItemEntity;
import dev.mesbase.business.production.domain.MesProductionCombinationItemExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface MesProductionCombinationItemMapper {
    long countByExample(MesProductionCombinationItemExample example);

    int deleteByExample(MesProductionCombinationItemExample example);

    int deleteByPrimaryKey(Long id);

    int insert(MesProductionCombinationItemEntity row);

    int insertSelective(MesProductionCombinationItemEntity row);

    List<MesProductionCombinationItemEntity> selectByExample(MesProductionCombinationItemExample example);

    MesProductionCombinationItemEntity selectByPrimaryKeyForUpdate(Long id);

    MesProductionCombinationItemEntity selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") MesProductionCombinationItemEntity row,
                                 @Param("example") MesProductionCombinationItemExample example);

    int updateByExample(@Param("row") MesProductionCombinationItemEntity row,
                        @Param("example") MesProductionCombinationItemExample example);

    int updateByPrimaryKeySelective(MesProductionCombinationItemEntity row);

    int updateByPrimaryKey(MesProductionCombinationItemEntity row);
}