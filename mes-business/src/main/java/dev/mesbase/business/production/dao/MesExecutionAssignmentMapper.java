// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.production.dao;

import dev.mesbase.business.production.domain.MesExecutionAssignmentEntity;
import dev.mesbase.business.production.domain.MesExecutionAssignmentExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface MesExecutionAssignmentMapper {
    long countByExample(MesExecutionAssignmentExample example);

    int deleteByExample(MesExecutionAssignmentExample example);

    int deleteByPrimaryKey(Long id);

    int insert(MesExecutionAssignmentEntity row);

    int insertSelective(MesExecutionAssignmentEntity row);

    List<MesExecutionAssignmentEntity> selectByExample(MesExecutionAssignmentExample example);

    MesExecutionAssignmentEntity selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") MesExecutionAssignmentEntity row,
                                 @Param("example") MesExecutionAssignmentExample example);

    int updateByExample(@Param("row") MesExecutionAssignmentEntity row,
                        @Param("example") MesExecutionAssignmentExample example);

    int updateByPrimaryKeySelective(MesExecutionAssignmentEntity row);

    int updateByPrimaryKey(MesExecutionAssignmentEntity row);
}