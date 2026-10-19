package dev.mesbase.business.basic.dao;

import dev.mesbase.business.basic.domain.WorkstationEntity;
import dev.mesbase.business.basic.domain.WorkstationExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface WorkstationMapper {
    long countByExample(WorkstationExample example);

    int deleteByExample(WorkstationExample example);

    int deleteByPrimaryKey(Long id);

    int insert(WorkstationEntity row);

    int insertSelective(WorkstationEntity row);

    List<WorkstationEntity> selectByExample(WorkstationExample example);

    WorkstationEntity selectByPrimaryKey(Long id);

    WorkstationEntity selectByPrimaryKeyForUpdate(Long id);

    int updateByExampleSelective(@Param("row") WorkstationEntity row, @Param("example") WorkstationExample example);

    int updateByExample(@Param("row") WorkstationEntity row, @Param("example") WorkstationExample example);

    int updateByPrimaryKeySelective(WorkstationEntity row);

    int updateByPrimaryKey(WorkstationEntity row);
}