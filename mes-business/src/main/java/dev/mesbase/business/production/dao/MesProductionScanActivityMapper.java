// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.production.dao;

import dev.mesbase.business.production.domain.MesProductionScanActivityEntity;
import dev.mesbase.business.production.domain.MesProductionScanActivityExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface MesProductionScanActivityMapper {
    long countByExample(MesProductionScanActivityExample example);

    int deleteByExample(MesProductionScanActivityExample example);

    int deleteByPrimaryKey(String id);

    int insert(MesProductionScanActivityEntity row);

    int insertSelective(MesProductionScanActivityEntity row);

    List<MesProductionScanActivityEntity> selectByExample(MesProductionScanActivityExample example);

    MesProductionScanActivityEntity selectByPrimaryKeyForUpdate(String id);

    MesProductionScanActivityEntity selectByPrimaryKey(String id);

    int updateByExampleSelective(@Param("row") MesProductionScanActivityEntity row,
                                 @Param("example") MesProductionScanActivityExample example);

    int updateByExample(@Param("row") MesProductionScanActivityEntity row,
                        @Param("example") MesProductionScanActivityExample example);

    int updateByPrimaryKeySelective(MesProductionScanActivityEntity row);

    int updateByPrimaryKey(MesProductionScanActivityEntity row);
}