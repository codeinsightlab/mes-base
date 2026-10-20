package dev.mesbase.business.legacy.service;

import dev.mesbase.business.legacy.domain.LegacyBatchStatus;
import dev.mesbase.business.production.domain.WorkOrderStatus;
import dev.mesbase.business.production.domain.ProductionExecutionStatus;
import dev.mesbase.business.production.domain.ExecutionPathStatus;
import dev.mesbase.business.production.domain.RoutingStatus;
import dev.mesbase.business.production.domain.ReportRecordType;
import dev.mesbase.business.shared.enums.EnableStatus;
import dev.mesbase.business.shared.enums.BusinessSource;
import dev.mesbase.business.basic.domain.MaterialType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.basic.dao.UnitMeasureMapper;
import dev.mesbase.business.basic.domain.MaterialEntity;
import dev.mesbase.business.basic.domain.MaterialExample;
import dev.mesbase.business.basic.domain.UnitMeasureEntity;
import dev.mesbase.business.basic.domain.UnitMeasureExample;
import dev.mesbase.business.legacy.dao.LegacyMigrationMapper;
import dev.mesbase.business.legacy.domain.LegacyHistory.Batch;
import dev.mesbase.business.legacy.domain.LegacyHistory.Mapping;
import dev.mesbase.business.legacy.domain.LegacyHistory.ExecutionEvidence;
import dev.mesbase.business.legacy.domain.LegacyImportCommand;
import dev.mesbase.business.production.dao.MesExecutionPathMapper;
import dev.mesbase.business.production.dao.MesExecutionPathNodeMapper;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.MesProductionReportMapper;
import dev.mesbase.business.production.dao.OperationMapper;
import dev.mesbase.business.production.dao.RoutingMapper;
import dev.mesbase.business.production.dao.RoutingNodeMapper;
import dev.mesbase.business.production.dao.RoutingEdgeMapper;
import dev.mesbase.business.production.dao.RoutingProductMapper;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.domain.MesExecutionPathEntity;
import dev.mesbase.business.production.domain.MesExecutionPathNodeEntity;
import dev.mesbase.business.production.domain.MesProductionExecutionEntity;
import dev.mesbase.business.production.domain.MesProductionReportEntity;
import dev.mesbase.business.production.domain.OperationEntity;
import dev.mesbase.business.production.domain.OperationExample;
import dev.mesbase.business.production.domain.RoutingEntity;
import dev.mesbase.business.production.domain.RoutingNodeEntity;
import dev.mesbase.business.production.domain.RoutingEdgeEntity;
import dev.mesbase.business.production.domain.RoutingProductEntity;
import dev.mesbase.business.production.domain.WorkorderEntity;
import dev.mesbase.business.production.domain.WorkorderExample;
import dev.mesbase.business.production.domain.RoutingNodeExample;
import java.util.TreeSet;
import java.util.Objects;
import dev.mesbase.business.production.service.BusinessSourceRules;
import dev.mesbase.business.production.service.ProductionQuantityService;
import dev.mesbase.business.production.service.ReportUserReferenceService;
import dev.mesbase.business.production.service.WorkorderErpFacts;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.platform.execution.FactoryContext;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Internal operations use case. No public import endpoint, scheduler or startup hook.
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class LegacyHistoryImportService {
    private static final String RULE = "report-driven-local-v2";
    private final LegacyMigrationMapper metadata;
    private final WorkorderMapper orders;
    private final UnitMeasureMapper units;
    private final MaterialMapper materials;
    private final OperationMapper operations;
    private final RoutingMapper routings;
    private final RoutingNodeMapper nodes;
    private final RoutingEdgeMapper edges;
    private final RoutingProductMapper products;
    private final MesProductionExecutionMapper executions;
    private final MesExecutionPathMapper paths;
    private final MesExecutionPathNodeMapper selected;
    private final MesProductionReportMapper reports;
    private final ReportUserReferenceService users;
    private final ProductionQuantityService quantities;
    private final BusinessSourceRules sources;
    private final WorkorderErpFacts facts;
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    public LegacyHistoryImportService(LegacyMigrationMapper metadata, WorkorderMapper orders,
                                      UnitMeasureMapper units, MaterialMapper materials, OperationMapper operations,
                                      RoutingMapper routings, RoutingNodeMapper nodes, RoutingEdgeMapper edges,
                                      RoutingProductMapper products, MesProductionExecutionMapper executions,
                                      MesExecutionPathMapper paths, MesExecutionPathNodeMapper selected,
                                      MesProductionReportMapper reports, ReportUserReferenceService users,
                                      ProductionQuantityService quantities, BusinessSourceRules sources,
                                      WorkorderErpFacts facts) {
        this.metadata = metadata;
        this.orders = orders;
        this.units = units;
        this.materials = materials;
        this.operations = operations;
        this.routings = routings;
        this.nodes = nodes;
        this.edges = edges;
        this.products = products;
        this.executions = executions;
        this.paths = paths;
        this.selected = selected;
        this.reports = reports;
        this.users = users;
        this.quantities = quantities;
        this.sources = sources;
        this.facts = facts;
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void prepare(LegacyImportCommand command) {
        check(command);
        var batch = metadata.batch(command.batchId());
        if (batch != null) {
            sameBatch(batch, command);
            return;
        }
        metadata.prepare(new Batch(command.batchId(), RULE, command.snapshotHash(), LegacyBatchStatus.PREPARED.getValue(),
                encode(List.of(hash(encode(command)))), SecurityUtils.getUserId(), LocalDateTime.now(), null));
        sameBatch(metadata.batch(command.batchId()), command);
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void approve(LegacyImportCommand command) {
        check(command);
        var batch = metadata.batch(command.batchId());
        sameBatch(batch, command);
        if (List.of(LegacyBatchStatus.PREPARED.getValue(), LegacyBatchStatus.FAILED.getValue())
                .contains(batch.status()))
            changed(metadata.transition(batch.id(), batch.status(), LegacyBatchStatus.REVIEWED.getValue(), LegacyBatchStatus.COMPLETED.getValue()));
        else if (!List.of(LegacyBatchStatus.REVIEWED.getValue(), LegacyBatchStatus.COMPLETED.getValue())
                .contains(batch.status())) conflict();
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public Long execute(LegacyImportCommand command) {
        return executeReviewed(command, false);
    }

    /**
     * Append explicitly reviewed cards without changing the original order, route or source mapping.
     */
    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public Long append(LegacyImportCommand command) {
        return executeReviewed(command, true);
    }

    private Long executeReviewed(LegacyImportCommand c, boolean append) {
        check(c);
        var locks = new TreeSet<String>();
        locks.add(key("WORKORDER", c.code()));
        locks.add(key("UNIT", c.unitCode()));
        locks.add(key("MATERIAL", c.productCode()));
        for (var e : c.executions()) for (var n : e.nodes()) locks.add(key("PROCESS", n.processId()));
        // Persistent source keys serialize absent mappings as well as existing ones across JVMs/batches.
        for (var identity : locks) {
            metadata.ensureLock(identity);
            if (!identity.equals(metadata.lock(identity))) conflict();
        }
        // Triggers read batch authorization rows: never hold a competing batch row while waiting for source ownership.
        var batch = metadata.batch(c.batchId());
        sameBatch(batch, c);
        var existing = metadata.find(key("WORKORDER", c.code()));
        if (existing != null) {
            var order = requireExistingOrder(c, existing);
            boolean allMapped = c.executions().stream().allMatch(e -> mappedExecution(c, e, existing.id()));
            if (LegacyBatchStatus.COMPLETED.getValue().equals(batch.status())) {
                if (!allMapped) conflict();
                return order.getId();
            }
            if (!LegacyBatchStatus.REVIEWED.getValue().equals(batch.status()) || (!append && !allMapped)) conflict();
            changed(metadata.transition(batch.id(), LegacyBatchStatus.REVIEWED.getValue(), LegacyBatchStatus.RUNNING.getValue(), LegacyBatchStatus.COMPLETED.getValue()));
            if (!allMapped) {
                var criteria = new RoutingNodeExample();
                criteria.createCriteria().andRoutingIdEqualTo(order.getRoutingId());
                var targetNodes = new LinkedHashMap<String, Long>();
                for (var node : nodes.selectByExample(criteria)) {
                    String process = node.getNodeCode().replaceFirst("^LEGACY_", "");
                    targetNodes.put(process, node.getId());
                    for (var e : c.executions())
                        for (var n : e.nodes())
                            if (process.equals(n.processId()) && (!node.getOperationCode()
                                    .equals(n.code()) || !node.getOperationName().equals(n.name()))) conflict();
                }
                // The formal locked path must still be a complete route. Do not invent unreported nodes for a partial legacy card.
                for (var e : c.executions())
                    if (metadata.find(key("EXECUTION", e.sourceKey())) == null && e.nodes()
                            .size() != targetNodes.size()) conflict();
                var existingInputs = metadata.orderInputs(existing.id());
                var added = c.executions().stream().filter(e -> metadata.find(key("EXECUTION", e.sourceKey())) == null)
                        .map(e -> e.inputQty()).reduce(BigDecimal.ZERO, BigDecimal::add);
                if (existingInputs.add(added).compareTo(order.getPlannedQty()) > 0) conflict();
                map(c.batchId(), key("APPEND_AUTHORIZATION", c.batchId() + "/" + existing.id()), existing.id(),
                        "APPEND_AUTHORIZATION", c.batchId() + "/" + existing.id(), c.sourceHash(), "APPEND_AUTHORIZATION", order.getId(),
                        c.sourcePayload(), encode(new AppendAuthorization(hash(encode(c)))));
                appendExecutions(c, order, targetNodes, existing.id());
            }
            changed(metadata.transition(batch.id(), LegacyBatchStatus.RUNNING.getValue(), LegacyBatchStatus.COMPLETED.getValue(), LegacyBatchStatus.COMPLETED.getValue()));
            return order.getId();
        }
        if (append || !LegacyBatchStatus.REVIEWED.getValue().equals(batch.status())) conflict();
        changed(metadata.transition(batch.id(), LegacyBatchStatus.REVIEWED.getValue(), LegacyBatchStatus.RUNNING.getValue(), LegacyBatchStatus.COMPLETED.getValue()));
        var collision = new WorkorderExample();
        collision.createCriteria().andCodeEqualTo(c.code());
        if (orders.countByExample(collision) != 0) conflict();
        Long unit = unit(c);
        Long product = product(c, unit);
        var routing = new RoutingEntity();
        routing.setCode("LEGACY_" + key("WORKORDER", c.code()).substring(0, 24));
        routing.setName("历史转换工艺 " + c.code());
        routing.setVersion(1);
        routing.setStatus(RoutingStatus.PUBLISHED.getValue());
        routing.setRevision(0);
        routing.setSourceVersion(RULE);
        routing.setPublishedAt(LocalDateTime.now());
        routing.setDescription("冻结来源结构和实际报工工序的迁移版本，不代表原始历史发布版本");
        changed(routings.insert(routing));
        var targetNodes = new LinkedHashMap<String, Long>();
        Long previous = null;
        int sequence = 0;
        for (var n : c.executions().getFirst().nodes()) {
            Long operation = operation(c.batchId(), n);
            var node = new RoutingNodeEntity();
            node.setRoutingId(routing.getId());
            node.setNodeCode("LEGACY_" + n.processId());
            node.setOperationId(operation);
            node.setOperationCode(n.code());
            node.setOperationName(n.name());
            node.setIsKey(false);
            node.setDisplayOrder(++sequence);
            changed(nodes.insert(node));
            targetNodes.put(n.processId(), node.getId());
            if (previous != null) {
                var edge = new RoutingEdgeEntity();
                edge.setRoutingId(routing.getId());
                edge.setFromNodeId(previous);
                edge.setToNodeId(node.getId());
                changed(edges.insert(edge));
            }
            previous = node.getId();
        }
        var association = new RoutingProductEntity();
        association.setRoutingId(routing.getId());
        association.setMaterialId(product);
        changed(products.insert(association));
        var order = new WorkorderEntity();
        order.setCode(c.code());
        order.setSource(BusinessSource.LOCAL.getValue());
        order.setProductId(product);
        order.setRoutingId(routing.getId());
        order.setStatus(WorkOrderStatus.HISTORICAL.getValue());
        order.setPlannedQty(c.plannedQty());
        order.setPlannedStartAt(c.plannedStart());
        order.setPlannedEndAt(c.plannedEnd());
        order.setRevision(0);
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        order.setRemark("历史只读；未选流程卡保持尚未迁移，不代表整单完工");
        changed(orders.insert(order));
        facts.save(order, true);
        String orderKey = key("WORKORDER", c.code());
        map(c.batchId(), orderKey, null, "WORKORDER", c.code(), c.sourceHash(), "WORKORDER", order.getId(), c.sourcePayload(), "{}");
        map(c.batchId(), key("ROUTING", c.code()), orderKey, "ROUTING", c.code(), c.sourceHash(), "ROUTING", routing.getId(), c.sourcePayload(), "{}");
        appendExecutions(c, order, targetNodes, orderKey);
        changed(metadata.transition(batch.id(), LegacyBatchStatus.RUNNING.getValue(), LegacyBatchStatus.COMPLETED.getValue(), LegacyBatchStatus.COMPLETED.getValue()));
        return order.getId();
    }

    private record AppendAuthorization(String commandHash) {
    }

    private WorkorderEntity requireExistingOrder(LegacyImportCommand c, Mapping existing) {
        if (!existing.sourceHash().equals(c.sourceHash()) || !existing.ruleVersion().equals(RULE)) conflict();
        var order = orders.selectByPrimaryKeyForUpdate(existing.entityId());
        var route = metadata.find(key("ROUTING", c.code()));
        var product = metadata.find(key("MATERIAL", c.productCode()));
        if (order == null || !WorkOrderStatus.HISTORICAL.getValue().equals(order.getStatus())
                || !BusinessSource.LOCAL.getValue().equals(order.getSource()) || !order.getCode().equals(c.code())
                || order.getPlannedQty().compareTo(c.plannedQty()) != 0
                || !order.getPlannedStartAt().equals(c.plannedStart()) || !order.getPlannedEndAt()
                .equals(c.plannedEnd())
                || route == null || !route.entityId().equals(order.getRoutingId()) || !route.sourceHash()
                .equals(c.sourceHash())
                || product == null || !product.entityId().equals(order.getProductId())
                || !product.sourceHash().equals(hash(encode(List.of(c.productCode(), c.productName(), c.unitCode())))))
            conflict();
        var material = materials.selectByPrimaryKey(order.getProductId());
        var unit = metadata.find(key("UNIT", c.unitCode()));
        var routeRow = routings.selectByPrimaryKey(order.getRoutingId());
        if (material == null || unit == null || !unit.entityId().equals(material.getUnitId())
                || !material.getCode().equals(c.productCode()) || !material.getName().equals(c.productName())
                || !Objects.equals(material.getSpecification(), c.specification())
                || !unit.sourceHash().equals(hash(encode(List.of(c.unitCode(), c.unitName()))))
                || routeRow == null || !RoutingStatus.PUBLISHED.getValue().equals(routeRow.getStatus())
                || !RULE.equals(routeRow.getSourceVersion())) conflict();
        return order;
    }

    private boolean mappedExecution(LegacyImportCommand c, LegacyImportCommand.Execution e, String orderKey) {
        var mapped = metadata.find(key("EXECUTION", e.sourceKey()));
        if (mapped == null) return false;
        if (!mapped.sourceHash().equals(e.sourceHash()) || !mapped.ruleVersion().equals(RULE)
                || !orderKey.equals(mapped.parentId())) conflict();
        var row = executions.selectByPrimaryKey(mapped.entityId());
        var order = metadata.find(orderKey);
        if (row == null || order == null || !row.getWorkorderId().equals(order.entityId())
                || !ProductionExecutionStatus.HISTORICAL.getValue().equals(row.getStatus())) conflict();
        for (var r : e.reports()) {
            var report = metadata.find(key("pro_reporting_work", r.sourceId()));
            if (report == null || !report.sourceHash().equals(r.sourceHash()) || !RULE.equals(report.ruleVersion())
                    || !key("NODE", e.sourceKey() + "/" + r.processId()).equals(report.parentId())) conflict();
        }
        return true;
    }

    private void appendExecutions(LegacyImportCommand c, WorkorderEntity order, Map<String, Long> targetNodes,
                                  String orderKey) {
        for (var e : c.executions()) {
            if (mappedExecution(c, e, orderKey)) continue;
            if (!e.nodes().stream().allMatch(n -> targetNodes.containsKey(n.processId()))) conflict();
            var execution = new MesProductionExecutionEntity();
            execution.setWorkorderId(order.getId());
            execution.setProductId(order.getProductId());
            execution.setRoutingId(order.getRoutingId());
            execution.setTargetQty(e.inputQty());
            execution.setStatus(ProductionExecutionStatus.HISTORICAL.getValue());
            execution.setExecutionNo("LEGACY_" + key("EXECUTION", e.sourceKey()).substring(0, 24));
            execution.setCreateTime(LocalDateTime.now());
            execution.setUpdateTime(LocalDateTime.now());
            execution.setCreateBy(String.valueOf(SecurityUtils.getUserId()));
            execution.setRemark("历史投入仅用于数量解释，未执行当前领料及完工");
            changed(executions.insert(execution));
            var path = new MesExecutionPathEntity();
            path.setId(execution.getId());
            path.setRoutingId(order.getRoutingId());
            path.setPathStatus(ExecutionPathStatus.LOCKED.getValue());
            path.setRevision(0);
            path.setSelectedAt(LocalDateTime.now());
            path.setLockedAt(LocalDateTime.now());
            changed(paths.insert(path));
            String executionKey = key("EXECUTION", e.sourceKey());
            map(c.batchId(), executionKey, orderKey, "EXECUTION", e.sourceKey(), e.sourceHash(), "EXECUTION",
                    execution.getId(), e.sourcePayload(), encode(new ExecutionEvidence(e.inputQty(), "LEGACY_MATERIAL_EVIDENCE")));
            int sequence = 0;
            for (var n : e.nodes()) {
                Long nodeId = targetNodes.get(n.processId());
                var choice = new MesExecutionPathNodeEntity();
                choice.setExecutionId(execution.getId());
                choice.setRoutingNodeId(nodeId);
                choice.setSequenceNo(++sequence);
                changed(selected.insert(choice));
                String nodeKey = key("NODE", e.sourceKey() + "/" + n.processId());
                map(c.batchId(), nodeKey, executionKey, "NODE", e.sourceKey() + "/" + n.processId(), hash(n.evidence()),
                        "NODE", nodeId, n.evidence(), encode(n));
            }
            for (var r : e.reports()) {
                users.requireExists(r.operatorUserId());
                var report = new MesProductionReportEntity();
                report.setExecutionId(execution.getId());
                report.setRoutingNodeId(targetNodes.get(r.processId()));
                report.setRecordType(ReportRecordType.REPORT.getValue());
                report.setQuantityDelta(r.quantity());
                report.setOperatorUserId(r.operatorUserId());
                report.setRecordedBy(SecurityUtils.getUserId());
                report.setReportTime(r.reportTime());
                report.setCreateTime(LocalDateTime.now());
                report.setReason("历史净报工；工序总加工量另有可追溯聚合证据");
                report.setRequestId(UUID.nameUUIDFromBytes(key("pro_reporting_work", r.sourceId()).getBytes(StandardCharsets.UTF_8))
                        .toString());
                report.setCommandFingerprint(r.sourceHash());
                changed(reports.insert(report));
                map(c.batchId(), key("pro_reporting_work", r.sourceId()), key("NODE", e.sourceKey() + "/" + r.processId()),
                        "pro_reporting_work", r.sourceId(), r.sourceHash(), "REPORT", report.getId(), r.sourcePayload(), "{}");
            }
            for (var q : e.defects())
                map(c.batchId(), key("qc_defective", q.sourceId()), executionKey,
                        "qc_defective", q.sourceId(), q.sourceHash(), "QUALITY_EVIDENCE", null, q.sourcePayload(), "{}");
            var projected = quantities.getExecutionQuantities(execution.getId());
            if (!projected.historical() || projected.writable() || projected.nodes().size() != e.nodes().size())
                conflict();
            for (int i = 0; i < e.nodes().size(); i++) {
                var q = projected.nodes().get(i);
                if (q.historicalConflict() || q.effectiveOutputQty().compareTo(e.nodes().get(i).legacyNet()) != 0
                        || q.availableQty().signum() != 0) conflict();
            }
        }
    }

    private Long unit(LegacyImportCommand c) {
        var old = metadata.find(key("UNIT", c.unitCode()));
        if (old != null) {
            if (!old.sourceHash()
                    .equals(hash(encode(List.of(c.unitCode(), c.unitName())))) || !RULE.equals(old.ruleVersion()))
                conflict();
            return old.entityId();
        }
        var f = new UnitMeasureExample();
        f.createCriteria().andCodeEqualTo(c.unitCode());
        if (units.countByExample(f) != 0) conflict();
        var row = new UnitMeasureEntity();
        row.setCode(c.unitCode());
        row.setName(c.unitName());
        row.setSymbol(c.unitName());
        row.setStatus(EnableStatus.ENABLED.getValue());
        changed(units.insert(row));
        String payload = encode(List.of(c.unitCode(), c.unitName()));
        map(c.batchId(), key("UNIT", c.unitCode()), null, "UNIT", c.unitCode(), hash(payload), "UNIT", row.getId(), payload, "{}");
        return row.getId();
    }

    private Long product(LegacyImportCommand c, Long unit) {
        var old = metadata.find(key("MATERIAL", c.productCode()));
        if (old != null) {
            if (!old.sourceHash()
                    .equals(hash(encode(List.of(c.productCode(), c.productName(), c.unitCode())))) || !RULE.equals(old.ruleVersion()))
                conflict();
            return old.entityId();
        }
        var f = new MaterialExample();
        f.createCriteria().andCodeEqualTo(c.productCode());
        if (materials.countByExample(f) != 0) conflict();
        var row = new MaterialEntity();
        row.setCode(c.productCode());
        row.setName(c.productName());
        row.setSpecification(c.specification());
        row.setUnitId(unit);
        row.setMaterialType(MaterialType.PRODUCT.getValue());
        row.setStatus(EnableStatus.ENABLED.getValue());
        changed(materials.insert(row));
        String payload = encode(List.of(c.productCode(), c.productName(), c.unitCode()));
        map(c.batchId(), key("MATERIAL", c.productCode()), null, "MATERIAL", c.productCode(), hash(payload), "MATERIAL", row.getId(), payload, "{}");
        return row.getId();
    }

    private Long operation(String batch, LegacyImportCommand.Node n) {
        var old = metadata.find(key("PROCESS", n.processId()));
        if (old != null) {
            if (!old.sourceHash()
                    .equals(hash(encode(List.of(n.processId(), n.code(), n.name())))) || !RULE.equals(old.ruleVersion()))
                conflict();
            return old.entityId();
        }
        var f = new OperationExample();
        f.createCriteria().andCodeEqualTo(n.code());
        if (operations.countByExample(f) != 0) conflict();
        var row = new OperationEntity();
        row.setCode(n.code());
        row.setName(n.name());
        row.setStatus(EnableStatus.ENABLED.getValue());
        row.setSort(0);
        row.setRevision(0);
        changed(operations.insert(row));
        String payload = encode(List.of(n.processId(), n.code(), n.name()));
        map(batch, key("PROCESS", n.processId()), null, "PROCESS", n.processId(), hash(payload), "OPERATION", row.getId(), payload, "{}");
        return row.getId();
    }

    private void check(LegacyImportCommand c) {
        if (c == null || !FactoryContext.required().factoryId().value().equals(c.factoryId())
                || c.executions() == null || c.executions().isEmpty() || c.executions().size() > 100
                || c.plannedQty() == null || c.plannedQty().signum() <= 0 || c.plannedEnd().isBefore(c.plannedStart()))
            conflict();
        if (c.code() == null || c.code().isBlank() || c.code().length() > 64
                || c.snapshotHash() == null || !c.snapshotHash().matches("[0-9a-f]{64}")
                || c.sourceHash() == null || !c.sourceHash().matches("[0-9a-f]{64}")
                || c.executions().stream().mapToInt(e -> e.reports().size()).sum() > 10000
                || c.executions().stream().map(e -> e.inputQty()).reduce(BigDecimal.ZERO, BigDecimal::add)
                .compareTo(c.plannedQty()) > 0)
            conflict();
        try {
            var source = json.readTree(c.sourcePayload());
            if (!hash(c.sourcePayload()).equals(c.sourceHash()) || !source.path("code").asText().equals(c.code()))
                conflict();
            for (var e : c.executions()) {
                var frozen = json.readTree(e.sourcePayload());
                if (!hash(e.sourcePayload()).equals(e.sourceHash()) || !encode(frozen.path("key")).equals(e.sourceKey())
                        || !frozen.path("key").get(0).asText().equals(c.code())
                        || new BigDecimal(frozen.path("input_qty_candidate").asText()).compareTo(e.inputQty()) != 0)
                    conflict();
                boolean belongs = false;
                for (var original : source.path("executions")) if (original.equals(frozen)) belongs = true;
                if (!belongs) conflict();
                for (var r : e.reports()) {
                    var raw = json.readTree(r.sourcePayload()).path("report");
                    if (!hash(encode(raw)).equals(r.sourceHash()) || !raw.path("record_id").asText()
                            .equals(r.sourceId())
                            || !raw.path("process_id").asText().equals(r.processId())
                            || !raw.path("user_id").asText().equals(r.operatorUserId().toString())
                            || new BigDecimal(raw.path("reporting_work_quantity").asText()).compareTo(r.quantity()) != 0
                            || !LocalDateTime.parse(raw.path("create_time").asText().replace(' ', 'T'))
                            .equals(r.reportTime())) conflict();
                    boolean present = false;
                    for (var original : frozen.path("reports")) if (original.path("raw").equals(raw)) present = true;
                    if (!present) conflict();
                }
                if (frozen.path("reports").size() != e.reports().size()) conflict();
            }
        } catch (JsonProcessingException failure) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        sources.localCreation();
        SecurityUtils.getUserId();
        for (var e : c.executions()) {
            if (e.nodes().isEmpty() || e.nodes().size() > 100 || e.reports().size() > 10000 || e.inputQty()
                    .signum() <= 0) conflict();
            BigDecimal upstream = e.inputQty();
            for (var n : e.nodes()) {
                if (n.legacyNet().signum() < 0 || n.grossCorrection().signum() < 0 || n.effectiveDefect().signum() < 0
                        || n.grossCorrection().compareTo(n.effectiveDefect()) != 0
                        || n.legacyNet().add(n.grossCorrection()).compareTo(upstream) > 0) conflict();
                var total = e.reports().stream().filter(r -> r.processId().equals(n.processId()))
                        .map(r -> r.quantity()).reduce(BigDecimal.ZERO, BigDecimal::add);
                if (total.compareTo(n.legacyNet()) != 0) conflict();
                upstream = n.legacyNet();
            }
        }
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public void recordFailure(LegacyImportCommand command, boolean conflict) {
        check(command);
        var batch = metadata.batch(command.batchId());
        sameBatch(batch, command);
        if (LegacyBatchStatus.COMPLETED.getValue().equals(batch.status())) return;
        String next = conflict ? LegacyBatchStatus.CONFLICT.getValue() : LegacyBatchStatus.FAILED.getValue();
        changed(metadata.transition(batch.id(), batch.status(), next, LegacyBatchStatus.COMPLETED.getValue()));
    }

    private void sameBatch(Batch batch, LegacyImportCommand c) {
        if (batch == null || !batch.ruleVersion().equals(RULE) || !batch.snapshotHash().equals(c.snapshotHash())
                || !batch.approvedScope().replace(" ", "").equals(encode(List.of(hash(encode(c)))))
                || !batch.operatorUserId().equals(SecurityUtils.getUserId())) conflict();
    }

    private void map(String batch, String id, String parent, String table, String pk, String sourceHash,
                     String kind, Long target, String raw, String conversion) {
        changed(metadata.insert(new Mapping(id, batch, parent, table, pk, sourceHash, RULE, kind, target, raw, conversion, LocalDateTime.now())));
    }

    private String key(String table, String pk) {
        return hash(encode(List.of("03dbc952-7403-11ef-802c-0242ac130002", "prod_j2eedb", table, pk)));
    }

    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Legacy evidence serialization failed", e);
        }
    }

    private static String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void changed(int rows) {
        if (rows != 1) conflict();
    }

    private static void conflict() {
        throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
    }
}
