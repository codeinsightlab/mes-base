// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.production.dao;

import dev.mesbase.business.production.domain.RoutingProductEntity;
import dev.mesbase.business.production.domain.RoutingProductExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface RoutingProductMapper {
    long countByExample(RoutingProductExample example);

    int deleteByExample(RoutingProductExample example);

    int deleteByPrimaryKey(Long id);

    int insert(RoutingProductEntity row);

    int insertSelective(RoutingProductEntity row);

    List<RoutingProductEntity> selectByExample(RoutingProductExample example);

    RoutingProductEntity selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") RoutingProductEntity row,
                                 @Param("example") RoutingProductExample example);

    int updateByExample(@Param("row") RoutingProductEntity row, @Param("example") RoutingProductExample example);

    int updateByPrimaryKeySelective(RoutingProductEntity row);

    int updateByPrimaryKey(RoutingProductEntity row);
}