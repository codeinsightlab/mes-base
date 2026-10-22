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
import dev.mesbase.business.quality.domain.DefectDestination;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Attribution;
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

    public WorkOrderProgressQueryService(WorkOrderProgressExtraMapper mapper) {
        this.mapper = mapper;
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
        var quality = mapper.quality(rows.stream().map(Order::id).toList()).stream()
                .collect(Collectors.groupingBy(Quality::workorderId));
        return new Page(rows.stream().map(row -> progress(row, completions.get(row.id()),
                        quality.getOrDefault(row.id(), List.of())))
                .toList(), total, offset, limit);
    }

    private static Progress progress(Order order, Completion completion, List<Quality> quality) {
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
        BigDecimal percent = null, gap = null;
        if (issue == null) {
            percent = completed.multiply(BigDecimal.valueOf(100)).divide(order.plannedQty(), 2, RoundingMode.HALF_UP);
            gap = order.plannedQty().subtract(completed).max(BigDecimal.ZERO);
        }
        BigDecimal scrap = BigDecimal.ZERO, untreated = BigDecimal.ZERO, waiting = BigDecimal.ZERO,
                reworking = BigDecimal.ZERO, unverified = BigDecimal.ZERO, transferred = BigDecimal.ZERO;
        boolean invalidQuality = false;
        for (var fact : quality) {
            // Returning material to a routing node is reprocessing, not a confirmed loss.
            if (DefectDestination.ROUTING_NODE.getValue().equals(fact.destinationType())) continue;
            if (Attribution.OVERLAP.name().equals(fact.attribution())) continue;
            if (!Attribution.INDEPENDENT.name().equals(fact.attribution())) {
                unverified = unverified.add(fact.declaredQty());
                continue;
            }
            var disposed = fact.scrapQty().add(fact.failedQty());
            var pendingDisposition = fact.declaredQty().subtract(fact.scrapQty()).subtract(fact.allocatedReworkQty())
                    .subtract(fact.returnedQty()).subtract(fact.transferredQty());
            var pendingStart = fact.allocatedReworkQty().subtract(fact.startedQty());
            var inProgress = fact.startedQty().subtract(fact.processedQty());
            if (fact.scrapQty().signum() < 0 || fact.failedQty().signum() < 0
                    || fact.recoveredQty().signum() < 0 || fact.allocatedReworkQty().signum() < 0
                    || fact.startedQty().signum() < 0 || pendingDisposition.signum() < 0 || pendingStart.signum() < 0
                    || inProgress.signum() < 0 || fact.processedQty().signum() < 0
                    || fact.processedQty().compareTo(fact.failedQty().add(fact.recoveredQty())) != 0)
                invalidQuality = true;
            scrap = scrap.add(disposed);
            transferred = transferred.add(fact.transferredQty());
            untreated = untreated.add(pendingDisposition);
            waiting = waiting.add(pendingStart);
            reworking = reworking.add(inProgress);
        }
        if (invalidQuality) issue = "质量处置数量异常，请核实";
        BigDecimal unfinished = null;
        if (gap != null && !invalidQuality)
            unfinished = order.plannedQty().subtract(completed).subtract(scrap).subtract(transferred)
                    .max(BigDecimal.ZERO);
        return new Progress(order.id(), order.code(), order.source(), order.status(), order.productCode(),
                order.productName(), order.unitName(), order.plannedQty(), completed, gap,
                invalidQuality ? null : scrap, unfinished, invalidQuality ? null : untreated,
                invalidQuality ? null : waiting, invalidQuality ? null : reworking,
                unverified, percent, completion == null ? 0 : completion.executionCount(),
                Optional.ofNullable(completion).map(nullableValue -> nullableValue.lastCompletedAt())
                        .orElse(null), issue, order.createTime(), invalidQuality ? null : transferred);
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
