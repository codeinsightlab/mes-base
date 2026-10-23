// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.basic.dao;

import dev.mesbase.business.basic.domain.MaterialBomItemEntity;
import dev.mesbase.business.basic.domain.MaterialBomItemExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface MaterialBomItemMapper {
    long countByExample(MaterialBomItemExample example);

    int deleteByExample(MaterialBomItemExample example);

    int deleteByPrimaryKey(Long id);

    int insert(MaterialBomItemEntity row);

    int insertSelective(MaterialBomItemEntity row);

    List<MaterialBomItemEntity> selectByExample(MaterialBomItemExample example);

    MaterialBomItemEntity selectByPrimaryKeyForUpdate(Long id);

    MaterialBomItemEntity selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") MaterialBomItemEntity row,
                                 @Param("example") MaterialBomItemExample example);

    int updateByExample(@Param("row") MaterialBomItemEntity row, @Param("example") MaterialBomItemExample example);

    int updateByPrimaryKeySelective(MaterialBomItemEntity row);

    int updateByPrimaryKey(MaterialBomItemEntity row);
}
