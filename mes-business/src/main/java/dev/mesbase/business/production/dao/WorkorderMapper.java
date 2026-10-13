// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.production.dao;

import dev.mesbase.business.production.domain.WorkorderEntity;
import dev.mesbase.business.production.domain.WorkorderExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface WorkorderMapper {
    long countByExample(WorkorderExample example);

    int deleteByExample(WorkorderExample example);

    int deleteByPrimaryKey(Long id);

    int insert(WorkorderEntity row);

    int insertSelective(WorkorderEntity row);

    List<WorkorderEntity> selectByExample(WorkorderExample example);

    WorkorderEntity selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") WorkorderEntity row, @Param("example") WorkorderExample example);

    int updateByExample(@Param("row") WorkorderEntity row, @Param("example") WorkorderExample example);

    int updateByPrimaryKeySelective(WorkorderEntity row);

    int updateByPrimaryKey(WorkorderEntity row);
}