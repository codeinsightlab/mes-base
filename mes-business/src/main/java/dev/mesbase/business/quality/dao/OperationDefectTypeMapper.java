// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.quality.dao;

import dev.mesbase.business.quality.domain.OperationDefectTypeEntity;
import dev.mesbase.business.quality.domain.OperationDefectTypeExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface OperationDefectTypeMapper {
    long countByExample(OperationDefectTypeExample example);

    int deleteByExample(OperationDefectTypeExample example);

    int deleteByPrimaryKey(Long id);

    int insert(OperationDefectTypeEntity row);

    int insertSelective(OperationDefectTypeEntity row);

    List<OperationDefectTypeEntity> selectByExample(OperationDefectTypeExample example);

    OperationDefectTypeEntity selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") OperationDefectTypeEntity row,
                                 @Param("example") OperationDefectTypeExample example);

    int updateByExample(@Param("row") OperationDefectTypeEntity row,
                        @Param("example") OperationDefectTypeExample example);

    int updateByPrimaryKeySelective(OperationDefectTypeEntity row);

    int updateByPrimaryKey(OperationDefectTypeEntity row);
}