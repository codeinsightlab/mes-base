package dev.issuefixture;

import dev.mesbase.acceptance.AcceptanceBusinessDatabase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.yaml.snakeyaml.Yaml;

/**
 * TEST ONLY: read-only pre-enablement scan. Never changes historical facts.
 */
@EnabledIfEnvironmentVariable(named = "MES_QUANTITY_AUDIT_MYSQL", matches = "true")
class ProductionQualityQuantityAuditMySqlTest {
    @Test
    void scanExistingFacts() throws Exception {
        var jdbc = new JdbcTemplate(AcceptanceBusinessDatabase.source());
        var result = new LinkedHashMap<String, Object>();
        result.put("target", "SIMPLE_B / 192.168.18.25:3306/mes_base_dev_business_simple_b");
        Map<?, ?> config = new Yaml().load(Files.readString(Path.of("mes-admin/src/main/resources/application-dev.yml")));
        var platform = (Map<?, ?>) ((Map<?, ?>) config.get("mes")).get("platform");
        var center = new JdbcTemplate(new DriverManagerDataSource((String) platform.get("url"), (String) platform.get("username"), (String) platform.get("password")));
        result.put("binding", center.queryForMap("SELECT f.factory_id,d.datasource_id,d.db_host,d.db_port,d.db_name,f.status factory_status,d.status datasource_status FROM mf_factory f JOIN mf_datasource d ON d.datasource_id=f.datasource_id WHERE f.factory_id='SIMPLE_B'"));
        String latest = "WITH latest AS (SELECT d.* FROM mes_production_defect d WHERE NOT EXISTS (SELECT 1 FROM mes_production_defect n WHERE n.declaration_id=d.declaration_id AND n.revision_no>d.revision_no)), "
                + "q AS (SELECT execution_id,affected_node_id,SUM(defect_qty) d FROM latest WHERE record_action<>'VOID' GROUP BY execution_id,affected_node_id), "
                + "r AS (SELECT execution_id,routing_node_id,SUM(quantity_delta) r FROM mes_production_report GROUP BY execution_id,routing_node_id) ";
        result.put("defectAboveReport", jdbc.queryForList(latest + "SELECT q.*,COALESCE(r.r,0) reported FROM q LEFT JOIN r ON r.execution_id=q.execution_id AND r.routing_node_id=q.affected_node_id WHERE q.d>COALESCE(r.r,0)"));
        result.put("downstreamAboveEffectiveOutput", jdbc.queryForList(latest + "SELECT p.execution_id,p.routing_node_id upstream_node,n.routing_node_id downstream_node,COALESCE(r.r,0)-COALESCE(q.d,0) effective_output,COALESCE(nr.r,0) downstream_reported FROM mes_execution_path_node p JOIN mes_execution_path_node n ON n.execution_id=p.execution_id AND n.sequence_no=p.sequence_no+1 LEFT JOIN r ON r.execution_id=p.execution_id AND r.routing_node_id=p.routing_node_id LEFT JOIN q ON q.execution_id=p.execution_id AND q.affected_node_id=p.routing_node_id LEFT JOIN r nr ON nr.execution_id=n.execution_id AND nr.routing_node_id=n.routing_node_id WHERE COALESCE(nr.r,0)>COALESCE(r.r,0)-COALESCE(q.d,0)"));
        result.put("qualityIdentityOrPathMismatch", jdbc.queryForList("SELECT d.id,d.execution_id,d.affected_node_id,d.discovered_node_id,d.responsible_node_id,d.target_node_id FROM mes_production_defect d LEFT JOIN mes_production_execution e ON e.id=d.execution_id WHERE e.id IS NULL OR EXISTS (SELECT 1 FROM (SELECT 1) z WHERE NOT EXISTS (SELECT 1 FROM mes_execution_path_node p JOIN routing_node n ON n.id=p.routing_node_id AND n.routing_id=e.routing_id WHERE p.execution_id=d.execution_id AND p.routing_node_id=d.affected_node_id)) OR NOT EXISTS (SELECT 1 FROM mes_execution_path_node p WHERE p.execution_id=d.execution_id AND p.routing_node_id=d.discovered_node_id) OR (d.responsible_node_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM mes_execution_path_node p WHERE p.execution_id=d.execution_id AND p.routing_node_id=d.responsible_node_id)) OR (d.target_node_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM mes_execution_path_node p WHERE p.execution_id=d.execution_id AND p.routing_node_id=d.target_node_id))"));
        result.put("possibleDuplicateActiveDeclarations", jdbc.queryForList(latest + "SELECT execution_id,affected_node_id,discovered_node_id,defect_qty,occurred_at,COUNT(*) declarations FROM latest WHERE record_action<>'VOID' GROUP BY execution_id,affected_node_id,discovered_node_id,defect_qty,occurred_at HAVING COUNT(*)>1"));
        result.put("reportOutsideActualPath", jdbc.queryForList("SELECT DISTINCT r.execution_id,r.routing_node_id FROM mes_production_report r WHERE NOT EXISTS (SELECT 1 FROM mes_execution_path_node p WHERE p.execution_id=r.execution_id AND p.routing_node_id=r.routing_node_id)"));
        result.put("reportBeyondTargetOrNegative", jdbc.queryForList(latest + "SELECT r.*,e.target_qty FROM r JOIN mes_production_execution e ON e.id=r.execution_id WHERE r.r<0 OR r.r>e.target_qty"));
        result.put("activeDeclarations", jdbc.queryForObject(latest + "SELECT COUNT(*) FROM latest WHERE record_action<>'VOID'", Long.class));
        result.put("reportMeaning", "UNKNOWN: existing rows cannot prove gross processed quantity versus good-only reporting; requires business confirmation before live enablement");
        result.put("counts", jdbc.queryForMap("SELECT (SELECT COUNT(*) FROM mes_production_report) report_rows,(SELECT COUNT(*) FROM mes_production_defect) quality_versions"));
        var dir = Path.of(".local/production-quality-phase2b-20261016");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("historical-audit-after.yml"), new Yaml().dump(result));
        result.forEach((key, value) -> System.out.println("QUANTITY_AUDIT " + key + "=" + (value instanceof List<?> rows ? rows.size() : key.equals("counts") ? value : "saved")));
    }
}
