// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.sales.dao;

import dev.mesbase.business.sales.domain.MesSalesOrderItemEntity;
import dev.mesbase.business.sales.domain.MesSalesOrderItemExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface MesSalesOrderItemMapper {
    long countByExample(MesSalesOrderItemExample example);

    int deleteByExample(MesSalesOrderItemExample example);

    int deleteByPrimaryKey(Long id);

    int insert(MesSalesOrderItemEntity row);

    int insertSelective(MesSalesOrderItemEntity row);

    List<MesSalesOrderItemEntity> selectByExample(MesSalesOrderItemExample example);

    MesSalesOrderItemEntity selectByPrimaryKeyForUpdate(Long id);

    MesSalesOrderItemEntity selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") MesSalesOrderItemEntity row, @Param("example") MesSalesOrderItemExample example);

    int updateByExample(@Param("row") MesSalesOrderItemEntity row, @Param("example") MesSalesOrderItemExample example);

    int updateByPrimaryKeySelective(MesSalesOrderItemEntity row);

    int updateByPrimaryKey(MesSalesOrderItemEntity row);
}