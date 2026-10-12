// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.modules.qc.dao;

import dev.mesbase.modules.qc.domain.QcIndexEntity;
import dev.mesbase.modules.qc.domain.QcIndexExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface QcIndexMapper {
    long countByExample(QcIndexExample example);

    int deleteByExample(QcIndexExample example);

    int deleteByPrimaryKey(Long indexId);

    int insert(QcIndexEntity row);

    int insertSelective(QcIndexEntity row);

    List<QcIndexEntity> selectByExample(QcIndexExample example);

    QcIndexEntity selectByPrimaryKey(Long indexId);

    int updateByExampleSelective(@Param("row") QcIndexEntity row, @Param("example") QcIndexExample example);

    int updateByExample(@Param("row") QcIndexEntity row, @Param("example") QcIndexExample example);

    int updateByPrimaryKeySelective(QcIndexEntity row);

    int updateByPrimaryKey(QcIndexEntity row);
}