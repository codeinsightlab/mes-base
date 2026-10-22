package dev.mesbase.business.legacy.dao;

import dev.mesbase.business.legacy.domain.LegacyHistory.ExecutionNode;
import dev.mesbase.business.legacy.domain.LegacyHistory.Input;
import dev.mesbase.business.legacy.domain.LegacyHistory.Mapping;
import dev.mesbase.business.legacy.domain.LegacyHistory.Node;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Read-only access to frozen evidence retained after the one-time import was retired.
 */
public interface LegacyHistoryMapper {
    // Original status 0/1 is an external scrap-conversion code, not a Base business enum.
    String PENDING_LOSS = "CASE WHEN b.status=#{completed} THEN 0 ELSE COALESCE((SELECT SUM(CAST(JSON_UNQUOTE(JSON_EXTRACT(q.source_payload,'$.quantity')) AS DECIMAL(20,6))) FROM mes_legacy_source_mapping q WHERE q.parent_id=e.id AND q.entity_kind='QUALITY_EVIDENCE' AND JSON_UNQUOTE(JSON_EXTRACT(q.source_payload,'$.process_id'))=SUBSTRING_INDEX(n.source_pk,'/',-1) AND (COALESCE(JSON_UNQUOTE(JSON_EXTRACT(q.source_payload,'$.status')),'') &lt;&gt; '1' OR COALESCE(JSON_UNQUOTE(JSON_EXTRACT(q.source_payload,'$.scrap_code')),'')='')),0) END unverified_loss_qty ";

    record CardCount(String sourceKey, long reportCount, long nodeCount) {
    }

    @Select("SELECT e.source_pk source_key,COUNT(r.id) report_count,COUNT(DISTINCT n.id) node_count FROM mes_legacy_source_mapping e LEFT JOIN mes_legacy_source_mapping n ON n.parent_id=e.id AND n.entity_kind='NODE' LEFT JOIN mes_legacy_source_mapping r ON r.parent_id=n.id AND r.entity_kind='REPORT' WHERE e.parent_id=#{id} AND e.entity_kind='EXECUTION' GROUP BY e.source_pk")
    List<CardCount> cardCounts(@Param("id") String id);

    @Select("SELECT id,batch_id,parent_id,source_table,source_pk,source_hash,rule_version,entity_kind,entity_id,source_payload,conversion_payload,created_at FROM mes_legacy_source_mapping WHERE entity_kind='WORKORDER' AND entity_id=#{id}")
    Mapping order(@Param("id") Long id);

    @Select("SELECT id,batch_id,parent_id,source_table,source_pk,source_hash,rule_version,entity_kind,entity_id,source_payload,conversion_payload,created_at FROM mes_legacy_source_mapping WHERE parent_id=#{id} AND entity_kind='EXECUTION'")
    List<Mapping> orderExecutions(@Param("id") String id);

    @Select("SELECT CAST(JSON_UNQUOTE(JSON_EXTRACT(e.conversion_payload,'$.inputQty')) AS DECIMAL(20,6)) "
            + "FROM mes_legacy_source_mapping e JOIN mes_legacy_source_mapping a ON a.parent_id=e.id "
            + "AND a.entity_kind='PRODUCTION_ACTIVATION' AND a.entity_id=e.entity_id AND a.source_hash=e.source_hash "
            + "AND a.rule_version='internal-production-activation-v1' WHERE e.entity_kind='EXECUTION' AND e.entity_id=#{id}")
    BigDecimal activatedInput(@Param("id") Long executionId);

    @Select("SELECT CAST(JSON_UNQUOTE(JSON_EXTRACT(conversion_payload,'$.inputQty')) AS DECIMAL(20,6)) "
            + "FROM mes_legacy_source_mapping WHERE entity_kind='EXECUTION' AND entity_id=#{id}")
    BigDecimal input(@Param("id") Long executionId);

