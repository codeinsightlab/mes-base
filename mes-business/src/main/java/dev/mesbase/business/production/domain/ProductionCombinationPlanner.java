package dev.mesbase.business.production.domain;

import dev.mesbase.business.production.domain.ProductionCombinationModels.Allocation;
import dev.mesbase.business.production.domain.ProductionCombinationModels.Demand;
import dev.mesbase.business.production.domain.ProductionCombinationModels.Plan;
import dev.mesbase.business.production.domain.ProductionCombinationModels.Selection;
import dev.mesbase.business.production.domain.ProductionCombinationModels.Stock;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Pure bounded integer-capacity planning. No database, stock reservation or execution side effects.
 */
public final class ProductionCombinationPlanner {
    private static final BigInteger ZERO = BigInteger.ZERO;
    private static final BigInteger ONE = BigInteger.ONE;
    private static final BigInteger TWO = BigInteger.TWO;
    private static final int MAX_EDGES = 20000;

    private static final class Budget {
        private long operations = 5_000_000;

        void use() {
            if (--operations < 0) throw new FoundationException(ErrorCode.PRODUCTION_COMBINATION_BUDGET);
        }
    }

    private ProductionCombinationPlanner() {
    }

    private record Batch(Long materialId, String batchNo) {
    }

    private record Lot(Batch batch, List<Stock> positions, BigInteger available) {
    }

    private static BigInteger scaled(BigDecimal value) {
        return value.setScale(6, RoundingMode.UNNECESSARY).unscaledValue();
    }

    private static BigDecimal decimal(BigInteger value) {
        return new BigDecimal(value, 6);
    }

    private static BigInteger roundedMaterial(Demand demand, BigInteger output, BigInteger plan) {
        return scaled(demand.totalRequiredQty()).multiply(output).multiply(TWO).add(plan).divide(plan.multiply(TWO));
    }

    private static BigInteger quantum(int precision) {
        if (precision < 0 || precision > 6) throw new FoundationException(ErrorCode.BAD_REQUEST);
        return BigInteger.TEN.pow(6 - precision);
    }

    public static Plan generate(List<Demand> demands, List<Stock> stocks, BigDecimal plan,
                                BigDecimal confirmed, Map<Long, BigDecimal> issued, BigDecimal requested,
                                BigDecimal remaining, int outputPrecision) {
        validate(demands, stocks, plan);
        var budget = new Budget();
        var lots = lots(stocks, demands);
        var all = selections(demands, lots);
        var maximum = maximum(demands, lots, all, plan, confirmed, issued, requested.min(remaining), outputPrecision, budget);
        if (maximum.signum() <= 0) return new Plan(BigDecimal.ZERO, List.of(), List.of());
        // The same material has the same allowed source set during generation. Largest lots first
        // attain the minimum number of distinct lots needed for its total material requirement.
        var needs = requirements(demands, plan, confirmed, issued, maximum);
        var totalByMaterial = new HashMap<Long, BigInteger>();
        for (int i = 0; i < demands.size(); i++)
            totalByMaterial.merge(demands.get(i).materialId(), needs.get(i), BigInteger::add);
        var chosen = new ArrayList<Lot>();
        for (var material : totalByMaterial.keySet().stream().sorted().toList()) {
            var rest = totalByMaterial.get(material);
            boolean hasBatch = false;
            for (var lot : lots.stream().filter(l -> l.batch().materialId().equals(material))
                    .sorted(Comparator.comparing(Lot::available).reversed()
                            .thenComparing(l -> l.batch().batchNo(), ProductionCombinationRules.BATCH_ORDER))
                    .toList()) {
                // Every required material keeps a fixed source identity, even when this
                // small planning quantity rounds its current cumulative increment to zero.
                if (rest.signum() <= 0 && hasBatch) break;
                chosen.add(lot);
                hasBatch = true;
                rest = rest.subtract(lot.available());
            }
        }
        var allocation = allocate(demands, chosen, selections(demands, chosen), needs, true, budget);
        if (allocation == null) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        // Include the selected material lot set on every same-material demand line. A zero
        // allocation now must not make a later positive cumulative rounding delta impossible.
        return new Plan(maximum, ProductionCombinationRules.canonical(demands, selections(demands, chosen)), allocation);
    }

