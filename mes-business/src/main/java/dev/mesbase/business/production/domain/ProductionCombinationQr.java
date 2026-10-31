package dev.mesbase.business.production.domain;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

/**
 * Encodes only a validated combination identifier; the SVG contains no user text or external resources.
 */
public final class ProductionCombinationQr {
    private ProductionCombinationQr() {
    }

    public static BitMatrix matrix(String code) {
        int delimiter = code.lastIndexOf(':');
        if (delimiter <= 5) throw new IllegalArgumentException("Invalid combination identifier");
        ProductionCombinationRules.parse(code, code.substring(5, delimiter));
        try {
            return new QRCodeWriter().encode(code, BarcodeFormat.QR_CODE, 0, 0);
        } catch (WriterException exception) {
            throw new IllegalStateException("Cannot encode combination identifier", exception);
        }
    }

    public static String svg(String code) {
        var matrix = matrix(code);
        var svg = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ");
        svg.append(matrix.getWidth()).append(' ').append(matrix.getHeight())
                .append("\" shape-rendering=\"crispEdges\"><rect width=\"100%\" height=\"100%\" fill=\"white\"/><path fill=\"black\" d=\"");
        for (int y = 0; y < matrix.getHeight(); y++)
            for (int x = 0; x < matrix.getWidth(); x++)
                if (matrix.get(x, y)) svg.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
        return svg.append("\"/></svg>").toString();
    }
}
