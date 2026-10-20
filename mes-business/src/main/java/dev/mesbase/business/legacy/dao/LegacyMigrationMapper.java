package dev.mesbase.business.legacy.dao;

import dev.mesbase.business.legacy.domain.LegacyHistory.Batch;
import dev.mesbase.business.legacy.domain.LegacyHistory.Mapping;
import dev.mesbase.business.legacy.domain.LegacyHistory.MappingIdentity;
import dev.mesbase.business.legacy.domain.LegacyHistory.Node;
import java.math.BigDecimal;
import dev.mesbase.business.legacy.domain.LegacyHistory.Input;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface LegacyMigrationMapper {
    @Insert("INSERT INTO mes_legacy_source_lock(id) VALUES(#{id}) ON DUPLICATE KEY UPDATE id=id")
    int ensureLock(@Param("id") String id);

    @Select("SELECT id FROM mes_legacy_source_lock WHERE id=#{id} FOR UPDATE")
    String lock(@Param("id") String id);

    @Select("SELECT COALESCE(SUM(CAST(JSON_UNQUOTE(JSON_EXTRACT(conversion_payload,'$.inputQty')) AS DECIMAL(20,6))),0) FROM mes_legacy_source_mapping WHERE parent_id=#{id} AND entity_kind='EXECUTION'")
    BigDecimal orderInputs(@Param("id") String orderMappingId);

    record CardCount(String sourceKey, long reportCount, long nodeCount) {
    }

    @Select("SELECT e.source_pk source_key,COUNT(r.id) report_count,COUNT(DISTINCT n.id) node_count FROM mes_legacy_source_mapping e LEFT JOIN mes_legacy_source_mapping n ON n.parent_id=e.id AND n.entity_kind='NODE' LEFT JOIN mes_legacy_source_mapping r ON r.parent_id=n.id AND r.entity_kind='REPORT' WHERE e.parent_id=#{id} AND e.entity_kind='EXECUTION' GROUP BY e.source_pk")
    List<CardCount> cardCounts(@Param("id") String id);

    @Select("SELECT id,batch_id,parent_id,source_table,source_pk,source_hash,rule_version,entity_kind,entity_id,source_payload,conversion_payload,created_at FROM mes_legacy_source_mapping WHERE entity_kind='WORKORDER' AND entity_id=#{id}")
    Mapping order(@Param("id") Long id);

    @Select("SELECT id,batch_id,parent_id,source_table,source_pk,source_hash,rule_version,entity_kind,entity_id,source_payload,conversion_payload,created_at FROM mes_legacy_source_mapping WHERE parent_id=#{id} AND entity_kind='EXECUTION'")
    List<Mapping> orderExecutions(@Param("id") String id);

    @Select("SELECT id,batch_id,parent_id,source_table,source_pk,source_hash,rule_version,entity_kind,entity_id,"
            + "source_payload,conversion_payload,created_at FROM mes_legacy_source_mapping WHERE id=#{id}")
    Mapping find(@Param("id") String id);

    @Select({"<script>SELECT id,parent_id,source_hash,rule_version,entity_id FROM mes_legacy_source_mapping WHERE id IN ",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>"})
    List<MappingIdentity> findIdentities(@Param("ids") List<String> ids);

    @Insert({"<script>INSERT INTO mes_legacy_source_mapping(id,batch_id,parent_id,source_table,source_pk,source_hash,",
            "rule_version,entity_kind,entity_id,source_payload,conversion_payload,created_at) VALUES ",
            "<foreach collection='rows' item='r' separator=','>",
            "(#{r.id},#{r.batchId},#{r.parentId},#{r.sourceTable},#{r.sourcePk},#{r.sourceHash},#{r.ruleVersion},",
            "#{r.entityKind},#{r.entityId},#{r.sourcePayload},#{r.conversionPayload},#{r.createdAt})</foreach></script>"})
    int insertBatch(@Param("rows") List<Mapping> rows);

    @Insert("INSERT INTO mes_legacy_source_mapping(id,batch_id,parent_id,source_table,source_pk,source_hash,"
            + "rule_version,entity_kind,entity_id,source_payload,conversion_payload,created_at) "
            + "VALUES(#{id},#{batchId},#{parentId},#{sourceTable},#{sourcePk},#{sourceHash},#{ruleVersion},"
            + "#{entityKind},#{entityId},#{sourcePayload},#{conversionPayload},#{createdAt})")
    int insert(Mapping row);

    @Select("SELECT id,rule_version,snapshot_hash,status,approved_scope,operator_user_id,created_at,finished_at "
            + "FROM mes_legacy_migration_batch WHERE id=#{id} FOR UPDATE")
    Batch batch(@Param("id") String id);

    @Insert("INSERT INTO mes_legacy_migration_batch(id,rule_version,snapshot_hash,status,approved_scope,"
            + "operator_user_id,created_at,finished_at) VALUES(#{id},#{ruleVersion},#{snapshotHash},#{status},"
            + "#{approvedScope},#{operatorUserId},#{createdAt},#{finishedAt}) ON DUPLICATE KEY UPDATE id=id")
    int prepare(Batch batch);

    @Update("UPDATE mes_legacy_migration_batch SET status=#{next},finished_at=CASE WHEN #{next}=#{completed} "
            + "THEN NOW(6) ELSE NULL END WHERE id=#{id} AND status=#{expected}")
    int transition(@Param("id") String id, @Param("expected") String expected, @Param("next") String next,
                   @Param("completed") String completed);

    @Select("SELECT CAST(JSON_UNQUOTE(JSON_EXTRACT(conversion_payload,'$.inputQty')) AS DECIMAL(20,6)) "
            + "FROM mes_legacy_source_mapping WHERE entity_kind='EXECUTION' AND entity_id=#{id}")
    BigDecimal input(@Param("id") Long executionId);

    @Select({"<script>SELECT entity_id execution_id, CAST(JSON_UNQUOTE(JSON_EXTRACT(conversion_payload,'$.inputQty')) AS DECIMAL(20,6)) quantity ",
            "FROM mes_legacy_source_mapping WHERE entity_kind='EXECUTION' AND entity_id IN ",
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>"})
    List<Input> inputs(@Param("ids") List<Long> ids);

    @Select("SELECT n.entity_id routing_node_id,"
            + "CAST(JSON_UNQUOTE(JSON_EXTRACT(n.conversion_payload,'$.legacyNet')) AS DECIMAL(20,6)) legacy_net,"
            + "CAST(JSON_UNQUOTE(JSON_EXTRACT(n.conversion_payload,'$.grossCorrection')) AS DECIMAL(20,6)) gross_correction,"
            + "CAST(JSON_UNQUOTE(JSON_EXTRACT(n.conversion_payload,'$.effectiveDefect')) AS DECIMAL(20,6)) effective_defect,"
            + "n.conversion_payload evidence FROM mes_legacy_source_mapping n JOIN mes_legacy_source_mapping e "
            + "ON e.id=n.parent_id WHERE e.entity_kind='EXECUTION' AND e.entity_id=#{id} AND n.entity_kind='NODE'")
    List<Node> nodes(@Param("id") Long executionId);

    @Select("SELECT m.id,m.batch_id,m.parent_id,m.source_table,m.source_pk,m.source_hash,m.rule_version,"
            + "m.entity_kind,m.entity_id,m.source_payload,m.conversion_payload,m.created_at "
            + "FROM mes_legacy_source_mapping m WHERE m.entity_kind='EXECUTION' AND m.entity_id=#{id}")
    Mapping execution(@Param("id") Long executionId);
}
