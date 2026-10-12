package dev.mesbase.modules.material.sync;

import dev.mesbase.modules.material.sync.ExternalMaterial;
import dev.mesbase.modules.material.sync.MaterialQueryPort;
import dev.mesbase.core.factory.FactoryScope;
import java.util.List;

/**
 * TEST ONLY: typed source data. No database access; ERP-only fields are ignored at the adapter boundary.
 */
public class TestMaterialQueryAdapter implements MaterialQueryPort {
    public List<ExternalMaterial> query(FactoryScope scope, int maximum) {
        return List.of(new ExternalMaterial("M001", "测试物料一", "TEST ONLY", null, "PCS", "ITEM", "ENABLED", "TEST-001", "ERP-M001"),
                new ExternalMaterial("M002", "测试物料二", null, null, "KG", "PRODUCT", "ENABLED", "TEST-002", "ERP-M002"));
    }
}
