package dev.mesbase.business.issue.service;

import dev.mesbase.business.issue.domain.MesMaterialIssueItemCreateCommand;
import dev.mesbase.business.issue.domain.MesMaterialIssueItemEntity;
import dev.mesbase.business.issue.domain.MesMaterialIssueItemPage;
import dev.mesbase.business.issue.domain.MesMaterialIssueItemQueryDto;
import dev.mesbase.business.issue.domain.MesMaterialIssueItemUpdateCommand;
import java.util.List;
import dev.mesbase.business.integration.production.ProductionSupply.Calculation;

/**
 * 领料出库明细 CRUD 用例。
 *
 * @author MES Base
 */
public interface IMesMaterialIssueItemService {
    MesMaterialIssueItemPage selectPage(MesMaterialIssueItemQueryDto query);

    MesMaterialIssueItemEntity selectById(Long id);

    MesMaterialIssueItemEntity insert(MesMaterialIssueItemCreateCommand input);

    MesMaterialIssueItemEntity insertCalculated(Long issueId, Calculation calculation);

    MesMaterialIssueItemEntity updateById(Long id, MesMaterialIssueItemUpdateCommand input);

    void deleteByIds(List<Long> ids);
}

