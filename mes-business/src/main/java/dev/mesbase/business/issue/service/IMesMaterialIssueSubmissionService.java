// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.issue.service;

import dev.mesbase.business.issue.domain.MesMaterialIssueSubmissionCreateCommand;
import dev.mesbase.business.issue.domain.MesMaterialIssueSubmissionEntity;
import dev.mesbase.business.issue.domain.MesMaterialIssueSubmissionPage;
import dev.mesbase.business.issue.domain.MesMaterialIssueSubmissionQueryDto;
import dev.mesbase.business.issue.domain.MesMaterialIssueSubmissionUpdateCommand;
import java.util.List;

/**
 * 领料正式提交尝试及冻结载荷历史 CRUD 用例。
 *
 * @author MES Base
 */
public interface IMesMaterialIssueSubmissionService {
    MesMaterialIssueSubmissionPage selectPage(MesMaterialIssueSubmissionQueryDto query);

    MesMaterialIssueSubmissionEntity selectById(Long id);

    MesMaterialIssueSubmissionEntity insert(MesMaterialIssueSubmissionCreateCommand input);

    MesMaterialIssueSubmissionEntity updateById(Long id, MesMaterialIssueSubmissionUpdateCommand input);

    void deleteByIds(List<Long> ids);
}

