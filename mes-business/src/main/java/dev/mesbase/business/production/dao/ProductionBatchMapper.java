// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.production.dao;

import dev.mesbase.business.production.domain.ProductionBatchEntity;
import dev.mesbase.business.production.domain.ProductionBatchExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface ProductionBatchMapper {
    long countByExample(ProductionBatchExample example);

    int deleteByExample(ProductionBatchExample example);

    int deleteByPrimaryKey(Long id);

    int insert(ProductionBatchEntity row);

    int insertSelective(ProductionBatchEntity row);

    List<ProductionBatchEntity> selectByExample(ProductionBatchExample example);

    ProductionBatchEntity selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") ProductionBatchEntity row,
                                 @Param("example") ProductionBatchExample example);

    int updateByExample(@Param("row") ProductionBatchEntity row, @Param("example") ProductionBatchExample example);

    int updateByPrimaryKeySelective(ProductionBatchEntity row);

    int updateByPrimaryKey(ProductionBatchEntity row);
}