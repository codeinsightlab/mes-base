// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.dv.dao;

import dev.mesbase.business.dv.domain.DvSubjectEntity;
import dev.mesbase.business.dv.domain.DvSubjectExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface DvSubjectMapper {
    long countByExample(DvSubjectExample example);

    int deleteByExample(DvSubjectExample example);

    int deleteByPrimaryKey(Long subjectId);

    int insert(DvSubjectEntity row);

    int insertSelective(DvSubjectEntity row);

    List<DvSubjectEntity> selectByExample(DvSubjectExample example);

    DvSubjectEntity selectByPrimaryKey(Long subjectId);

    int updateByExampleSelective(@Param("row") DvSubjectEntity row, @Param("example") DvSubjectExample example);

    int updateByExample(@Param("row") DvSubjectEntity row, @Param("example") DvSubjectExample example);

    int updateByPrimaryKeySelective(DvSubjectEntity row);

    int updateByPrimaryKey(DvSubjectEntity row);
}