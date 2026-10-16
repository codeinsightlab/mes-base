// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.production.dao;

import dev.mesbase.business.production.domain.MesProductionScanIdentityEntity;
import dev.mesbase.business.production.domain.MesProductionScanIdentityExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface MesProductionScanIdentityMapper {
    long countByExample(MesProductionScanIdentityExample example);

    int deleteByExample(MesProductionScanIdentityExample example);

    int deleteByPrimaryKey(String id);

    int insert(MesProductionScanIdentityEntity row);

    int insertSelective(MesProductionScanIdentityEntity row);

    List<MesProductionScanIdentityEntity> selectByExample(MesProductionScanIdentityExample example);

    MesProductionScanIdentityEntity selectByPrimaryKey(String id);

    int updateByExampleSelective(@Param("row") MesProductionScanIdentityEntity row, @Param("example") MesProductionScanIdentityExample example);

    int updateByExample(@Param("row") MesProductionScanIdentityEntity row, @Param("example") MesProductionScanIdentityExample example);

    int updateByPrimaryKeySelective(MesProductionScanIdentityEntity row);

    int updateByPrimaryKey(MesProductionScanIdentityEntity row);
}