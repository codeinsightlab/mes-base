package dev.mesbase.business.quality.service;

import com.github.pagehelper.PageHelper;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.quality.dao.MesQualityReleaseMapper;
import dev.mesbase.business.quality.domain.MesQualityReleaseEntity;
import dev.mesbase.business.quality.domain.MesQualityReleaseExample;
import dev.mesbase.common.validation.ParameterChecks;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only audit of retired release facts; never participates in production or stock eligibility.
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class QualityReleaseService {
    public record History(List<MesQualityReleaseEntity> items, long total, int offset, int limit) {
    }

    private final MesQualityReleaseMapper releases;
    private final MesProductionExecutionMapper executions;

    public QualityReleaseService(MesQualityReleaseMapper releases, MesProductionExecutionMapper executions) {
        this.releases = releases;
        this.executions = executions;
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public History history(Long execution, int offset, int limit) {
        ParameterChecks.paging(offset, limit);
        if (executions.selectByPrimaryKey(execution) == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        var filter = new MesQualityReleaseExample();
        filter.createCriteria().andExecutionIdEqualTo(execution);
        filter.setOrderByClause("sequence_no DESC");
        try (var page = PageHelper.offsetPage(offset, limit)) {
            var rows = List.copyOf(releases.selectByExample(filter));
            return new History(rows, page.getTotal(), offset, limit);
        }
    }
}
