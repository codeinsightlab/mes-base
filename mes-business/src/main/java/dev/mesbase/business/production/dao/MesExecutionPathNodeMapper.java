// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.production.dao;

import dev.mesbase.business.production.domain.MesExecutionPathNodeEntity;
import dev.mesbase.business.production.domain.MesExecutionPathNodeExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface MesExecutionPathNodeMapper {
    long countByExample(MesExecutionPathNodeExample example);

    int deleteByExample(MesExecutionPathNodeExample example);

    int deleteByPrimaryKey(Long id);

    int insert(MesExecutionPathNodeEntity row);

    int insertSelective(MesExecutionPathNodeEntity row);

    List<MesExecutionPathNodeEntity> selectByExample(MesExecutionPathNodeExample example);

    MesExecutionPathNodeEntity selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") MesExecutionPathNodeEntity row, @Param("example") MesExecutionPathNodeExample example);

    int updateByExample(@Param("row") MesExecutionPathNodeEntity row, @Param("example") MesExecutionPathNodeExample example);

    int updateByPrimaryKeySelective(MesExecutionPathNodeEntity row);

    int updateByPrimaryKey(MesExecutionPathNodeEntity row);
}