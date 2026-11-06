// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.purchase.dao;

import dev.mesbase.business.purchase.domain.PurchaseSupplierEntity;
import dev.mesbase.business.purchase.domain.PurchaseSupplierExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface PurchaseSupplierMapper {
    long countByExample(PurchaseSupplierExample example);

    int deleteByExample(PurchaseSupplierExample example);

    int deleteByPrimaryKey(Long id);

    int insert(PurchaseSupplierEntity row);

    int insertSelective(PurchaseSupplierEntity row);

    List<PurchaseSupplierEntity> selectByExample(PurchaseSupplierExample example);

    PurchaseSupplierEntity selectByPrimaryKeyForUpdate(Long id);

    PurchaseSupplierEntity selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") PurchaseSupplierEntity row,
                                 @Param("example") PurchaseSupplierExample example);

    int updateByExample(@Param("row") PurchaseSupplierEntity row, @Param("example") PurchaseSupplierExample example);

    int updateByPrimaryKeySelective(PurchaseSupplierEntity row);

    int updateByPrimaryKey(PurchaseSupplierEntity row);
}