    public static Plan fixed(List<Demand> demands, List<Stock> stocks, List<Selection> selected,
                             BigDecimal plan, BigDecimal confirmed, Map<Long, BigDecimal> issued,
                             BigDecimal requested, BigDecimal remaining, int outputPrecision) {
        validate(demands, stocks, plan);
        var budget = new Budget();
        var canonical = ProductionCombinationRules.canonical(demands, selected);
        var lots = lots(stocks, demands);
        var maximum = maximum(demands, lots, canonical, plan, confirmed, issued, remaining, outputPrecision, budget);
        if (requested == null) return new Plan(maximum, canonical, List.of());
        if (requested.signum() <= 0 || requested.compareTo(maximum) > 0
                || scaled(requested).remainder(quantum(outputPrecision)).signum() != 0)
            throw new FoundationException(ErrorCode.PRODUCTION_INPUT_QUOTA_EXCEEDED);
        var needs = requirements(demands, plan, confirmed, issued, requested);
        for (int i = 0; i < demands.size(); i++)
            if (needs.get(i).signum() < 0
                    || needs.get(i).remainder(quantum(demands.get(i).quantityPrecision())).signum() != 0)
                throw new FoundationException(ErrorCode.BAD_REQUEST);
        var allocation = allocate(demands, lots, canonical, needs, true, budget);
        if (allocation == null || allocation.isEmpty()) throw new FoundationException(ErrorCode.STOCK_INSUFFICIENT);
        return new Plan(requested, canonical, allocation);
    }

    private static void validate(List<Demand> demands, List<Stock> stocks, BigDecimal plan) {
        if (demands == null || demands.isEmpty() || demands.size() > 100 || stocks == null || stocks.size() > 2000
                || plan == null || plan.signum() <= 0) throw new FoundationException(ErrorCode.BAD_REQUEST);
        if (demands.stream().anyMatch(d -> d.totalRequiredQty() == null || d.totalRequiredQty().signum() <= 0))
            throw new FoundationException(ErrorCode.BAD_REQUEST);
    }

    private static List<Lot> lots(List<Stock> stocks, List<Demand> demands) {
        var precisions = new HashMap<Long, Integer>();
        demands.forEach(d -> precisions.put(d.materialId(), d.quantityPrecision()));
        if (stocks.stream().anyMatch(s -> s.availableQty() == null || s.availableQty().signum() < 0))
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        var usable = stocks.stream().filter(s -> precisions.containsKey(s.materialId())).map(s ->
                new Stock(s.id(), s.materialId(), s.batchNo(), s.warehouseId(), s.binCode(), s.unitId(),
                        s.availableQty().setScale(precisions.get(s.materialId()), RoundingMode.DOWN))).toList();
        var warehouseMaterials = new HashMap<Long, Set<Long>>();
        usable.stream().filter(s -> s.availableQty().signum() > 0).forEach(s ->
                warehouseMaterials.computeIfAbsent(s.warehouseId(), id -> new HashSet<>()).add(s.materialId()));
        var groups = new LinkedHashMap<Batch, List<Stock>>();
        var seen = new HashSet<Long>();
        for (var stock : usable.stream().sorted(Comparator.comparing(Stock::materialId)
                        .thenComparing(Stock::batchNo, ProductionCombinationRules.BATCH_ORDER).thenComparing(Stock::id))
                .toList()) {
            if (!seen.add(stock.id()) || stock.availableQty().signum() < 0)
                throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            if (stock.availableQty().signum() == 0) continue;
            groups.computeIfAbsent(new Batch(stock.materialId(), stock.batchNo()), k -> new ArrayList<>()).add(stock);
        }
        return groups.entrySet().stream().map(e -> new Lot(e.getKey(), e.getValue().stream()
                .sorted(Comparator.<Stock>comparingInt(s -> warehouseMaterials.get(s.warehouseId()).size()).reversed()
                        .thenComparing(Stock::warehouseId)
                        .thenComparing(Comparator.comparing(Stock::availableQty).reversed()).thenComparing(Stock::id))
                .toList(),
                e.getValue().stream().map(s -> scaled(s.availableQty())).reduce(ZERO, BigInteger::add))).toList();
    }

