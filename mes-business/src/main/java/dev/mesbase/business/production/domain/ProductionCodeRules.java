package dev.mesbase.business.production.domain;

import dev.mesbase.common.utils.StringUtils;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.nio.charset.StandardCharsets;

/**
 * The sole production code format is work-order:finished-product#batch, with byte-exact field semantics.
 */
public final class ProductionCodeRules {
    private ProductionCodeRules() {
    }

    public static ProductionScanCode parse(String value) {
        if (value == null || value.length() > 2048) throw invalid();
        int orderEnd = value.indexOf(':');
        int productEnd = value.indexOf('#', orderEnd + 1);
        if (orderEnd <= 0 || productEnd <= orderEnd + 1 || productEnd >= value.length() - 1) throw invalid();
        var fields = new ProductionScanCode(value.substring(0, orderEnd), value.substring(orderEnd + 1, productEnd),
                value.substring(productEnd + 1));
        validate(fields);
        return fields;
    }

    public static String format(ProductionScanCode fields) {
        validate(fields);
        return fields.workorderCode() + ":" + fields.productCode() + "#" + fields.batchNo();
    }

    public static void validate(ProductionScanCode fields) {
        if (fields == null) throw invalid();
        field(fields.workorderCode(), 64);
        field(fields.productCode(), 64);
        field(fields.batchNo(), 128);
        if (fields.workorderCode().indexOf(':') >= 0 || fields.productCode().indexOf('#') >= 0) throw invalid();
    }

    private static void field(String value, int maximum) {
        if (StringUtils.isBlank(value) || value.codePointCount(0, value.length()) > maximum
                || value.codePoints().anyMatch(Character::isISOControl)
                || !StandardCharsets.UTF_8.newEncoder().canEncode(value)) throw invalid();
    }

    private static FoundationException invalid() {
        return new FoundationException(ErrorCode.EXECUTION_QR_INVALID);
    }
}
