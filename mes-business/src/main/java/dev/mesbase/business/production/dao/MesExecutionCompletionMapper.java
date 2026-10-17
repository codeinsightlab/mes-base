// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.production.dao;

import dev.mesbase.business.production.domain.MesExecutionCompletionEntity;
import dev.mesbase.business.production.domain.MesExecutionCompletionExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface MesExecutionCompletionMapper {
    long countByExample(MesExecutionCompletionExample example);

    int deleteByExample(MesExecutionCompletionExample example);

    int deleteByPrimaryKey(String id);

    int insert(MesExecutionCompletionEntity row);

    int insertSelective(MesExecutionCompletionEntity row);

    List<MesExecutionCompletionEntity> selectByExample(MesExecutionCompletionExample example);

    MesExecutionCompletionEntity selectByPrimaryKey(String id);

    int updateByExampleSelective(@Param("row") MesExecutionCompletionEntity row,
                                 @Param("example") MesExecutionCompletionExample example);

    int updateByExample(@Param("row") MesExecutionCompletionEntity row,
                        @Param("example") MesExecutionCompletionExample example);

    int updateByPrimaryKeySelective(MesExecutionCompletionEntity row);

    int updateByPrimaryKey(MesExecutionCompletionEntity row);
}