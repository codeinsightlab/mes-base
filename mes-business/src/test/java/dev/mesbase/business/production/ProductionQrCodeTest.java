package dev.mesbase.business.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import dev.mesbase.business.production.domain.ProductionQrCode;
import dev.mesbase.business.production.domain.ProductionQrCode.MaterialBatch;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductionQrCodeTest {
    @Test
    void identicalSetsHaveIdenticalCodesRegardlessOfInputOrder() {
        var a = new MaterialBatch("A", "0001");
        var b = new MaterialBatch("B", "Lot2");
        assertEquals("WO:A#0001$B#Lot2", ProductionQrCode.generate("WO", List.of(b, a, b)));
        assertEquals(ProductionQrCode.generate("WO", List.of(a, b)),
                ProductionQrCode.normalize("WO:B#Lot2$A#0001"));
        assertNotEquals(ProductionQrCode.generate("WO", List.of(a)),
                ProductionQrCode.generate("WO", List.of(new MaterialBatch("A", "0001 "))));
    }

    @Test
    void preservesRawBatchTextAndReservedDelimitersWithoutAmbiguity() {
        var items = List.of(new MaterialBatch("物料#$", "000批:$#%~ "),
                new MaterialBatch("A", null), new MaterialBatch("A", ""), new MaterialBatch("A", "~"));
        String code = ProductionQrCode.generate("工:单", items);
        var parsed = ProductionQrCode.parse(code);
        assertEquals("工:单", parsed.workorderCode());
        assertEquals(4, parsed.materials().size());
        assertEquals(code, ProductionQrCode.generate(parsed.workorderCode(), parsed.materials()));
        assertEquals(code, ProductionQrCode.normalize(code));
        assertEquals("WO:A#~", ProductionQrCode.generate("WO", List.of(new MaterialBatch("A", null))));
        assertEquals("WO:A#%7E", ProductionQrCode.generate("WO", List.of(new MaterialBatch("A", "~"))));
    }

    @Test
    void rejectsMalformedAndOversizedPayloads() {
        for (String code : List.of("", "COMB:F:1", "WO:A", "WO:A#1$", "WO:A#x#y", "WO:A#%00", "WO:A#%24x%")) {
            assertThrows(RuntimeException.class, () -> ProductionQrCode.parse(code), code);
        }
        assertThrows(RuntimeException.class, () -> ProductionQrCode.generate("WO",
                List.of(new MaterialBatch("A", "a".repeat(129)))));
    }

    @Test
    void generatedQrCanBeDecodedBackToTheExactUnicodeCode() throws Exception {
        String code = ProductionQrCode.generate("工单001", List.of(new MaterialBatch("物料A", "批次0001 ")));
        var matrix = ProductionQrCode.matrix(code);
        int scale = 8;
        int width = matrix.getWidth() * scale;
        int height = matrix.getHeight() * scale;
        int[] pixels = new int[width * height];
        Arrays.fill(pixels, 0xFFFFFF);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (matrix.get(x / scale, y / scale)) pixels[y * width + x] = 0;
            }
        }
        var bitmap = new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(width, height, pixels)));
        assertEquals(code, new MultiFormatReader().decode(bitmap).getText());
    }
}
