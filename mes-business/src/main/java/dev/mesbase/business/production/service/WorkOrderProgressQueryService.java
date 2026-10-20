package dev.mesbase.business.production.service;

import com.github.pagehelper.PageHelper;
import dev.mesbase.business.production.dao.WorkOrderProgressExtraMapper;
import dev.mesbase.business.production.domain.WorkOrderProgress.Completion;
import dev.mesbase.business.production.domain.WorkOrderProgress.Order;
import dev.mesbase.business.production.domain.WorkOrderProgress.Progress;
import dev.mesbase.business.production.domain.WorkOrderStatus;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
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

    public record Page(List<Progress> items, long total, int offset, int limit) {
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true,
            isolation = Isolation.REPEATABLE_READ)
    public Page page(String keyword, String status, int offset, int limit) {
        ProductionQuantityService.paging(offset, limit);
        keyword = normalize(keyword);
        status = normalize(status);
        if (keyword != null && keyword.length() > 100
                || status != null && !"ALL".equals(status) && WorkOrderStatus.from(status) == null)
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        String pattern = keyword == null ? null : "%" + keyword.replace("!", "!!")
                .replace("%", "!%").replace("_", "!_") + "%";
        List<Order> rows;
        long total;
        try (var page = PageHelper.offsetPage(offset, limit)) {
            rows = List.copyOf(mapper.orders(pattern, status));
            total = page.getTotal();
        }
        if (rows.isEmpty()) return new Page(List.of(), total, offset, limit);
        var completions = mapper.completions(rows.stream().map(Order::id).toList()).stream()
                .collect(Collectors.toMap(Completion::workorderId, Function.identity()));
        return new Page(rows.stream().map(row -> progress(row, completions.get(row.id())))
                .toList(), total, offset, limit);
    }

    private static Progress progress(Order order, Completion completion) {
        String issue = completion != null && (completion.factCount() != completion.executionCount()
                || completion.invalidCount() > 0 || completion.quantity() == null)
                ? "正式完工事实重复或数量异常，请核实" : null;
        BigDecimal completed = issue != null ? null : completion == null ? BigDecimal.ZERO : completion.quantity();
        if (order.plannedQty() == null || order.plannedQty().signum() <= 0)
            issue = "计划数量无效，无法计算完成率";
        BigDecimal percent = issue == null ? completed.multiply(BigDecimal.valueOf(100))
                .divide(order.plannedQty(), 2, RoundingMode.HALF_UP) : null;
        BigDecimal remaining = issue == null ? order.plannedQty().subtract(completed).max(BigDecimal.ZERO) : null;
        return new Progress(order, completed, remaining, percent, completion == null ? 0 : completion.executionCount(),
                completion == null ? null : completion.lastCompletedAt(), issue);
    }

    private static String normalize(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