    private static List<Selection> selections(List<Demand> demands, List<Lot> lots) {
        return demands.stream()
                .map(d -> new Selection(d.id(), lots.stream().filter(l -> l.batch().materialId().equals(d.materialId()))
                        .map(l -> l.batch().batchNo()).sorted(ProductionCombinationRules.BATCH_ORDER).toList()))
                .toList();
    }

    private static List<BigInteger> requirements(List<Demand> demands, BigDecimal plan, BigDecimal confirmed,
                                                 Map<Long, BigDecimal> issued, BigDecimal additional) {
        var target = scaled(confirmed.add(additional));
        var total = scaled(plan);
        return demands.stream()
                .map(d -> roundedMaterial(d, target, total).subtract(scaled(issued.getOrDefault(d.id(), BigDecimal.ZERO))))
                .toList();
    }

    private static BigDecimal maximum(List<Demand> demands, List<Lot> lots, List<Selection> selected,
                                      BigDecimal plan, BigDecimal confirmed, Map<Long, BigDecimal> issued,
                                      BigDecimal remaining, int precision, Budget budget) {
        if (remaining.signum() <= 0 || lots.isEmpty() || selected.stream().anyMatch(s -> s.batches().isEmpty()))
            return BigDecimal.ZERO;
        var step = quantum(precision);
        var low = ZERO;
        var high = scaled(remaining).divide(step);
        while (low.compareTo(high) < 0) {
            var middle = low.add(high).add(ONE).divide(TWO);
            var needs = requirements(demands, plan, confirmed, issued, decimal(middle.multiply(step)));
            if (needs.stream()
                    .allMatch(n -> n.signum() >= 0) && allocate(demands, lots, selected, needs, false, budget) != null)
                low = middle;
            else high = middle.subtract(ONE);
        }
        var base = scaled(confirmed);
        var target = base.add(low.multiply(step));
        var p = scaled(plan);
        // Project to the greatest legal output grid point. Each jump removes only values whose
        // cumulative material amount cannot satisfy that material's precision.
        for (int attempt = 0; attempt < 10000 && target.compareTo(base) > 0; attempt++) {
            boolean valid = true;
            for (var d : demands) {
                var value = roundedMaterial(d, target, p).subtract(scaled(issued.getOrDefault(d.id(), BigDecimal.ZERO)));
                var unitStep = quantum(d.quantityPrecision());
                if (value.signum() < 0) throw new FoundationException(ErrorCode.PRODUCTION_INPUT_STALE);
                if (value.remainder(unitStep).signum() != 0) {
                    var aligned = value.divide(unitStep).multiply(unitStep)
                            .add(scaled(issued.getOrDefault(d.id(), BigDecimal.ZERO)));
                    var upper = p.multiply(aligned.multiply(TWO).add(ONE)).subtract(ONE)
                            .divide(scaled(d.totalRequiredQty()).multiply(TWO));
                    target = base.add(upper.subtract(base).max(ZERO).divide(step).multiply(step));
                    valid = false;
                    break;
                }
            }
            if (valid) {
                var needs = requirements(demands, plan, confirmed, issued, decimal(target.subtract(base)));
                if (needs.stream().allMatch(amount -> amount.signum() == 0)) return BigDecimal.ZERO;
                return decimal(target.subtract(base));
            }
        }
        if (target.compareTo(base) <= 0) return BigDecimal.ZERO;
        throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
    }

