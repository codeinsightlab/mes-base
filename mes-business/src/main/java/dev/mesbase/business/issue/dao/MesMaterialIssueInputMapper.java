// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.issue.dao;

import dev.mesbase.business.issue.domain.MesMaterialIssueInputEntity;
import dev.mesbase.business.issue.domain.MesMaterialIssueInputExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface MesMaterialIssueInputMapper {
    long countByExample(MesMaterialIssueInputExample example);

    int deleteByExample(MesMaterialIssueInputExample example);

    int deleteByPrimaryKey(String id);

    int insert(MesMaterialIssueInputEntity row);

    int insertSelective(MesMaterialIssueInputEntity row);

    List<MesMaterialIssueInputEntity> selectByExampleWithBLOBs(MesMaterialIssueInputExample example);

    List<MesMaterialIssueInputEntity> selectByExample(MesMaterialIssueInputExample example);

    MesMaterialIssueInputEntity selectByPrimaryKey(String id);

    int updateByExampleSelective(@Param("row") MesMaterialIssueInputEntity row, @Param("example") MesMaterialIssueInputExample example);

    int updateByExampleWithBLOBs(@Param("row") MesMaterialIssueInputEntity row, @Param("example") MesMaterialIssueInputExample example);

    int updateByExample(@Param("row") MesMaterialIssueInputEntity row, @Param("example") MesMaterialIssueInputExample example);

    int updateByPrimaryKeySelective(MesMaterialIssueInputEntity row);

    int updateByPrimaryKeyWithBLOBs(MesMaterialIssueInputEntity row);

    int updateByPrimaryKey(MesMaterialIssueInputEntity row);
}