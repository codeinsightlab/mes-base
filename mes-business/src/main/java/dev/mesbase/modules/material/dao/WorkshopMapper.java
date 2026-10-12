// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.modules.material.dao;

import dev.mesbase.modules.material.domain.WorkshopEntity;
import dev.mesbase.modules.material.domain.WorkshopExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface WorkshopMapper {
    long countByExample(WorkshopExample example);

    int deleteByExample(WorkshopExample example);

    int deleteByPrimaryKey(Long id);

    int insert(WorkshopEntity row);

    int insertSelective(WorkshopEntity row);

    List<WorkshopEntity> selectByExample(WorkshopExample example);

    WorkshopEntity selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") WorkshopEntity row, @Param("example") WorkshopExample example);

    int updateByExample(@Param("row") WorkshopEntity row, @Param("example") WorkshopExample example);

    int updateByPrimaryKeySelective(WorkshopEntity row);

    int updateByPrimaryKey(WorkshopEntity row);
}