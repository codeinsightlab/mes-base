package dev.mesbase.business.wm.dao;

import dev.mesbase.business.wm.domain.WmStockDocumentEntity;
import dev.mesbase.business.wm.domain.WmStockDocumentExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface WmStockDocumentMapper {
    long countByExample(WmStockDocumentExample example);

    int deleteByExample(WmStockDocumentExample example);

    int deleteByPrimaryKey(Long id);

    int insert(WmStockDocumentEntity row);

    int insertSelective(WmStockDocumentEntity row);

    List<WmStockDocumentEntity> selectByExample(WmStockDocumentExample example);

    WmStockDocumentEntity selectByPrimaryKey(Long id);

    WmStockDocumentEntity selectByPrimaryKeyForUpdate(Long id);

    int updateByExampleSelective(@Param("row") WmStockDocumentEntity row,
                                 @Param("example") WmStockDocumentExample example);

    int updateByExample(@Param("row") WmStockDocumentEntity row, @Param("example") WmStockDocumentExample example);

    int updateByPrimaryKeySelective(WmStockDocumentEntity row);

    int updateByPrimaryKey(WmStockDocumentEntity row);
}