    @Select({"<script>SELECT entity_id execution_id, CAST(JSON_UNQUOTE(JSON_EXTRACT(conversion_payload,'$.inputQty')) AS DECIMAL(20,6)) quantity ",
            "FROM mes_legacy_source_mapping WHERE entity_kind='EXECUTION' AND entity_id IN ",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>"})
    List<Input> inputs(@Param("ids") List<Long> ids);

    @Select("<script><bind name=\"completed\" value=\"@dev.mesbase.business.legacy.domain.LegacyBatchStatus@COMPLETED.getValue()\"/>SELECT n.entity_id routing_node_id,"
            + "CAST(JSON_UNQUOTE(JSON_EXTRACT(n.conversion_payload,'$.legacyNet')) AS DECIMAL(20,6)) legacy_net,"
            + "CASE WHEN b.status=#{completed} THEN CAST(JSON_UNQUOTE(JSON_EXTRACT(c.conversion_payload,'$.grossDelta')) AS DECIMAL(20,6)) ELSE CAST(JSON_UNQUOTE(JSON_EXTRACT(n.conversion_payload,'$.grossCorrection')) AS DECIMAL(20,6)) END gross_correction,"
            + "CASE WHEN b.status=#{completed} THEN 0 ELSE CAST(JSON_UNQUOTE(JSON_EXTRACT(n.conversion_payload,'$.effectiveDefect')) AS DECIMAL(20,6)) END effective_defect,"
            + "n.conversion_payload evidence," + PENDING_LOSS + " FROM mes_legacy_source_mapping n JOIN mes_legacy_source_mapping e "
            + "ON e.id=n.parent_id "
            + "LEFT JOIN mes_legacy_source_mapping c ON c.id=SHA2(CONCAT('legacy-quality-formal-v1:',n.id),256) AND c.parent_id=n.id AND c.entity_kind='NODE_QUALITY_CONVERSION' "
            + "AND c.rule_version='legacy-quality-formal-v1' AND c.source_hash=n.source_hash "
            + "LEFT JOIN mes_legacy_migration_batch b ON b.id=c.batch_id AND b.rule_version=c.rule_version AND JSON_CONTAINS(b.approved_scope,JSON_QUOTE(c.id)) "
            + "WHERE e.entity_kind='EXECUTION' AND e.entity_id=#{id} AND n.entity_kind='NODE'</script>")
    List<Node> nodes(@Param("id") Long executionId);

    @Select({"<script><bind name=\"completed\" value=\"@dev.mesbase.business.legacy.domain.LegacyBatchStatus@COMPLETED.getValue()\"/>SELECT e.entity_id execution_id,n.entity_id routing_node_id,",
            "CAST(JSON_UNQUOTE(JSON_EXTRACT(n.conversion_payload,'$.legacyNet')) AS DECIMAL(20,6)) legacy_net,",
            "CASE WHEN b.status=#{completed} THEN CAST(JSON_UNQUOTE(JSON_EXTRACT(c.conversion_payload,'$.grossDelta')) AS DECIMAL(20,6)) ELSE CAST(JSON_UNQUOTE(JSON_EXTRACT(n.conversion_payload,'$.grossCorrection')) AS DECIMAL(20,6)) END gross_correction,",
            "CASE WHEN b.status=#{completed} THEN 0 ELSE CAST(JSON_UNQUOTE(JSON_EXTRACT(n.conversion_payload,'$.effectiveDefect')) AS DECIMAL(20,6)) END effective_defect,", PENDING_LOSS,
            "FROM mes_legacy_source_mapping n JOIN mes_legacy_source_mapping e ON e.id=n.parent_id ",
            "LEFT JOIN mes_legacy_source_mapping c ON c.id=SHA2(CONCAT('legacy-quality-formal-v1:',n.id),256) AND c.parent_id=n.id AND c.entity_kind='NODE_QUALITY_CONVERSION' ",
            "AND c.rule_version='legacy-quality-formal-v1' AND c.source_hash=n.source_hash ",
            "LEFT JOIN mes_legacy_migration_batch b ON b.id=c.batch_id AND b.rule_version=c.rule_version AND JSON_CONTAINS(b.approved_scope,JSON_QUOTE(c.id)) ",
            "WHERE e.entity_kind='EXECUTION' AND n.entity_kind='NODE' AND e.entity_id IN ",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>"})
    List<ExecutionNode> nodesBatch(@Param("ids") List<Long> executionIds);

    @Select("SELECT m.id,m.batch_id,m.parent_id,m.source_table,m.source_pk,m.source_hash,m.rule_version,"
            + "m.entity_kind,m.entity_id,m.source_payload,m.conversion_payload,m.created_at "
            + "FROM mes_legacy_source_mapping m WHERE m.entity_kind='EXECUTION' AND m.entity_id=#{id}")
    Mapping execution(@Param("id") Long executionId);
}
