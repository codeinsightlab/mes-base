package dev.mesbase.business.wm.dao;

import dev.mesbase.business.wm.domain.WmInventoryEntity;
import dev.mesbase.business.wm.domain.WmInventoryExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface WmInventoryMapper {
    long countByExample(WmInventoryExample example);

    int deleteByExample(WmInventoryExample example);

    int deleteByPrimaryKey(Long id);

    int insert(WmInventoryEntity row);

    int insertSelective(WmInventoryEntity row);

    List<WmInventoryEntity> selectByExample(WmInventoryExample example);

    WmInventoryEntity selectByPrimaryKey(Long id);

    WmInventoryEntity selectByPrimaryKeyForUpdate(Long id);

    int updateByExampleSelective(@Param("row") WmInventoryEntity row, @Param("example") WmInventoryExample example);

    int updateByExample(@Param("row") WmInventoryEntity row, @Param("example") WmInventoryExample example);

    int updateByPrimaryKeySelective(WmInventoryEntity row);

    int updateByPrimaryKey(WmInventoryEntity row);
}