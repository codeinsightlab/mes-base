// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.production.dao;

import dev.mesbase.business.production.domain.BatchRouteNodeEntity;
import dev.mesbase.business.production.domain.BatchRouteNodeExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface BatchRouteNodeMapper {
    long countByExample(BatchRouteNodeExample example);

    int deleteByExample(BatchRouteNodeExample example);

    int deleteByPrimaryKey(Long id);

    int insert(BatchRouteNodeEntity row);

    int insertSelective(BatchRouteNodeEntity row);

    List<BatchRouteNodeEntity> selectByExample(BatchRouteNodeExample example);

    BatchRouteNodeEntity selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") BatchRouteNodeEntity row,
                                 @Param("example") BatchRouteNodeExample example);

    int updateByExample(@Param("row") BatchRouteNodeEntity row, @Param("example") BatchRouteNodeExample example);

    int updateByPrimaryKeySelective(BatchRouteNodeEntity row);

    int updateByPrimaryKey(BatchRouteNodeEntity row);
}