    private static List<Allocation> allocate(List<Demand> demands, List<Lot> lots, List<Selection> selections,
                                             List<BigInteger> amounts, boolean output, Budget budget) {
        int source = demands.size() + lots.size(), sink = source + 1;
        var graph = new Flow(sink + 1, budget);
        var links = new ArrayList<Link>();
        int edges = 0;
        BigInteger total = ZERO;
        for (int i = 0; i < demands.size(); i++) {
            var amount = amounts.get(i);
            if (amount.signum() < 0) return null;
            total = total.add(amount);
            graph.add(source, i, amount);
            var demand = demands.get(i);
            var allowed = selections.stream().filter(s -> s.demandLineId().equals(demand.id())).findFirst()
                    .orElseThrow().batches();
            for (int j = 0; j < lots.size(); j++)
                if (lots.get(j).batch().materialId().equals(demands.get(i).materialId())
                        && allowed.contains(lots.get(j).batch().batchNo())) {
                    if (++edges > MAX_EDGES) throw new FoundationException(ErrorCode.PRODUCTION_COMBINATION_BUDGET);
                    links.add(new Link(i, j, graph.add(i, demands.size() + j, amount)));
                }
        }
        for (int j = 0; j < lots.size(); j++) graph.add(demands.size() + j, sink, lots.get(j).available());
        if (!graph.max(source, sink).equals(total)) return null;
        if (!output) return List.of();
        var balances = new HashMap<Long, BigInteger>();
        lots.forEach(l -> l.positions().forEach(s -> balances.put(s.id(), scaled(s.availableQty()))));
        var result = new ArrayList<Allocation>();
        for (var link : links) {
            var rest = link.edge().initial.subtract(link.edge().capacity);
            var d = demands.get(link.demand());
            for (var stock : lots.get(link.lot()).positions()) {
                var take = rest.min(balances.get(stock.id()));
                if (take.signum() <= 0) continue;
                result.add(new Allocation(d.id(), stock.id(), d.materialId(), stock.warehouseId(), stock.binCode(), stock.batchNo(), d.unitId(), decimal(take)));
                balances.put(stock.id(), balances.get(stock.id()).subtract(take));
                rest = rest.subtract(take);
            }
        }
        if (result.size() > 100) throw new FoundationException(ErrorCode.BAD_REQUEST);
        return List.copyOf(result);
    }

    private record Link(int demand, int lot, Flow.Edge edge) {
    }

    /**
     * Integer residual flow also handles two demand lines sharing the same material/lot.
     */
    private static final class Flow {
        private static final class Edge {
            final int to, reverse;
            final BigInteger initial;
            BigInteger capacity;

            Edge(int to, int reverse, BigInteger capacity) {
                this.to = to;
                this.reverse = reverse;
                this.initial = capacity;
                this.capacity = capacity;
            }
        }

        private final Budget budget;
        private final List<List<Edge>> graph;

        Flow(int size, Budget budget) {
            this.budget = budget;
            graph = new ArrayList<>();
            for (int i = 0; i < size; i++) graph.add(new ArrayList<>());
        }

        Edge add(int from, int to, BigInteger capacity) {
            budget.use();
            var forward = new Edge(to, graph.get(to).size(), capacity);
            var back = new Edge(from, graph.get(from).size(), ZERO);
            graph.get(from).add(forward);
            graph.get(to).add(back);
            return forward;
        }

        BigInteger max(int source, int sink) {
            BigInteger result = ZERO;
            while (true) {
                int[] parent = new int[graph.size()], edge = new int[graph.size()];
                Arrays.fill(parent, -1);
                var queue = new ArrayDeque<Integer>();
                queue.add(source);
                parent[source] = source;
                while (!queue.isEmpty() && parent[sink] < 0) {
                    int from = queue.remove();
                    for (int i = 0; i < graph.get(from).size(); i++) {
                        budget.use();
                        var e = graph.get(from).get(i);
                        if (e.capacity.signum() > 0 && parent[e.to] < 0) {
                            parent[e.to] = from;
                            edge[e.to] = i;
                            queue.add(e.to);
                        }
                    }
                }
                if (parent[sink] < 0) return result;
                BigInteger amount = null;
                for (int at = sink; at != source; at = parent[at]) {
                    var capacity = graph.get(parent[at]).get(edge[at]).capacity;
                    if (amount == null) amount = capacity;
                    else amount = amount.min(capacity);
                }
                for (int at = sink; at != source; at = parent[at]) {
                    var e = graph.get(parent[at]).get(edge[at]);
                    e.capacity = e.capacity.subtract(amount);
                    var back = graph.get(at).get(e.reverse);
                    back.capacity = back.capacity.add(amount);
                }
                result = result.add(amount);
            }
        }
    }
}
