package dev.mesbase.business.production.domain;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Static graph path validation; never enumerates all DAG combinations.
 */
public final class BatchPathPolicy {
    private BatchPathPolicy() {
    }

    public static void quantity(BigDecimal value) {
        if (value == null || value.signum() <= 0 || value.scale() > 6
                || value.precision() - value.scale() > 14) invalid();
    }

    public static void allocation(BigDecimal planned, List<WorkOrderModels.BatchInput> batches) {
        quantity(planned);
        if (batches == null || batches.isEmpty() || batches.size() > 100) invalid();
        Set<String> codes = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (var b : batches) {
            if (b == null || b.batchNo() == null || b.batchNo().isBlank()
                    || b.batchNo().length() > 128 || !codes.add(b.batchNo().trim().toLowerCase(Locale.ROOT))) invalid();
            quantity(b.plannedQty());
            total = total.add(b.plannedQty());
        }
        if (total.compareTo(planned) != 0) invalid();
    }

    public static void validate(List<Long> path, List<RoutingNodeEntity> nodes, List<RoutingEdgeEntity> edges) {
        if (path == null || path.isEmpty() || path.size() > 100 || path.stream().anyMatch(Objects::isNull)
                || new HashSet<>(path).size() != path.size()) invalid();
        Set<Long> ids = nodes.stream().map(RoutingNodeEntity::getId).collect(Collectors.toSet());
        if (!ids.containsAll(path) || edges.stream()
                .anyMatch(e -> !ids.contains(e.getFromNodeId()) || !ids.contains(e.getToNodeId()))) invalid();
        if (edges.stream().anyMatch(e -> e.getToNodeId().equals(path.getFirst()))
                || edges.stream().anyMatch(e -> e.getFromNodeId().equals(path.getLast()))) invalid();
        for (int i = 1; i < path.size(); i++) {
            Long from = path.get(i - 1), to = path.get(i);
            if (edges.stream().noneMatch(e -> e.getFromNodeId().equals(from) && e.getToNodeId().equals(to))) invalid();
        }
    }

    private static void invalid() {
        throw new FoundationException(ErrorCode.WORKORDER_INVALID);
    }
}
