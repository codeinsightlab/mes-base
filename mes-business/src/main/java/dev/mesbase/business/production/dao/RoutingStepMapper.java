// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.production.dao;

import dev.mesbase.business.production.domain.RoutingStepEntity;
import dev.mesbase.business.production.domain.RoutingStepExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface RoutingStepMapper {
    long countByExample(RoutingStepExample example);

    int deleteByExample(RoutingStepExample example);

    int deleteByPrimaryKey(Long id);

    int insert(RoutingStepEntity row);

    int insertSelective(RoutingStepEntity row);

    List<RoutingStepEntity> selectByExample(RoutingStepExample example);

    RoutingStepEntity selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") RoutingStepEntity row, @Param("example") RoutingStepExample example);

    int updateByExample(@Param("row") RoutingStepEntity row, @Param("example") RoutingStepExample example);

    int updateByPrimaryKeySelective(RoutingStepEntity row);

    int updateByPrimaryKey(RoutingStepEntity row);
}