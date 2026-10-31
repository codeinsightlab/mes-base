package dev.mesbase.business.production;

import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.Decoder;
import dev.mesbase.business.production.domain.ProductionCombinationModels.Demand;
import dev.mesbase.business.production.domain.ProductionCombinationModels.Selection;
import dev.mesbase.business.production.domain.ProductionCombinationModels.Stock;
import dev.mesbase.business.production.domain.ProductionCombinationPlanner;
import dev.mesbase.business.production.domain.ProductionCombinationQr;
import dev.mesbase.business.production.domain.ProductionCombinationRules;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionCombinationRulesTest {
    private static BigDecimal n(String value) {
        return new BigDecimal(value);
    }

    private static Demand d(long id, int line, long material, String total) {
        return new Demand(id, line, material, "M" + material, 1L, n(total), 6);
    }

    private static Stock s(long id, long material, String batch, String available) {
        return new Stock(id, material, batch, id, "B" + id, 1L, n(available));
    }

    @Test
    void qrEncodesTheExactFactoryScopedStableIdentity() throws Exception {
        String code = ProductionCombinationRules.code("A", 9007199254740993L);
        var matrix = ProductionCombinationQr.matrix(code);
        int[] region = matrix.getEnclosingRectangle();
        var modules = new BitMatrix(region[2], region[3]);
        for (int y = 0; y < region[3]; y++)
            for (int x = 0; x < region[2]; x++)
                if (matrix.get(region[0] + x, region[1] + y)) modules.set(x, y);
        assertEquals(code, new Decoder().decode(modules).getText());
        assertThrows(RuntimeException.class, () -> ProductionCombinationRules.parse(code, "B"));
    }

    @Test
    void identityUsesBomLineOrderAndBatchSetsButNoQuantityOrPosition() {
        var demands = List.of(d(2, 20, 2, "100"), d(1, 10, 1, "200"));
        var first = List.of(new Selection(1L, List.of("B02", "B01", "B01")), new Selection(2L, List.of("C01")));
        var other = List.of(new Selection(2L, List.of("C01")), new Selection(1L, List.of("B01", "B02")));
        assertEquals(ProductionCombinationRules.fingerprint(7L, demands, first), ProductionCombinationRules.fingerprint(7L, demands.reversed(), other));
        assertNotEquals(ProductionCombinationRules.fingerprint(8L, demands, first), ProductionCombinationRules.fingerprint(7L, demands, first));
        assertNotEquals(ProductionCombinationRules.fingerprint(7L, demands, List.of(new Selection(1L, List.of("b01", "B02")), new Selection(2L, List.of("C01")))), ProductionCombinationRules.fingerprint(7L, demands, first));
        assertEquals(9L, ProductionCombinationRules.parse(ProductionCombinationRules.code("A", 9L), "A"));
        assertThrows(RuntimeException.class, () -> ProductionCombinationRules.parse("COMB:A:09", "A"));
    }

    @Test
    void multipleLotsRemainOneCombinationAndSmallerRequestUsesOnlyFixedLots() {
        var demands = List.of(d(1, 1, 1, "200"), d(2, 2, 2, "100"));
        var stocks = List.of(s(1, 1, "B01", "120"), s(2, 1, "B02", "80"), s(3, 2, "C01", "100"));
        var plan = ProductionCombinationPlanner.generate(demands, stocks, n("100"), BigDecimal.ZERO, Map.of(), n("100"), n("100"), 0);
        assertEquals(0, plan.outputQty().compareTo(n("100")));
        assertEquals(List.of("B01", "B02"), plan.selections().getFirst().batches());
        var smaller = ProductionCombinationPlanner.fixed(demands, stocks, plan.selections(), n("100"), BigDecimal.ZERO, Map.of(), n("50"), n("100"), 0);
        assertEquals(0, smaller.allocations().stream().filter(a -> a.materialId() == 1L).map(a -> a.quantity())
                .reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(n("100")));
        var shortage = ProductionCombinationPlanner.fixed(demands, List.of(s(1, 1, "B01", "70"), s(4, 1, "OTHER", "999"), s(3, 2, "C01", "100")), plan.selections(), n("100"), BigDecimal.ZERO, Map.of(), null, n("100"), 0);
        assertEquals(0, shortage.outputQty().compareTo(n("35")));
    }

    @Test
    void repeatedMaterialRowsShareOneStockBalanceAndFixedLotPermissionsUseResidualFlow() {
        var demands = List.of(d(1, 1, 1, "100"), d(2, 2, 1, "100"));
        var stocks = List.of(s(1, 1, "A", "60"), s(2, 1, "B", "40"));
        var selected = List.of(new Selection(1L, List.of("A", "B")), new Selection(2L, List.of("A")));
        var plan = ProductionCombinationPlanner.fixed(demands, stocks, selected, n("100"), BigDecimal.ZERO, Map.of(), n("50"), n("100"), 0);
        assertEquals(0, plan.allocations().stream().map(a -> a.quantity()).reduce(BigDecimal.ZERO, BigDecimal::add)
                .compareTo(n("100")));
        assertEquals(0, plan.allocations().stream().filter(a -> a.stockId() == 1L).map(a -> a.quantity())
                .reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(n("60")));
        assertThrows(RuntimeException.class, () -> ProductionCombinationPlanner.fixed(demands, stocks, selected, n("100"), BigDecimal.ZERO, Map.of(), n("51"), n("100"), 0));
    }

    @Test
    void cumulativeSixDecimalRemainderIsNotRoundedOncePerDocument() {
        var demands = List.of(d(1, 1, 1, "1"));
        var selected = List.of(new Selection(1L, List.of("A")));
        var finalPart = ProductionCombinationPlanner.fixed(demands, List.of(s(1, 1, "A", "0.333334")), selected, n("3"), n("2"), Map.of(1L, n("0.666666")), n("1"), n("1"), 0);
        assertEquals(0, finalPart.allocations().getFirst().quantity().compareTo(n("0.333334")));
    }

    @Test
    void quantitiesRespectMaterialGridsAndDoNotCombineFractionalWholeUnitPositions() {
        var demand = new Demand(1L, 1, 1L, "M1", 1L, n("10"), 0);
        var result = ProductionCombinationPlanner.generate(List.of(demand), List.of(s(1, 1, "A", "1.9"), s(2, 1, "A", "1.1")), n("10"), BigDecimal.ZERO, Map.of(), n("10"), n("10"), 0);
        assertEquals(0, n("2").compareTo(result.outputQty()));
        assertEquals(2, result.allocations().size());
        result.allocations().forEach(a -> assertEquals(0, a.quantity().remainder(BigDecimal.ONE).signum()));
    }

    @Test
    void oversizedSharedLotGraphFailsExplicitlyInsteadOfTruncatingRequirements() {
        var demands = LongStream.rangeClosed(1, 100).mapToObj(id -> d(id, (int) id, 1, "1")).toList();
        var stocks = LongStream.rangeClosed(1, 201).mapToObj(id -> s(id, 1, "B" + id, "1")).toList();
        var failure = assertThrows(FoundationException.class, () -> ProductionCombinationPlanner.generate(demands, stocks, n("1"), BigDecimal.ZERO, Map.of(), n("1"), n("1"), 0));
        assertEquals(ErrorCode.PRODUCTION_COMBINATION_BUDGET, failure.code());
    }

    @Test
    void zeroRoundedMaterialCannotProduceAnEmptyExecutionAllowance() {
        var result = ProductionCombinationPlanner.generate(List.of(d(1, 1, 1, "0.000001")),
                List.of(s(1, 1, "A", "1")), n("1000000"), BigDecimal.ZERO, Map.of(), n("0.000001"), n("0.000001"), 6);
        assertEquals(0, result.outputQty().signum());
        assertTrue(result.allocations().isEmpty());
    }

    @Test
    void smallPlanningQuantityRetainsEveryMaterialsFixedBatchIdentity() {
        var demands = List.of(d(1, 1, 1, "100"), d(2, 2, 2, "0.000001"));
        var stocks = List.of(s(1, 1, "A", "1"), s(2, 2, "B", "1"));
        var result = ProductionCombinationPlanner.generate(demands, stocks, n("1000000"),
                BigDecimal.ZERO, Map.of(), n("1"), n("1"), 0);
        assertEquals(0, result.outputQty().compareTo(n("1")));
        assertEquals(List.of("B"), result.selections().get(1).batches());
        assertEquals(1, result.allocations().size());
        var fixed = ProductionCombinationPlanner.fixed(demands, stocks, result.selections(), n("1000000"),
                BigDecimal.ZERO, Map.of(), n("1"), n("1"), 0);
        assertEquals(result.allocations(), fixed.allocations());
    }
}

