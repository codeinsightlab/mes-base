package dev.mesbase.business.production.domain;

import dev.mesbase.common.utils.RequestFingerprint;

import dev.mesbase.common.validation.ParameterChecks;
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
            if (id == null || !UUID.fromString(id).toString().equals(id)) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        } catch (IllegalArgumentException e) {
            ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        }
    }

    public static void quantity(BigDecimal q, boolean zero) {
        if (q == null || q.signum() < (zero ? 0 : 1) || q.scale() > 6 || q.precision() - q.scale() > 14)
            ParameterChecks.reject(ErrorCode.BAD_REQUEST);
    }

    public static void reason(String reason) {
        ParameterChecks.text(reason, true, 500, ErrorCode.BAD_REQUEST);
    }

    public static String hash(Object... values) {
        return RequestFingerprint.sha256(new ObjectMapper().writeValueAsString(Arrays.asList(values)));
    }

    public static String number(BigDecimal q) {
        return q.stripTrailingZeros().toPlainString();
    }

    public static Long actor() {
        Long id = SecurityUtils.getUserId();
        if (id == null) throw new FoundationException(ErrorCode.UNAUTHENTICATED);
        return id;
    }


    public static void blocked(String explanation) {
        throw new ProductionClosureBlockedException(explanation);
    }
}
