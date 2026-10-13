// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.issue.service;

import dev.mesbase.business.issue.domain.MesMaterialIssueItemCreateCommand;
import dev.mesbase.business.issue.domain.MesMaterialIssueItemEntity;
import dev.mesbase.business.issue.domain.MesMaterialIssueItemPage;
import dev.mesbase.business.issue.domain.MesMaterialIssueItemQueryDto;
import dev.mesbase.business.issue.domain.MesMaterialIssueItemUpdateCommand;
import java.util.List;

/**
 * 领料出库明细 CRUD 用例。
 *
 * @author MES Base
 */
public interface IMesMaterialIssueItemService {
    MesMaterialIssueItemPage selectPage(MesMaterialIssueItemQueryDto query);

    MesMaterialIssueItemEntity selectById(Long id);

    MesMaterialIssueItemEntity insert(MesMaterialIssueItemCreateCommand input);

    MesMaterialIssueItemEntity updateById(Long id, MesMaterialIssueItemUpdateCommand input);

    void deleteByIds(List<Long> ids);
}

