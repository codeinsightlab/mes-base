package dev.mesbase.business.production.domain;

import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.Arrays;
import tools.jackson.databind.ObjectMapper;

/**
 * Validation shared by the local closure commands, never a second quantity calculator.
 */
public final class ProductionClosureRules {
    private ProductionClosureRules() {
    }

    public static void uuid(String id) {
        try {
            if (id == null || !UUID.fromString(id).toString().equals(id)) bad();
        } catch (IllegalArgumentException e) {
            bad();
        }
    }

    public static void quantity(BigDecimal q, boolean zero) {
        if (q == null || q.signum() < (zero ? 0 : 1) || q.scale() > 6 || q.precision() - q.scale() > 14) bad();
    }

    public static void reason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500) bad();
    }

    public static String hash(Object... values) {
        return ProductionScanCode.digest(new ObjectMapper().writeValueAsString(Arrays.asList(values)));
    }

    public static String number(BigDecimal q) {
        return q.stripTrailingZeros().toPlainString();
    }

    public static Long actor() {
        Long id = SecurityUtils.getUserId();
        if (id == null) throw new FoundationException(ErrorCode.UNAUTHENTICATED);
        return id;
    }

    public static void bad() {
        throw new FoundationException(ErrorCode.BAD_REQUEST);
    }

    public static void conflict() {
        throw new FoundationException(ErrorCode.PRODUCTION_CLOSURE_CONFLICT);
    }

    public static void blocked(String explanation) {
        throw new ProductionClosureBlockedException(explanation);
    }
}
