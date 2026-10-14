// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.production.dao;

import dev.mesbase.business.production.domain.MesExecutionPathEntity;
import dev.mesbase.business.production.domain.MesExecutionPathExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface MesExecutionPathMapper {
    long countByExample(MesExecutionPathExample example);

    int deleteByExample(MesExecutionPathExample example);

    int deleteByPrimaryKey(Long id);

    int insert(MesExecutionPathEntity row);

    int insertSelective(MesExecutionPathEntity row);

    List<MesExecutionPathEntity> selectByExample(MesExecutionPathExample example);

    MesExecutionPathEntity selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") MesExecutionPathEntity row, @Param("example") MesExecutionPathExample example);

    int updateByExample(@Param("row") MesExecutionPathEntity row, @Param("example") MesExecutionPathExample example);

    int updateByPrimaryKeySelective(MesExecutionPathEntity row);

    int updateByPrimaryKey(MesExecutionPathEntity row);
}