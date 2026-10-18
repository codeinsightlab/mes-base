package dev.mesbase.business.quality.service;

import com.github.pagehelper.PageHelper;
import dev.mesbase.business.quality.dao.DefectStatisticsExtraMapper;
import dev.mesbase.business.quality.domain.DefectStatistics.Query;
import dev.mesbase.business.quality.domain.DefectStatistics.Row;
import dev.mesbase.business.quality.domain.DefectCostCategory;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Attribution;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
@Transactional(transactionManager = "businessTransactionManager", readOnly = true)
public class DefectStatisticsService {
    private final DefectStatisticsExtraMapper mapper;

    public DefectStatisticsService(DefectStatisticsExtraMapper mapper) {
        this.mapper = mapper;
    }

    public record Page(List<Row> items, long total, int offset, int limit) {
    }

    public Page summarize(Query q) {
        int offset = q.offset() == null ? 0 : q.offset(), limit = q.limit() == null ? 20 : q.limit();
        if (offset < 0 || limit < 1 || limit > 100 || q.costCategory() != null && !DefectCostCategory.UNKNOWN.name()
                .equals(q.costCategory()) && !DefectCostCategory.valid(q.costCategory())
                || q.from() != null && q.to() != null && q.from().isAfter(q.to())) bad();
        if (q.attribution() != null) {
            try {
                Attribution.valueOf(q.attribution());
            } catch (IllegalArgumentException e) {
                bad();
            }
        }
        try (var page = PageHelper.offsetPage(offset, limit)) {
            return new Page(List.copyOf(mapper.summarize(q)), page.getTotal(), offset, limit);
        }
    }

    private static void bad() {
        throw new FoundationException(ErrorCode.BAD_REQUEST);
    }
}
