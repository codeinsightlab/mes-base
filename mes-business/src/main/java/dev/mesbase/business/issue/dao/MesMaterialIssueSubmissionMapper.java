// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.issue.dao;

import dev.mesbase.business.issue.domain.MesMaterialIssueSubmissionEntity;
import dev.mesbase.business.issue.domain.MesMaterialIssueSubmissionExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface MesMaterialIssueSubmissionMapper {
    long countByExample(MesMaterialIssueSubmissionExample example);

    int deleteByExample(MesMaterialIssueSubmissionExample example);

    int deleteByPrimaryKey(Long id);

    int insert(MesMaterialIssueSubmissionEntity row);

    int insertSelective(MesMaterialIssueSubmissionEntity row);

    List<MesMaterialIssueSubmissionEntity> selectByExampleWithBLOBs(MesMaterialIssueSubmissionExample example);

    List<MesMaterialIssueSubmissionEntity> selectByExample(MesMaterialIssueSubmissionExample example);

    MesMaterialIssueSubmissionEntity selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") MesMaterialIssueSubmissionEntity row, @Param("example") MesMaterialIssueSubmissionExample example);

    int updateByExampleWithBLOBs(@Param("row") MesMaterialIssueSubmissionEntity row, @Param("example") MesMaterialIssueSubmissionExample example);

    int updateByExample(@Param("row") MesMaterialIssueSubmissionEntity row, @Param("example") MesMaterialIssueSubmissionExample example);

    int updateByPrimaryKeySelective(MesMaterialIssueSubmissionEntity row);

    int updateByPrimaryKeyWithBLOBs(MesMaterialIssueSubmissionEntity row);

    int updateByPrimaryKey(MesMaterialIssueSubmissionEntity row);
}