package dev.mesbase.business.wm.dao;

import dev.mesbase.business.wm.domain.WmStockActionEntity;
import dev.mesbase.business.wm.domain.WmStockActionExample;
import java.util.List;
import java.math.BigDecimal;
import org.apache.ibatis.annotations.Param;

public interface WmStockActionMapper {
    BigDecimal sumReturns(@Param("reference") String reference, @Param("type") String type);

    long countByExample(WmStockActionExample example);

    int deleteByExample(WmStockActionExample example);

    int deleteByPrimaryKey(String id);

    int insert(WmStockActionEntity row);

    int insertSelective(WmStockActionEntity row);

    List<WmStockActionEntity> selectByExample(WmStockActionExample example);

    WmStockActionEntity selectByPrimaryKey(String id);

    int updateByExampleSelective(@Param("row") WmStockActionEntity row, @Param("example") WmStockActionExample example);

    int updateByExample(@Param("row") WmStockActionEntity row, @Param("example") WmStockActionExample example);

    int updateByPrimaryKeySelective(WmStockActionEntity row);

    int updateByPrimaryKey(WmStockActionEntity row);
}