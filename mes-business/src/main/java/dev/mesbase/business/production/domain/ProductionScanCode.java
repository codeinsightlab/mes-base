package dev.mesbase.business.production.domain;

import dev.mesbase.common.utils.StringUtils;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Work order:product#batch text. No trimming, case folding or Unicode normalization.
 */
public record ProductionScanCode(String workorderCode, String productCode, String batchNo) {
    public ProductionScanCode {
        field(workorderCode, 64);
        field(productCode, 64);
        field(batchNo, 128);
        if (workorderCode.indexOf(':') >= 0 || productCode.indexOf('#') >= 0) invalid();
    }

    public String encode() {
        return workorderCode + ":" + productCode + "#" + batchNo;
    }

    public static String digest(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    public static ProductionScanCode parse(String value) {
        if (value == null || value.length() > 2048) return invalid();
        int orderEnd = value.indexOf(':');
        int productEnd = value.indexOf('#', orderEnd + 1);
        if (orderEnd <= 0 || productEnd <= orderEnd + 1 || productEnd >= value.length() - 1) return invalid();
        return new ProductionScanCode(value.substring(0, orderEnd), value.substring(orderEnd + 1, productEnd),
                value.substring(productEnd + 1));
    }

    public record MaterialLot(String materialCode, String lotCode) {
    }

    public static MaterialLot material(String value) {
        if (value == null || value.length() > 1024) return invalid();
        String[] p = value.split("#", -1);
        if (p.length != 2) return invalid();
        field(p[0], 64);
        field(p[1], 128);
        return new MaterialLot(p[0], p[1]);
    }

    private static void field(String value, int max) {
        if (StringUtils.isBlank(value) || value.codePointCount(0, value.length()) > max || value.codePoints()
                .anyMatch(c -> Character.isISOControl(c))
                || !StandardCharsets.UTF_8.newEncoder().canEncode(value)) invalid();
    }

    private static <T> T invalid() {
        throw new FoundationException(ErrorCode.EXECUTION_QR_INVALID);
    }
}
