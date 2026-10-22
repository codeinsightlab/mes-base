package dev.mesbase.business.quality.service;

import com.github.pagehelper.PageHelper;
import dev.mesbase.business.quality.dao.DefectStatisticsExtraMapper;
import dev.mesbase.business.quality.domain.DefectStatistics.Query;
import dev.mesbase.business.quality.domain.DefectStatistics.Row;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Attribution;
import dev.mesbase.business.quality.domain.WasteCategory;
import dev.mesbase.common.core.page.PageData;
import dev.mesbase.common.validation.ParameterChecks;
import dev.mesbase.core.error.ErrorCode;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
@Transactional(transactionManager = "businessTransactionManager", readOnly = true)
public class DefectStatisticsService {
    private final DefectStatisticsExtraMapper mapper;

    public DefectStatisticsService(DefectStatisticsExtraMapper mapper) {
        this.mapper = mapper;
    }


    public record Page(List<Row> items, long total, int offset, int limit) implements PageData<Row> {
    }

    public Page summarize(Query q) {
        int offset = q.offset() == null ? 0 : q.offset(), limit = q.limit() == null ? 20 : q.limit();
        if (offset < 0 || limit < 1 || limit > 100 || q.wasteCategory() != null && WasteCategory.from(q.wasteCategory()) == null
                || q.from() != null && q.to() != null && q.from().isAfter(q.to()))
            ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        if (q.attribution() != null) {
            try {
                Attribution.valueOf(q.attribution());
            } catch (IllegalArgumentException e) {
                ParameterChecks.reject(ErrorCode.BAD_REQUEST);
            }
        }
        try (var page = PageHelper.offsetPage(offset, limit)) {
            var rows = List.copyOf(mapper.summarize(q));
            return new Page(rows, page.getTotal(), offset, limit);
        }
    }

}
