// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.quality.dao;

import dev.mesbase.business.quality.domain.MesQualityTreatmentEntity;
import dev.mesbase.business.quality.domain.MesQualityTreatmentExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface MesQualityTreatmentMapper {
    long countByExample(MesQualityTreatmentExample example);

    int deleteByExample(MesQualityTreatmentExample example);

    int deleteByPrimaryKey(String id);

    int insert(MesQualityTreatmentEntity row);

    int insertSelective(MesQualityTreatmentEntity row);

    List<MesQualityTreatmentEntity> selectByExample(MesQualityTreatmentExample example);

    MesQualityTreatmentEntity selectByPrimaryKey(String id);

    int updateByExampleSelective(@Param("row") MesQualityTreatmentEntity row, @Param("example") MesQualityTreatmentExample example);

    int updateByExample(@Param("row") MesQualityTreatmentEntity row, @Param("example") MesQualityTreatmentExample example);

    int updateByPrimaryKeySelective(MesQualityTreatmentEntity row);

    int updateByPrimaryKey(MesQualityTreatmentEntity row);
}