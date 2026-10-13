// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.production.dao;

import dev.mesbase.business.production.domain.RoutingNodeEntity;
import dev.mesbase.business.production.domain.RoutingNodeExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface RoutingNodeMapper {
    long countByExample(RoutingNodeExample example);

    int deleteByExample(RoutingNodeExample example);

    int deleteByPrimaryKey(Long id);

    int insert(RoutingNodeEntity row);

    int insertSelective(RoutingNodeEntity row);

    List<RoutingNodeEntity> selectByExample(RoutingNodeExample example);

    RoutingNodeEntity selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") RoutingNodeEntity row, @Param("example") RoutingNodeExample example);

    int updateByExample(@Param("row") RoutingNodeEntity row, @Param("example") RoutingNodeExample example);

    int updateByPrimaryKeySelective(RoutingNodeEntity row);

    int updateByPrimaryKey(RoutingNodeEntity row);
}