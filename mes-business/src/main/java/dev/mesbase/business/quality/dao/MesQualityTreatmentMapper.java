package dev.mesbase.business.quality.dao;

import dev.mesbase.business.quality.domain.MesQualityTreatmentEntity;
import dev.mesbase.business.quality.domain.MesQualityTreatmentExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface MesQualityTreatmentMapper {
    List<MesQualityTreatmentEntity> selectRecent(@Param("limit") int limit);

    long countByExample(MesQualityTreatmentExample example);

    int insert(MesQualityTreatmentEntity row);

    List<MesQualityTreatmentEntity> selectByExample(MesQualityTreatmentExample example);

    MesQualityTreatmentEntity selectByPrimaryKey(String id);

}