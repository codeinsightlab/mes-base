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
import dev.mesbase.core.error.SafeBusinessMessage;
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
        if (q == null)
            ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("统计筛选条件不能为空"));
        int offset = q.offset() == null ? 0 : q.offset(), limit = q.limit() == null ? 20 : q.limit();
        ParameterChecks.paging(offset, limit, 100);
        if (q.wasteCategory() != null && WasteCategory.from(q.wasteCategory()) == null)
            ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("工废/料废类别筛选值无效"));
        if (q.from() != null && q.to() != null && q.from().isAfter(q.to()))
            ParameterChecks.reject(ErrorCode.BAD_REQUEST, new SafeBusinessMessage("开始时间不能晚于结束时间"));
        if (q.attribution() != null) {
            try {
                Attribution.valueOf(q.attribution());
            } catch (IllegalArgumentException e) {
                ParameterChecks.reject(ErrorCode.BAD_REQUEST,
                        new SafeBusinessMessage("申报核实状态筛选值无效"));
            }
        }
        try (var page = PageHelper.offsetPage(offset, limit)) {
            var rows = List.copyOf(mapper.summarize(q));
            return new Page(rows, page.getTotal(), offset, limit);
        }
    }

}
