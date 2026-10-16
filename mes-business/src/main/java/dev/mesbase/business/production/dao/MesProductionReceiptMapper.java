package dev.mesbase.business.production.dao;

import dev.mesbase.business.production.domain.MesProductionReceiptEntity;
import dev.mesbase.business.production.domain.MesProductionReceiptExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import java.math.BigDecimal;

public interface MesProductionReceiptMapper {
    BigDecimal netQuantity(@Param("executionId") Long executionId);

    BigDecimal originalBalance(@Param("id") String id);

    long countByExample(MesProductionReceiptExample example);

    int deleteByExample(MesProductionReceiptExample example);

    int deleteByPrimaryKey(String id);

    int insert(MesProductionReceiptEntity row);

    int insertSelective(MesProductionReceiptEntity row);

    List<MesProductionReceiptEntity> selectByExample(MesProductionReceiptExample example);

    MesProductionReceiptEntity selectByPrimaryKey(String id);

    int updateByExampleSelective(@Param("row") MesProductionReceiptEntity row,
                                 @Param("example") MesProductionReceiptExample example);

    int updateByExample(@Param("row") MesProductionReceiptEntity row,
                        @Param("example") MesProductionReceiptExample example);

    int updateByPrimaryKeySelective(MesProductionReceiptEntity row);

    int updateByPrimaryKey(MesProductionReceiptEntity row);
}