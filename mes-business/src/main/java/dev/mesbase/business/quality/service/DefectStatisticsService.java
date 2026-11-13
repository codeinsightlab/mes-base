package dev.mesbase.business.quality.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import dev.mesbase.business.quality.domain.DefectStatistics.Source;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.CurrentFact;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.State;
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
import org.springframework.transaction.annotation.Isolation;

@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
@Transactional(transactionManager = "businessTransactionManager", readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class DefectStatisticsService {
    private final DefectStatisticsExtraMapper mapper;

    private final QualityTreatmentService treatments;

    public DefectStatisticsService(DefectStatisticsExtraMapper mapper, QualityTreatmentService treatments) {
        this.mapper = mapper;
        this.treatments = treatments;
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
        var items = new ArrayList<Row>();
        long total = 0, scanned = 0;
        Aggregate aggregate = null;
        while (true) {
            var sources = mapper.sources(q, scanned, 250);
            if (sources.isEmpty()) break;
            var facts = treatments.interpretFacts(sources.stream().map(Source::declaration).toList()).stream()
                    .collect(Collectors.toMap(CurrentFact::declarationId, Function.identity()));
            for (var source : sources) {
                var summary = facts.get(source.declarationId()).summary(false);
                if (summary.state() == State.VOIDED) continue;
                if (summary.state() != State.UNRESOLVED && q.attribution() != null && (summary.attribution() == null
                        || !q.attribution().equals(summary.attribution().getValue()))) continue;
                if (aggregate == null || !aggregate.matches(source)) {
                    if (aggregate != null) {
                        if (total >= offset && items.size() < limit) items.add(aggregate.row());
                        total++;
                    }
                    aggregate = new Aggregate(source);
                }
                aggregate.add(source, facts.get(source.declarationId()));
            }
            scanned += sources.size();
            if (sources.size() < 250) break;
        }
        if (aggregate != null) {
            if (total >= offset && items.size() < limit) items.add(aggregate.row());
            total++;
        }
        return new Page(List.copyOf(items), total, offset, limit);
    }

    /**
     * SQL order keeps each group and each snapshot adjacent, so memory does not grow with group count.
     */
    private static final class Aggregate {
        private final Source first;
        private String name, previousName;
        private long variants, invalid;
        private BigDecimal declared = BigDecimal.ZERO, independent = BigDecimal.ZERO, overlap = BigDecimal.ZERO,
                unverified = BigDecimal.ZERO, returned = BigDecimal.ZERO, net = BigDecimal.ZERO;
        private boolean invalidDeclared;

        private Aggregate(Source first) {
            this.first = first;
        }

        private boolean matches(Source source) {
            return Objects.equals(first.operationId(), source.operationId())
                    && Objects.equals(first.defectTypeId(), source.defectTypeId())
                    && Objects.equals(first.wasteCategory(), source.wasteCategory());
        }

        private void add(Source source, CurrentFact fact) {
            if (source.typeName() != null && !Objects.equals(previousName, source.typeName())) {
                variants++;
                previousName = source.typeName();
                if (name == null) name = source.typeName();
            }
            if (fact.defectQty() == null) invalidDeclared = true;
            else declared = declared.add(fact.defectQty());
            var summary = fact.summary(false);
            if (summary.state() == State.UNRESOLVED) {
                invalid++;
                return;
            }
            switch (summary.attribution()) {
                case INDEPENDENT -> {
                    independent = independent.add(fact.defectQty());
                    returned = returned.add(summary.returnedQty());
                    net = net.add(summary.netDefectQty());
                }
                case OVERLAP -> overlap = overlap.add(fact.defectQty());
                case UNVERIFIED -> unverified = unverified.add(fact.defectQty());
            }
        }

        private Row row() {
            BigDecimal declaredValue = declared;
            if (invalidDeclared) declaredValue = null;
            return new Row(first.operationId(), first.defectTypeId(), name, variants, first.wasteCategory(),
                    declaredValue, independent, overlap, unverified, returned, net, invalid);
        }
    }
}
