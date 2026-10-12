// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.basic.dao;

import dev.mesbase.business.basic.domain.SafetyStockEntity;
import dev.mesbase.business.basic.domain.SafetyStockExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface SafetyStockMapper {
    long countByExample(SafetyStockExample example);

    int deleteByExample(SafetyStockExample example);

    int deleteByPrimaryKey(Long id);

    int insert(SafetyStockEntity row);

    int insertSelective(SafetyStockEntity row);

    List<SafetyStockEntity> selectByExample(SafetyStockExample example);

    SafetyStockEntity selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") SafetyStockEntity row, @Param("example") SafetyStockExample example);

    int updateByExample(@Param("row") SafetyStockEntity row, @Param("example") SafetyStockExample example);

    int updateByPrimaryKeySelective(SafetyStockEntity row);

    int updateByPrimaryKey(SafetyStockEntity row);
}