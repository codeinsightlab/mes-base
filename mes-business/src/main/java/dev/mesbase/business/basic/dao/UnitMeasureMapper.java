// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.basic.dao;

import dev.mesbase.business.basic.domain.UnitMeasureEntity;
import dev.mesbase.business.basic.domain.UnitMeasureExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface UnitMeasureMapper {
    long countByExample(UnitMeasureExample example);

    int deleteByExample(UnitMeasureExample example);

    int deleteByPrimaryKey(Long id);

    int insert(UnitMeasureEntity row);

    int insertSelective(UnitMeasureEntity row);

    List<UnitMeasureEntity> selectByExample(UnitMeasureExample example);

    UnitMeasureEntity selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") UnitMeasureEntity row, @Param("example") UnitMeasureExample example);

    int updateByExample(@Param("row") UnitMeasureEntity row, @Param("example") UnitMeasureExample example);

    int updateByPrimaryKeySelective(UnitMeasureEntity row);

    int updateByPrimaryKey(UnitMeasureEntity row);
}