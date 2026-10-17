// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.wm.dao;

import dev.mesbase.business.wm.domain.WmWarehouseErpExtraEntity;
import dev.mesbase.business.wm.domain.WmWarehouseErpExtraExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface WmWarehouseErpExtraMapper {
    long countByExample(WmWarehouseErpExtraExample example);

    int deleteByExample(WmWarehouseErpExtraExample example);

    int deleteByPrimaryKey(Long id);

    int insert(WmWarehouseErpExtraEntity row);

    int insertSelective(WmWarehouseErpExtraEntity row);

    List<WmWarehouseErpExtraEntity> selectByExample(WmWarehouseErpExtraExample example);

    WmWarehouseErpExtraEntity selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") WmWarehouseErpExtraEntity row,
                                 @Param("example") WmWarehouseErpExtraExample example);

    int updateByExample(@Param("row") WmWarehouseErpExtraEntity row,
                        @Param("example") WmWarehouseErpExtraExample example);

    int updateByPrimaryKeySelective(WmWarehouseErpExtraEntity row);

    int updateByPrimaryKey(WmWarehouseErpExtraEntity row);
}