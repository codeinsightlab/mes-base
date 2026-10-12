// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.modules.material.dao;

import dev.mesbase.modules.material.domain.MaterialEntity;
import dev.mesbase.modules.material.domain.MaterialExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface MaterialMapper {
    long countByExample(MaterialExample example);

    int deleteByExample(MaterialExample example);

    int deleteByPrimaryKey(Long id);

    int insert(MaterialEntity row);

    int insertSelective(MaterialEntity row);

    List<MaterialEntity> selectByExample(MaterialExample example);

    MaterialEntity selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") MaterialEntity row, @Param("example") MaterialExample example);

    int updateByExample(@Param("row") MaterialEntity row, @Param("example") MaterialExample example);

    int updateByPrimaryKeySelective(MaterialEntity row);

    int updateByPrimaryKey(MaterialEntity row);
}