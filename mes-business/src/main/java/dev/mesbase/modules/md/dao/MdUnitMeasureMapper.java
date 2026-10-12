// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.modules.md.dao;

import dev.mesbase.modules.md.domain.MdUnitMeasureEntity;
import dev.mesbase.modules.md.domain.MdUnitMeasureExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface MdUnitMeasureMapper {
    long countByExample(MdUnitMeasureExample example);

    int deleteByExample(MdUnitMeasureExample example);

    int deleteByPrimaryKey(Long measureId);

    int insert(MdUnitMeasureEntity row);

    int insertSelective(MdUnitMeasureEntity row);

    List<MdUnitMeasureEntity> selectByExample(MdUnitMeasureExample example);

    MdUnitMeasureEntity selectByPrimaryKey(Long measureId);

    int updateByExampleSelective(@Param("row") MdUnitMeasureEntity row, @Param("example") MdUnitMeasureExample example);

    int updateByExample(@Param("row") MdUnitMeasureEntity row, @Param("example") MdUnitMeasureExample example);

    int updateByPrimaryKeySelective(MdUnitMeasureEntity row);

    int updateByPrimaryKey(MdUnitMeasureEntity row);
}