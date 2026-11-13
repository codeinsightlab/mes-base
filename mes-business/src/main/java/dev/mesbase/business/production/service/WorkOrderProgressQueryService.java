package dev.mesbase.business.production.service;

import com.github.pagehelper.PageHelper;
import dev.mesbase.business.production.dao.WorkOrderProgressExtraMapper;
import dev.mesbase.business.production.domain.WorkOrderProgress.Completion;
import dev.mesbase.business.production.domain.WorkOrderProgress.Filter;
import dev.mesbase.business.production.domain.WorkOrderProgress.Order;
import dev.mesbase.business.production.domain.WorkOrderProgress.Page;
import dev.mesbase.business.production.domain.WorkOrderProgress.Progress;
import dev.mesbase.business.production.domain.WorkOrderProgress.Quality;
import dev.mesbase.business.production.domain.WorkOrderProgress.Query;
import dev.mesbase.business.production.domain.WorkOrderStatus;
import dev.mesbase.business.production.domain.ProductionExecutionStatus;
import dev.mesbase.business.quality.service.QualityTreatmentService;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.CurrentFact;
import java.util.HashMap;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Attribution;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.State;
import dev.mesbase.common.utils.StringUtils;
import dev.mesbase.common.validation.ParameterChecks;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class WorkOrderProgressQueryService {
    private final WorkOrderProgressExtraMapper mapper;

    private final QualityTreatmentService treatments;

    public WorkOrderProgressQueryService(WorkOrderProgressExtraMapper mapper, QualityTreatmentService treatments) {
        this.mapper = mapper;
        this.treatments = treatments;
    }

    private static final class QualityTotals {
        private BigDecimal untreated = BigDecimal.ZERO;
        private BigDecimal returned = BigDecimal.ZERO;
        private BigDecimal transferred = BigDecimal.ZERO;
        private BigDecimal unverified = BigDecimal.ZERO;
        private BigDecimal completedLoss = BigDecimal.ZERO;
        private boolean resolved = true;

        private void add(CurrentFact fact, String executionStatus) {
            var summary = fact.summary(false);
            if (summary.state() == State.VOIDED) return;
            if (summary.state() == State.UNRESOLVED) {
                resolved = false;
                return;
            }
            if (summary.attribution() == Attribution.UNVERIFIED) unverified = unverified.add(fact.defectQty());
            else if (summary.attribution() == Attribution.INDEPENDENT) {
                untreated = untreated.add(summary.pendingDispositionQty());
                returned = returned.add(summary.returnedQty());
                transferred = transferred.add(summary.transferredQty());
                if (ProductionExecutionStatus.FULLY_RECEIVED.getValue().equals(executionStatus))
                    completedLoss = completedLoss.add(summary.netDefectQty());
            }
        }
    }


    @Transactional(transactionManager = "businessTransactionManager", readOnly = true,
            isolation = Isolation.REPEATABLE_READ)
    public Page page(Query query) {
        int offset = query.offset(), limit = query.limit();
        ParameterChecks.paging(offset, limit);
        String workorderCode = normalize(query.workorderCode());
        String productCode = normalize(query.productCode());
        String status = normalize(query.status());
        if (workorderCode != null && workorderCode.length() > 64
                || productCode != null && productCode.length() > 64
                || status != null && !"ALL".equals(status) && WorkOrderStatus.from(status) == null)
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        var filter = new Filter(prefixPattern(workorderCode), prefixPattern(productCode), status);
        List<Order> rows;
        long total;
        try (var page = PageHelper.offsetPage(offset, limit)) {
            rows = List.copyOf(mapper.orders(filter));
            total = page.getTotal();
        }
        if (rows.isEmpty()) return new Page(List.of(), total, offset, limit);
        var completions = mapper.completions(rows.stream().map(Order::id).toList()).stream()
                .collect(Collectors.toMap(Completion::workorderId, Function.identity()));
        var quality = new HashMap<Long, QualityTotals>();
        var workorders = rows.stream().map(Order::id).toList();
        int qualityOffset = 0;
        while (true) {
            List<Quality> references;
            try (var page = PageHelper.offsetPage(qualityOffset, 250, false)) {
                references = List.copyOf(mapper.quality(workorders));
            }
            if (references.isEmpty()) break;
            var facts = treatments.interpretFacts(references.stream().map(Quality::declaration).toList())
                    .stream().collect(Collectors.toMap(CurrentFact::declarationId, Function.identity()));
            for (var reference : references) {
                var fact = facts.get(reference.declarationId());
                if (fact == null) throw new FoundationException(ErrorCode.QUALITY_DATA_INVALID);
                quality.computeIfAbsent(reference.workorderId(), id -> new QualityTotals())
                        .add(fact, reference.executionStatus());
            }
            qualityOffset += references.size();
            if (references.size() < 250) break;
        }
        return new Page(rows.stream().map(row -> progress(row, completions.get(row.id()),
                        quality.getOrDefault(row.id(), new QualityTotals())))
                .toList(), total, offset, limit);
    }

    private static Progress progress(Order order, Completion completion, QualityTotals quality) {
        String issue = null;
        BigDecimal completed = BigDecimal.ZERO;
        if (completion != null) {
            completed = completion.quantity();
            if (completion.factCount() != completion.executionCount() || completion.invalidCount() > 0 || completed == null) {
                issue = "正式完工事实重复或数量异常，请核实";
                completed = null;
            }
        }
        if (order.plannedQty() == null || order.plannedQty().signum() <= 0)
            issue = "计划数量无效，无法计算完成率";
        BigDecimal received = completed;
        BigDecimal completedLoss = quality.completedLoss;
        BigDecimal percent = null, gap = null;
        BigDecimal untreated = quality.untreated, returned = quality.returned,
                unverified = quality.unverified, transferred = quality.transferred;
        boolean qualityResolved = quality.resolved;
        if (!qualityResolved) {
            untreated = returned = unverified = transferred = null;
            issue = issue == null ? "质量处置数量待确认" : issue + "；质量处置数量待确认";
        }
        BigDecimal unfinished = null;
        if (issue == null) {
            // Only receipt-closed executions contribute R + D. Q is a handoff, never extra output.
            completed = received.add(completedLoss);
            percent = completed.multiply(BigDecimal.valueOf(100)).divide(order.plannedQty(), 2, RoundingMode.HALF_UP);
            gap = order.plannedQty().subtract(received).max(BigDecimal.ZERO);
            unfinished = order.plannedQty().subtract(completed).max(BigDecimal.ZERO);
        } else if (!qualityResolved) {
            completed = null;
        }
        return new Progress(order.id(), order.code(), order.source(), order.status(), order.productCode(),
                order.productName(), order.unitName(), order.plannedQty(), completed, gap,
                unfinished, untreated, returned,
                unverified, percent, completion == null ? 0 : completion.executionCount(),
                Optional.ofNullable(completion).map(nullableValue -> nullableValue.lastCompletedAt())
                        .orElse(null), issue, order.createTime(), transferred);
    }


    private static String normalize(String text) {
        if (StringUtils.isBlank(text)) return null;
        return StringUtils.trim(text);
    }

    private static String prefixPattern(String value) {
        if (value == null) return null;
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
}
