// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.wm.dao;

import dev.mesbase.business.wm.domain.WmWarehouseEntity;
import dev.mesbase.business.wm.domain.WmWarehouseExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface WmWarehouseMapper {
    long countByExample(WmWarehouseExample example);

    int deleteByExample(WmWarehouseExample example);

    int deleteByPrimaryKey(Long id);

    int insert(WmWarehouseEntity row);

    int insertSelective(WmWarehouseEntity row);

    List<WmWarehouseEntity> selectByExample(WmWarehouseExample example);

    WmWarehouseEntity selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") WmWarehouseEntity row, @Param("example") WmWarehouseExample example);

    int updateByExample(@Param("row") WmWarehouseEntity row, @Param("example") WmWarehouseExample example);

    int updateByPrimaryKeySelective(WmWarehouseEntity row);

    int updateByPrimaryKey(WmWarehouseEntity row);
}