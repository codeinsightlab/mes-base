// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.production.dao;

import dev.mesbase.business.production.domain.RoutingEdgeEntity;
import dev.mesbase.business.production.domain.RoutingEdgeExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface RoutingEdgeMapper {
    long countByExample(RoutingEdgeExample example);

    int deleteByExample(RoutingEdgeExample example);

    int deleteByPrimaryKey(Long id);

    int insert(RoutingEdgeEntity row);

    int insertSelective(RoutingEdgeEntity row);

    List<RoutingEdgeEntity> selectByExample(RoutingEdgeExample example);

    RoutingEdgeEntity selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") RoutingEdgeEntity row, @Param("example") RoutingEdgeExample example);

    int updateByExample(@Param("row") RoutingEdgeEntity row, @Param("example") RoutingEdgeExample example);

    int updateByPrimaryKeySelective(RoutingEdgeEntity row);

    int updateByPrimaryKey(RoutingEdgeEntity row);
}