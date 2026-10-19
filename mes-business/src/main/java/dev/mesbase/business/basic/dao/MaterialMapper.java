package dev.mesbase.business.basic.dao;

import dev.mesbase.business.basic.domain.MaterialEntity;
import dev.mesbase.business.basic.domain.MaterialExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface MaterialMapper {
    long countByExample(MaterialExample example);

    int deleteByExample(MaterialExample example);

    int deleteByPrimaryKey(Long id);

    int insert(MaterialEntity row);

    int insertSelective(MaterialEntity row);

    List<MaterialEntity> selectByExample(MaterialExample example);

    MaterialEntity selectByPrimaryKey(Long id);

    MaterialEntity selectByPrimaryKeyForUpdate(Long id);

    int updateByExampleSelective(@Param("row") MaterialEntity row, @Param("example") MaterialExample example);

    int updateByExample(@Param("row") MaterialEntity row, @Param("example") MaterialExample example);

    int updateByPrimaryKeySelective(MaterialEntity row);

    int updateByPrimaryKey(MaterialEntity row);
}