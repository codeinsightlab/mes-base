// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.production.dao;

import dev.mesbase.business.production.domain.WorkorderErpExtraEntity;
import dev.mesbase.business.production.domain.WorkorderErpExtraExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface WorkorderErpExtraMapper {
    long countByExample(WorkorderErpExtraExample example);

    int deleteByExample(WorkorderErpExtraExample example);

    int deleteByPrimaryKey(Long id);

    int insert(WorkorderErpExtraEntity row);

    int insertSelective(WorkorderErpExtraEntity row);

    List<WorkorderErpExtraEntity> selectByExample(WorkorderErpExtraExample example);

    WorkorderErpExtraEntity selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") WorkorderErpExtraEntity row,
                                 @Param("example") WorkorderErpExtraExample example);

    int updateByExample(@Param("row") WorkorderErpExtraEntity row, @Param("example") WorkorderErpExtraExample example);

    int updateByPrimaryKeySelective(WorkorderErpExtraEntity row);

    int updateByPrimaryKey(WorkorderErpExtraEntity row);
}