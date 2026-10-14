package dev.mesbase.business.issue.dao;

import dev.mesbase.business.issue.domain.MesMaterialIssueErpExtraEntity;
import dev.mesbase.business.issue.domain.MesMaterialIssueErpExtraExample;
import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface MesMaterialIssueErpExtraMapper {
    long countByExample(MesMaterialIssueErpExtraExample example);

    int deleteByExample(MesMaterialIssueErpExtraExample example);

    int deleteByPrimaryKey(Long id);

    int insert(MesMaterialIssueErpExtraEntity row);

    int insertSelective(MesMaterialIssueErpExtraEntity row);

    List<MesMaterialIssueErpExtraEntity> selectByExample(MesMaterialIssueErpExtraExample example);

    MesMaterialIssueErpExtraEntity selectByPrimaryKey(Long id);

    int updateByExampleSelective(@Param("row") MesMaterialIssueErpExtraEntity row,
                                 @Param("example") MesMaterialIssueErpExtraExample example);

    int updateByExample(@Param("row") MesMaterialIssueErpExtraEntity row,
                        @Param("example") MesMaterialIssueErpExtraExample example);

    int updateByPrimaryKeySelective(MesMaterialIssueErpExtraEntity row);

    int updateByPrimaryKey(MesMaterialIssueErpExtraEntity row);
}