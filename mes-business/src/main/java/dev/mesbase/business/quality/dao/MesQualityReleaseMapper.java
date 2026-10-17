// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.quality.dao;

import dev.mesbase.business.quality.domain.MesQualityReleaseEntity;
import dev.mesbase.business.quality.domain.MesQualityReleaseExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface MesQualityReleaseMapper {
    long countByExample(MesQualityReleaseExample example);

    int deleteByExample(MesQualityReleaseExample example);

    int deleteByPrimaryKey(String id);

    int insert(MesQualityReleaseEntity row);

    int insertSelective(MesQualityReleaseEntity row);

    List<MesQualityReleaseEntity> selectByExample(MesQualityReleaseExample example);

    MesQualityReleaseEntity selectByPrimaryKey(String id);

    int updateByExampleSelective(@Param("row") MesQualityReleaseEntity row,
                                 @Param("example") MesQualityReleaseExample example);

    int updateByExample(@Param("row") MesQualityReleaseEntity row, @Param("example") MesQualityReleaseExample example);

    int updateByPrimaryKeySelective(MesQualityReleaseEntity row);

    int updateByPrimaryKey(MesQualityReleaseEntity row);
}