// MES-CODEGEN: scaffold; review required.
package dev.mesbase.business.issue.service;

import dev.mesbase.business.issue.domain.MesMaterialIssueErpExtraCreateCommand;
import dev.mesbase.business.issue.domain.MesMaterialIssueErpExtraEntity;
import dev.mesbase.business.issue.domain.MesMaterialIssueErpExtraPage;
import dev.mesbase.business.issue.domain.MesMaterialIssueErpExtraQueryDto;
import dev.mesbase.business.issue.domain.MesMaterialIssueErpExtraUpdateCommand;
import java.util.List;

/**
 * 领料ERP外部事实 CRUD 用例。
 *
 * @author MES Base
 */
public interface IMesMaterialIssueErpExtraService {
    MesMaterialIssueErpExtraPage selectPage(MesMaterialIssueErpExtraQueryDto query);

    MesMaterialIssueErpExtraEntity selectById(Long id);

    MesMaterialIssueErpExtraEntity insert(MesMaterialIssueErpExtraCreateCommand input);

    MesMaterialIssueErpExtraEntity updateById(Long id, MesMaterialIssueErpExtraUpdateCommand input);

    void deleteByIds(List<Long> ids);
}

