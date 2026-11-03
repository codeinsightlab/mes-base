package dev.mesbase.business.production.domain;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Production QR protocol: workorder:material#batch$material#batch.
 *
 * <p>Contains no database identifiers, quantities or inventory locations. Material/batch pairs are
 * sorted by their exact original text and deduplicated. Case, whitespace and leading zeroes remain
 * significant. Reserved delimiters are percent escaped; an unbatched material uses {@code ~}, while
 * a literal tilde is escaped. This class owns normalization, parsing, validation and SVG rendering.
 */
public final class ProductionQrCode {
    public static final int MAX_LENGTH = 2048;
    private static final Comparator<String> UTF8_ORDER = (left, right) -> java.util.Arrays.compareUnsigned(
            left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    private static final Comparator<MaterialBatch> ORDER = Comparator
            .comparing(MaterialBatch::materialCode, UTF8_ORDER)
            .thenComparing(MaterialBatch::batchNo, Comparator.nullsFirst(UTF8_ORDER));

    private ProductionQrCode() {
    }

    public record MaterialBatch(String materialCode, String batchNo) {
    }

    public record Content(String workorderCode, List<MaterialBatch> materials) {
        public Content {
            materials = List.copyOf(materials);
        }
    }

    public static String generate(String workorderCode, List<MaterialBatch> materials) {
        text(workorderCode, 64, false);
        if (materials == null || materials.isEmpty() || materials.size() > 10000) throw invalid();
        for (var item : materials) {
            if (item == null) throw invalid();
            text(item.materialCode(), 64, false);
            if (item.batchNo() != null) text(item.batchNo(), 128, true);
        }
        var values = materials.stream().distinct().sorted(ORDER).map(item -> {
            String batch = "~";
            if (item.batchNo() != null) batch = encode(item.batchNo());
            return encode(item.materialCode()) + "#" + batch;
        }).toList();
        String code = encode(workorderCode) + ":" + String.join("$", values);
        if (code.length() > MAX_LENGTH || code.getBytes(StandardCharsets.UTF_8).length > MAX_LENGTH)
            throw invalid();
        return code;
    }

    public static Content parse(String code) {
        if (code == null || code.isEmpty() || code.length() > MAX_LENGTH) throw invalid();
        int separator = code.indexOf(':');
        if (separator <= 0 || code.indexOf(':', separator + 1) >= 0) throw invalid();
        String order = decode(code.substring(0, separator));
        var entries = new java.util.ArrayList<MaterialBatch>();
        for (String entry : code.substring(separator + 1).split("\\$", -1)) {
            int batchStart = entry.indexOf('#');
            if (batchStart <= 0 || entry.indexOf('#', batchStart + 1) >= 0) throw invalid();
            String material = decode(entry.substring(0, batchStart));
            String rawBatch = entry.substring(batchStart + 1);
            String batch = null;
            if (!rawBatch.equals("~")) batch = decode(rawBatch);
            entries.add(new MaterialBatch(material, batch));
        }
        // Validate fields and size through the same boundary used for generation.
        generate(order, entries);
        return new Content(order, entries.stream().distinct().sorted(ORDER).toList());
    }

    public static String normalize(String code) {
        var content = parse(code);
        return generate(content.workorderCode(), content.materials());
    }

    public static BitMatrix matrix(String code) {
        var content = parse(code);
        if (content.materials().stream().map(MaterialBatch::materialCode).distinct().count()
                != content.materials().size()) {
            org.slf4j.LoggerFactory.getLogger(ProductionQrCode.class).error(
                    "event=production.qr.split_batch_print_rejected workorder={} action=replenish_or_merge_batches",
                    content.workorderCode());
            throw new FoundationException(ErrorCode.PRODUCTION_COMBINATION_SPLIT_BATCH_REQUIRED);
        }
        String normalized = generate(content.workorderCode(), content.materials());
        try {
            return new QRCodeWriter().encode(normalized, BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name()));
        } catch (WriterException failure) {
            throw new FoundationException(ErrorCode.EXECUTION_QR_INVALID, failure);
        }
    }

    public static String svg(String code) {
        var matrix = matrix(code);
        var svg = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ");
        svg.append(matrix.getWidth()).append(' ').append(matrix.getHeight())
                .append("\" shape-rendering=\"crispEdges\"><rect width=\"100%\" height=\"100%\" fill=\"white\"/><path fill=\"black\" d=\"");
        for (int y = 0; y < matrix.getHeight(); y++) {
            for (int x = 0; x < matrix.getWidth(); x++) {
                if (matrix.get(x, y)) svg.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
            }
        }
        return svg.append("\"/></svg>").toString();
    }

    private static void text(String value, int maximum, boolean emptyAllowed) {
        if (value == null || (!emptyAllowed && value.isBlank())
                || value.codePointCount(0, value.length()) > maximum
                || value.codePoints().anyMatch(Character::isISOControl)
                || !StandardCharsets.UTF_8.newEncoder().canEncode(value)) throw invalid();
    }

    private static String encode(String value) {
        return value.replace("%", "%25").replace(":", "%3A").replace("#", "%23")
                .replace("$", "%24").replace("~", "%7E")
                .replace("/", "%2F").replace("\\", "%5C").replace("?", "%3F");
    }

    private static String decode(String value) {
        var result = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current != '%') {
                if (current == ':' || current == '#' || current == '$' || current == '~') throw invalid();
                result.append(current);
                continue;
            }
            if (i + 2 >= value.length()) throw invalid();
            String escaped = value.substring(i + 1, i + 3);
            result.append(switch (escaped) {
                case "25" -> '%';
                case "3A" -> ':';
                case "23" -> '#';
                case "24" -> '$';
                case "7E" -> '~';
                case "2F" -> '/';
                case "5C" -> '\\';
                case "3F" -> '?';
                default -> throw invalid();
            });
            i += 2;
        }
        return result.toString();
    }

    private static FoundationException invalid() {
        return new FoundationException(ErrorCode.EXECUTION_QR_INVALID);
    }
}
