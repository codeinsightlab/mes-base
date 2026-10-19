package dev.mesbase.business.production.dao;

import dev.mesbase.business.production.domain.OperationEntity;
import dev.mesbase.business.production.domain.OperationExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface OperationMapper {
    long countByExample(OperationExample example);

    int deleteByExample(OperationExample example);

    int deleteByPrimaryKey(Long id);

    int insert(OperationEntity row);

    int insertSelective(OperationEntity row);

    List<OperationEntity> selectByExample(OperationExample example);

    OperationEntity selectByPrimaryKey(Long id);

    OperationEntity selectByPrimaryKeyForUpdate(Long id);

    int updateByExampleSelective(@Param("row") OperationEntity row, @Param("example") OperationExample example);

    int updateByExample(@Param("row") OperationEntity row, @Param("example") OperationExample example);

    int updateByPrimaryKeySelective(OperationEntity row);

    int updateByPrimaryKey(OperationEntity row);
}