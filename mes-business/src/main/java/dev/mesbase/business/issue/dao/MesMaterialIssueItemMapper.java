// MES-CODEGEN: MyBatis Generator standard single-table persistence.
package dev.mesbase.business.issue.dao;

import dev.mesbase.business.issue.domain.MesMaterialIssueItemEntity;
import dev.mesbase.business.issue.domain.MesMaterialIssueItemExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface MesMaterialIssueItemMapper {
    long countByExample(MesMaterialIssueItemExample example);

    int deleteByExample(MesMaterialIssueItemExample example);

    int deleteByPrimaryKey(Long id);

    int insert(MesMaterialIssueItemEntity row);

    int insertSelective(MesMaterialIssueItemEntity row);

    List<MesMaterialIssueItemEntity> selectByExample(MesMaterialIssueItemExample example);

    MesMaterialIssueItemEntity selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") MesMaterialIssueItemEntity row,
                                 @Param("example") MesMaterialIssueItemExample example);

    int updateByExample(@Param("row") MesMaterialIssueItemEntity row,
                        @Param("example") MesMaterialIssueItemExample example);

    int updateByPrimaryKeySelective(MesMaterialIssueItemEntity row);

    int updateByPrimaryKey(MesMaterialIssueItemEntity row);
}