package dev.mesbase.platform.codegen;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaSourceImportsTest {
    @Test void generatedTypesUseExplicitImports() {
        String result=JavaSourceImports.explicit("public class Probe { java.util.List<java.math.BigDecimal> values; }");
        assertTrue(result.contains("import java.util.List;"));
        assertTrue(result.contains("import java.math.BigDecimal;"));
        assertTrue(result.contains("List<BigDecimal> values"));
    }
    @Test void classNameStringsAndSqlRemainLiteral() {
        String result=JavaSourceImports.explicit("public class Probe { String sql=\"SELECT dbo.Customer FROM dbo.TableName\"; String type=\"java.util.List\"; }");
        assertTrue(result.contains("\"SELECT dbo.Customer FROM dbo.TableName\""));
        assertTrue(result.contains("\"java.util.List\""));
        assertFalse(result.contains("import dbo."));
    }
    @Test void actualSimpleNameConflictsKeepOneQualifiedType() {
        String result=JavaSourceImports.explicit("public class Probe { java.util.Date first; java.sql.Date second; }");
        assertTrue(result.contains("import java.util.Date;"));
        assertTrue(result.contains("Date first; java.sql.Date second;"));
        assertFalse(result.contains("import java.sql.Date;"));
    }
}
