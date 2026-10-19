package dev.mesbase.business.issue.dao;

import dev.mesbase.business.issue.domain.MesMaterialIssueEntity;
import dev.mesbase.business.issue.domain.MesMaterialIssueExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface MesMaterialIssueMapper {
    long countByExample(MesMaterialIssueExample example);

    int deleteByExample(MesMaterialIssueExample example);

    int deleteByPrimaryKey(Long id);

    int insert(MesMaterialIssueEntity row);

    int insertSelective(MesMaterialIssueEntity row);

    List<MesMaterialIssueEntity> selectByExample(MesMaterialIssueExample example);

    MesMaterialIssueEntity selectByPrimaryKey(Long id);

    MesMaterialIssueEntity selectByPrimaryKeyForUpdate(Long id);

    int updateByExampleSelective(@Param("row") MesMaterialIssueEntity row,
                                 @Param("example") MesMaterialIssueExample example);

    int updateByExample(@Param("row") MesMaterialIssueEntity row, @Param("example") MesMaterialIssueExample example);

    int updateByPrimaryKeySelective(MesMaterialIssueEntity row);

    int updateByPrimaryKey(MesMaterialIssueEntity row);
}