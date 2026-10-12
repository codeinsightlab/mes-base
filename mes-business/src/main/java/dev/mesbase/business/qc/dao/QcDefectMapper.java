// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.qc.dao;

import dev.mesbase.business.qc.domain.QcDefectEntity;
import dev.mesbase.business.qc.domain.QcDefectExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface QcDefectMapper {
    long countByExample(QcDefectExample example);

    int deleteByExample(QcDefectExample example);

    int deleteByPrimaryKey(Long defectId);

    int insert(QcDefectEntity row);

    int insertSelective(QcDefectEntity row);

    List<QcDefectEntity> selectByExample(QcDefectExample example);

    QcDefectEntity selectByPrimaryKey(Long defectId);

    int updateByExampleSelective(@Param("row") QcDefectEntity row, @Param("example") QcDefectExample example);

    int updateByExample(@Param("row") QcDefectEntity row, @Param("example") QcDefectExample example);

    int updateByPrimaryKeySelective(QcDefectEntity row);

    int updateByPrimaryKey(QcDefectEntity row);
}