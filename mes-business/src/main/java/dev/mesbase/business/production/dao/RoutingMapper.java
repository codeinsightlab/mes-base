package dev.mesbase.business.production.dao;

import dev.mesbase.business.production.domain.RoutingEntity;
import dev.mesbase.business.production.domain.RoutingExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface RoutingMapper {
    long countByExample(RoutingExample example);

    int deleteByExample(RoutingExample example);

    int deleteByPrimaryKey(Long id);

    int insert(RoutingEntity row);

    int insertSelective(RoutingEntity row);

    List<RoutingEntity> selectByExample(RoutingExample example);

    RoutingEntity selectByPrimaryKey(Long id);

    RoutingEntity selectByPrimaryKeyForUpdate(Long id);

    int updateByExampleSelective(@Param("row") RoutingEntity row, @Param("example") RoutingExample example);

    int updateByExample(@Param("row") RoutingEntity row, @Param("example") RoutingExample example);

    int updateByPrimaryKeySelective(RoutingEntity row);

    int updateByPrimaryKey(RoutingEntity row);
}