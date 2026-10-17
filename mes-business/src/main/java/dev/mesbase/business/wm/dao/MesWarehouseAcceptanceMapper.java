package dev.mesbase.business.wm.dao;

import dev.mesbase.business.wm.domain.MesWarehouseAcceptanceEntity;
import dev.mesbase.business.wm.domain.MesWarehouseAcceptanceExample;
import java.util.List;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Param;

public interface MesWarehouseAcceptanceMapper {
    BigDecimal netReceipt(@Param("receiptId") String receiptId);

    BigDecimal netExecution(@Param("executionId") Long executionId);

    BigDecimal originalBalance(@Param("id") String id);

    long countByExample(MesWarehouseAcceptanceExample example);

    int deleteByExample(MesWarehouseAcceptanceExample example);

    int deleteByPrimaryKey(String id);

    int insert(MesWarehouseAcceptanceEntity row);

    int insertSelective(MesWarehouseAcceptanceEntity row);

    List<MesWarehouseAcceptanceEntity> selectByExample(MesWarehouseAcceptanceExample example);

    MesWarehouseAcceptanceEntity selectByPrimaryKey(String id);

    int updateByExampleSelective(@Param("row") MesWarehouseAcceptanceEntity row,
                                 @Param("example") MesWarehouseAcceptanceExample example);

    int updateByExample(@Param("row") MesWarehouseAcceptanceEntity row,
                        @Param("example") MesWarehouseAcceptanceExample example);

    int updateByPrimaryKeySelective(MesWarehouseAcceptanceEntity row);

    int updateByPrimaryKey(MesWarehouseAcceptanceEntity row);
}