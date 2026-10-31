// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.wm.dao;

import dev.mesbase.business.wm.domain.WmStockDocumentLineEntity;
import dev.mesbase.business.wm.domain.WmStockDocumentLineExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface WmStockDocumentLineMapper {
    long countByExample(WmStockDocumentLineExample example);

    int deleteByExample(WmStockDocumentLineExample example);

    int deleteByPrimaryKey(Long id);

    int insert(WmStockDocumentLineEntity row);

    int insertSelective(WmStockDocumentLineEntity row);

    List<WmStockDocumentLineEntity> selectByExample(WmStockDocumentLineExample example);

    WmStockDocumentLineEntity selectByPrimaryKeyForUpdate(Long id);

    WmStockDocumentLineEntity selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") WmStockDocumentLineEntity row,
                                 @Param("example") WmStockDocumentLineExample example);

    int updateByExample(@Param("row") WmStockDocumentLineEntity row,
                        @Param("example") WmStockDocumentLineExample example);

    int updateByPrimaryKeySelective(WmStockDocumentLineEntity row);

    int updateByPrimaryKey(WmStockDocumentLineEntity row);
}