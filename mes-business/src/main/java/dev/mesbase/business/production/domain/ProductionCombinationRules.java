package dev.mesbase.business.production.domain;

import dev.mesbase.business.production.domain.ProductionCombinationModels.Demand;
import dev.mesbase.business.production.domain.ProductionCombinationModels.Selection;
import dev.mesbase.common.utils.RequestFingerprint;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import tools.jackson.databind.ObjectMapper;

/**
 * Canonical BOM order and exact batch text; positions and quantities are deliberately absent.
 */
public final class ProductionCombinationRules {
    private static final String PREFIX = "COMB:";
    public static final Comparator<String> BATCH_ORDER = Comparator.nullsFirst(Comparator.naturalOrder());

    private ProductionCombinationRules() {
    }

    public static List<Selection> canonical(List<Demand> demands, List<Selection> selected) {
        var byId = new HashMap<Long, List<String>>();
        for (var row : selected) {
            if (row == null || row.demandLineId() == null || row.batches() == null || row.batches().isEmpty()
                    || row.batches().size() > 100 || byId.putIfAbsent(row.demandLineId(), row.batches()) != null)
                throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        if (byId.size() != demands.size()) throw new FoundationException(ErrorCode.BAD_REQUEST);
        var result = new ArrayList<Selection>();
        for (var demand : demands.stream().sorted(Comparator.comparingInt(Demand::lineNo).thenComparing(Demand::id))
                .toList()) {
            var values = byId.get(demand.id());
            if (values == null) throw new FoundationException(ErrorCode.BAD_REQUEST);
            var batches = new ArrayList<>(values.stream().distinct().sorted(BATCH_ORDER).toList());
            if (batches.stream().anyMatch(v -> v != null && (v.codePointCount(0, v.length()) > 128
                    || !StandardCharsets.UTF_8.newEncoder().canEncode(v)
                    || v.codePoints().anyMatch(Character::isISOControl))))
                throw new FoundationException(ErrorCode.BAD_REQUEST);
            result.add(new Selection(demand.id(), Collections.unmodifiableList(batches)));
        }
        return List.copyOf(result);
    }

    private record IdentityLine(Long demandLineId, int lineNo, Long materialId, String materialCode,
                                List<String> batches) {
    }

    private record Identity(Long workorderId, List<IdentityLine> lines) {
    }

    public static String fingerprint(Long workorderId, List<Demand> demands, List<Selection> selected) {
        var sorted = canonical(demands, selected);
        var index = new HashMap<Long, Demand>();
        demands.forEach(d -> index.put(d.id(), d));
        var lines = sorted.stream().map(s -> {
            var d = index.get(s.demandLineId());
            return new IdentityLine(d.id(), d.lineNo(), d.materialId(), d.materialCode(), s.batches());
        }).toList();
        return RequestFingerprint.sha256(new ObjectMapper().writeValueAsString(new Identity(workorderId, lines)));
    }

    public static String code(String factoryId, Long id) {
        if (factoryId == null || factoryId.isEmpty() || factoryId.length() > 128
                || factoryId.codePoints().anyMatch(Character::isISOControl) || id == null || id <= 0)
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        return PREFIX + factoryId + ":" + id;
    }

    public static boolean isCombination(String code) {
        return code != null && code.startsWith(PREFIX);
    }

    public static Long parse(String code, String expectedFactoryId) {
        if (!isCombination(code) || expectedFactoryId == null || !code.startsWith(PREFIX + expectedFactoryId + ":"))
            throw new FoundationException(ErrorCode.EXECUTION_QR_INVALID);
        try {
            long id = Long.parseLong(code.substring((PREFIX + expectedFactoryId + ":").length()));
            if (id <= 0 || !Objects.equals(code(expectedFactoryId, id), code)) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException failure) {
            throw new FoundationException(ErrorCode.EXECUTION_QR_INVALID, failure);
        }
    }
}
