// Manually maintained MBG mapper; merge schema changes before regenerating.
package dev.mesbase.business.basic.dao;

import dev.mesbase.business.basic.domain.MaterialBomEntity;
import dev.mesbase.business.basic.domain.MaterialBomExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface MaterialBomMapper {
    long countByExample(MaterialBomExample example);

    int deleteByExample(MaterialBomExample example);

    int deleteByPrimaryKey(Long id);

    int insert(MaterialBomEntity row);

    int insertSelective(MaterialBomEntity row);

    List<MaterialBomEntity> selectByExample(MaterialBomExample example);

    MaterialBomEntity selectByPrimaryKeyForUpdate(Long id);

    MaterialBomEntity selectByPrimaryKey(Long id);

    /** Business query. Preserve when updating standard single-table mappings. */
    MaterialBomEntity selectDefaultByProductId(@Param("productId") Long productId);

    int updateByExampleSelective(@Param("row") MaterialBomEntity row, @Param("example") MaterialBomExample example);

    int updateByExample(@Param("row") MaterialBomEntity row, @Param("example") MaterialBomExample example);

    int updateByPrimaryKeySelective(MaterialBomEntity row);

    int updateByPrimaryKey(MaterialBomEntity row);
}
