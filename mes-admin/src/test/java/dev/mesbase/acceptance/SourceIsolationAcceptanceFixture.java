package dev.mesbase.acceptance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;

/**
 * TEST ONLY negative-source fixture on the verified acceptance DB. No ERP connector or production endpoint.
 * Synthetic EXTERNAL rows intentionally lack a usable ERP mapping. They prove HTTP rejection only.
 */
public final class SourceIsolationAcceptanceFixture {
    private static final Path FILE = Path.of(".local/source-v17-20261017/negative-fixture.json");

    public static void main(String[] args) throws Exception {
        try (var c = AcceptanceBusinessDatabase.source().getConnection()) {
            c.setAutoCommit(false);
            try {
                if ("setup".equals(args[0])) {
                    if (Files.exists(FILE))
                        throw new IllegalStateException("Fixture already recorded; inspect before retry");
                    String tag = args[1];
                    if (!tag.startsWith("TEST_V17_")) throw new IllegalArgumentException("TEST tag required");
                    long warehouse = insert(c, "INSERT INTO wm_warehouse(code,name,status,source,remark) VALUES(?,?,'ENABLED','EXTERNAL',?)",
                            tag + "_EXTERNAL_NEGATIVE", "TEST ONLY 外部来源拒绝夹具", "合成HTTP负例，非真实ERP同步或权威库存");
                    long inventory = insert(c, "INSERT INTO wm_inventory(material_id,warehouse_id,quantity,available_quantity,status,batch_no,bin_code,remark) VALUES(?,?,0,0,'ENABLED',?,'TEST_ONLY',?)",
                            Long.valueOf(args[2]), warehouse, tag + "_MIRROR", "合成外部镜像零余额，仅验证LOCAL Owner拒绝");
                    long order = insert(c, "INSERT INTO workorder(code,product_id,planned_qty,routing_id,status,revision,source,remark,create_time,update_time) VALUES(?,?,10,?,'RELEASED',0,'EXTERNAL',?,NOW(),NOW())",
                            tag + "_EXTERNAL_NEGATIVE", Long.valueOf(args[3]), Long.valueOf(args[4]), "TEST ONLY 来源负例，非ERP工单同步证明");
                    try (var p = c.prepareStatement("INSERT INTO workorder_erp_extra(id,external_id,provider) VALUES(?,'TEST_ONLY_UNVERIFIED','TEST_ONLY_SOURCE_NEGATIVE')")) {
                        p.setLong(1, order);
                        p.executeUpdate();
                    }
                    c.commit();
                    Files.writeString(FILE, new ObjectMapper().writeValueAsString(Map.of("tag", tag, "warehouseId", warehouse, "inventoryId", inventory, "workorderId", order)));
                    System.out.println("Synthetic EXTERNAL rejection fixture recorded; no ERP capability claimed");
                } else if ("cleanup".equals(args[0])) {
                    var n = new ObjectMapper().readTree(Files.readString(FILE));
                    long w = n.get("warehouseId").asLong(), stock = n.get("inventoryId")
                            .asLong(), order = n.get("workorderId").asLong();
                    String tag = n.get("tag").asText();
                    check(c, "SELECT COUNT(*) FROM wm_warehouse WHERE id=? AND code=?", 1, w, tag + "_EXTERNAL_NEGATIVE");
                    check(c, "SELECT COUNT(*) FROM workorder WHERE id=? AND code=?", 1, order, tag + "_EXTERNAL_NEGATIVE");
                    check(c, "SELECT COUNT(*) FROM wm_stock_action WHERE stock_id=?", 0, stock);
                    check(c, "SELECT COUNT(*) FROM mes_material_issue WHERE workorder_id=?", 0, order);
                    check(c, "SELECT COUNT(*) FROM mes_production_execution WHERE workorder_id=?", 0, order);
                    check(c, "SELECT COUNT(*) FROM mes_material_issue_item WHERE warehouse_id=?", 0, w);
                    check(c, "SELECT COUNT(*) FROM mes_production_receipt WHERE warehouse_id=?", 0, w);
                    for (var operation : Map.of("workorder_erp_extra", order, "workorder", order, "wm_inventory", stock, "wm_warehouse", w)
                            .entrySet()) {
                        try (var p = c.prepareStatement("DELETE FROM " + operation.getKey() + " WHERE id=?")) {
                            p.setLong(1, operation.getValue());
                            p.executeUpdate();
                        }
                    }
                    c.commit();
                    System.out.println("Only own unreferenced negative-source fixture removed; original records/facts preserved");
                } else throw new IllegalArgumentException("Expected setup/cleanup");
            } catch (Exception failure) {
                c.rollback();
                throw failure;
            }
        }
    }

    private static long insert(Connection c, String sql, Object... values) throws Exception {
        try (var p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < values.length; i++) p.setObject(i + 1, values[i]);
            p.executeUpdate();
            try (var keys = p.getGeneratedKeys()) {
                if (!keys.next()) throw new IllegalStateException("Missing fixture ID");
                return keys.getLong(1);
            }
        }
    }

    private static void check(Connection c, String sql, int expected, Object... values) throws Exception {
        try (var p = c.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) p.setObject(i + 1, values[i]);
            try (var r = p.executeQuery()) {
                r.next();
                if (r.getInt(1) != expected)
                    throw new IllegalStateException("Fixture is referenced or changed; retain it for inspection");
            }
        }
    }
}
