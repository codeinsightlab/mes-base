package dev.mesbase.business.issue.service;

import com.github.pagehelper.Page;
import dev.mesbase.business.issue.domain.MesMaterialIssueSubmissionCreateCommand;
import dev.mesbase.business.issue.domain.MesMaterialIssueSubmissionEntity;
import dev.mesbase.business.issue.domain.MesMaterialIssueSubmissionQueryDto;
import dev.mesbase.business.issue.domain.MesMaterialIssueSubmissionUpdateCommand;
import java.util.List;

/**
 * 领料正式提交尝试及冻结载荷历史 CRUD 用例。
 *
 * @author MES Base
 */
public interface IMesMaterialIssueSubmissionService {
    Page<MesMaterialIssueSubmissionEntity> selectPage(MesMaterialIssueSubmissionQueryDto query);

    MesMaterialIssueSubmissionEntity selectById(Long id);

    MesMaterialIssueSubmissionEntity insert(MesMaterialIssueSubmissionCreateCommand input);

    MesMaterialIssueSubmissionEntity updateById(Long id, MesMaterialIssueSubmissionUpdateCommand input);

    void deleteByIds(List<Long> ids);
}

