// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.purchase.dao;

import dev.mesbase.business.purchase.domain.PurchaseReferencePriceEntity;
import dev.mesbase.business.purchase.domain.PurchaseReferencePriceExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface PurchaseReferencePriceMapper {
    long countByExample(PurchaseReferencePriceExample example);

    int deleteByExample(PurchaseReferencePriceExample example);

    int deleteByPrimaryKey(Long id);

    int insert(PurchaseReferencePriceEntity row);

    int insertSelective(PurchaseReferencePriceEntity row);

    List<PurchaseReferencePriceEntity> selectByExample(PurchaseReferencePriceExample example);

    PurchaseReferencePriceEntity selectByPrimaryKeyForUpdate(Long id);

    PurchaseReferencePriceEntity selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") PurchaseReferencePriceEntity row,
                                 @Param("example") PurchaseReferencePriceExample example);

    int updateByExample(@Param("row") PurchaseReferencePriceEntity row,
                        @Param("example") PurchaseReferencePriceExample example);

    int updateByPrimaryKeySelective(PurchaseReferencePriceEntity row);

    int updateByPrimaryKey(PurchaseReferencePriceEntity row);
}
