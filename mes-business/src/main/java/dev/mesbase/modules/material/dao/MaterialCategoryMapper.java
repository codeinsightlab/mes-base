// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.modules.material.dao;

import dev.mesbase.modules.material.domain.MaterialCategoryEntity;
import dev.mesbase.modules.material.domain.MaterialCategoryExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface MaterialCategoryMapper {
    long countByExample(MaterialCategoryExample example);

    int deleteByExample(MaterialCategoryExample example);

    int deleteByPrimaryKey(Long id);

    int insert(MaterialCategoryEntity row);

    int insertSelective(MaterialCategoryEntity row);

    List<MaterialCategoryEntity> selectByExample(MaterialCategoryExample example);

    MaterialCategoryEntity selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") MaterialCategoryEntity row,
                                 @Param("example") MaterialCategoryExample example);

    int updateByExample(@Param("row") MaterialCategoryEntity row, @Param("example") MaterialCategoryExample example);

    int updateByPrimaryKeySelective(MaterialCategoryEntity row);

    int updateByPrimaryKey(MaterialCategoryEntity row);
}