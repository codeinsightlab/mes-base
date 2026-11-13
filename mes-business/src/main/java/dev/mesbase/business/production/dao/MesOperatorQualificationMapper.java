// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.production.dao;

import dev.mesbase.business.production.domain.MesOperatorQualificationEntity;
import dev.mesbase.business.production.domain.MesOperatorQualificationExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface MesOperatorQualificationMapper {
    long countByExample(MesOperatorQualificationExample example);

    int deleteByExample(MesOperatorQualificationExample example);

    int deleteByPrimaryKey(Long userId);

    int insert(MesOperatorQualificationEntity row);

    int insertSelective(MesOperatorQualificationEntity row);

    List<MesOperatorQualificationEntity> selectByExample(MesOperatorQualificationExample example);

    MesOperatorQualificationEntity selectByPrimaryKeyForUpdate(Long userId);

    MesOperatorQualificationEntity selectByPrimaryKey(Long userId);

    int updateByExampleSelective(@Param("row") MesOperatorQualificationEntity row,
                                 @Param("example") MesOperatorQualificationExample example);

    int updateByExample(@Param("row") MesOperatorQualificationEntity row,
                        @Param("example") MesOperatorQualificationExample example);

    int updateByPrimaryKeySelective(MesOperatorQualificationEntity row);

    int updateByPrimaryKey(MesOperatorQualificationEntity row);
}