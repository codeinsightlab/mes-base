package dev.mesbase.business.issue.service;

import com.github.pagehelper.Page;
import dev.mesbase.business.issue.domain.MesMaterialIssueCreateCommand;
import dev.mesbase.business.issue.domain.MesMaterialIssueEntity;
import dev.mesbase.business.issue.domain.MesMaterialIssueQueryDto;
import dev.mesbase.business.issue.domain.MesMaterialIssueUpdateCommand;
import java.util.List;

/**
 * 领料出库单 CRUD 用例。
 *
 * @author MES Base
 */
public interface IMesMaterialIssueService {
    Page<MesMaterialIssueEntity> selectPage(MesMaterialIssueQueryDto query);

    MesMaterialIssueEntity selectById(Long id);

    MesMaterialIssueEntity insert(MesMaterialIssueCreateCommand input);

    MesMaterialIssueEntity updateById(Long id, MesMaterialIssueUpdateCommand input);

    void deleteByIds(List<Long> ids);
}

