package dev.mesbase.business.production.domain;

import dev.mesbase.common.utils.StringUtils;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Byte-exact, versioned identities. No trimming, case folding or Unicode normalization.
 */
public record ProductionScanCode(String productCode, String materialCode, String lotCode) {
    public ProductionScanCode {
        field(productCode, 64);
        field(materialCode, 64);
        field(lotCode, 128);
    }

    public String encode() {
        return "MES1." + part(productCode) + "." + part(materialCode) + "." + part(lotCode);
    }

    public String key() {
        return digest(encode());
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
        String[] p = value.split("\\.", -1);
        if (p.length != 4 || !p[0].equals("MES1")) return invalid();
        var result = new ProductionScanCode(decode(p[1]), decode(p[2]), decode(p[3]));
        if (!result.encode().equals(value)) return invalid();
        return result;
    }

    public record MaterialLot(String materialCode, String lotCode) {
    }

    public static MaterialLot material(String value) {
        if (value != null && value.startsWith("MES1.")) {
            var p = parse(value);
            return new MaterialLot(p.materialCode(), p.lotCode());
        }
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

    private static String part(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String value) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(Base64.getUrlDecoder().decode(value))).toString();
        } catch (IllegalArgumentException | CharacterCodingException failure) {
            return invalid();
        }
    }

    private static <T> T invalid() {
        throw new FoundationException(ErrorCode.EXECUTION_QR_INVALID);
    }
}
