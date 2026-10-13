package dev.mesbase.business.basic.sync;

import dev.mesbase.business.basic.sync.ExternalMaterial;
import dev.mesbase.business.basic.sync.MaterialQueryPort;
import dev.mesbase.core.factory.FactoryScope;
import java.util.List;

/**
 * TEST ONLY: typed source data. No database access; ERP-only fields are ignored at the adapter boundary.
 */
public class TestMaterialQueryAdapter implements MaterialQueryPort {
    public List<ExternalMaterial> query(FactoryScope scope, int maximum) {
        return List.of(new ExternalMaterial("M001", "测试物料一", "TEST ONLY", null, 1L, "ITEM", "ENABLED", "TEST-001", "ERP-M001"),
                new ExternalMaterial("M002", "测试物料二", null, null, 2L, "PRODUCT", "ENABLED", "TEST-002", "ERP-M002"));
    }
}
