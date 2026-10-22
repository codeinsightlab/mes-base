package dev.mesbase.business.legacy.service;

import dev.mesbase.common.utils.StringUtils;
import dev.mesbase.common.validation.ParameterChecks;
import java.util.Optional;

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
import com.fasterxml.jackson.databind.JsonNode;
import dev.mesbase.business.legacy.domain.LegacyHistory.MappingIdentity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.Collection;
import java.util.function.Function;
import javax.sql.DataSource;
import dev.mesbase.infrastructure.persistence.BusinessRuntimeBinding;
import dev.mesbase.infrastructure.persistence.FactoryBusinessDataSource;
import dev.mesbase.infrastructure.persistence.SpringCommitActions;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
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
import dev.mesbase.business.production.domain.MesProductionExecutionExample;
import dev.mesbase.business.production.domain.MesProductionReportEntity;
import dev.mesbase.business.production.domain.OperationEntity;
import dev.mesbase.business.production.domain.OperationExample;
import dev.mesbase.business.production.domain.RoutingEntity;
import dev.mesbase.business.production.domain.RoutingExample;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Internal operations use case. No public import endpoint, scheduler or startup hook.
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class LegacyHistoryImportService {
    private static final String RULE = "report-driven-local-v2";
    private static final int BATCH_SIZE = 128;
    private static final int MAPPING_PAYLOAD_BYTES = 512 * 1024;
    private final ObjectProvider<DataSource> dataSource;
    private final SpringCommitActions commits;
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
                                      WorkorderErpFacts facts,
                                      @Qualifier("businessDataSource") ObjectProvider<DataSource> dataSource,
                                      SpringCommitActions commits) {
        this.dataSource = dataSource;
        this.commits = commits;
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

    /**
     * Explicit, bounded work-unit cache; never a global cache or a second routing authority.
     */
    public static final class BatchReferences implements AutoCloseable {
        private final Thread owner = Thread.currentThread();
        private final BusinessRuntimeBinding binding;
        private final Set<String> commands;
        private final Map<String, MappingIdentity> identities = new LinkedHashMap<>();
        private final Map<Long, MaterialEntity> materials = new LinkedHashMap<>();
        private final Map<Long, UnitMeasureEntity> units = new LinkedHashMap<>();
        private final Map<Long, OperationEntity> operations = new LinkedHashMap<>();
        private final Map<Long, RoutingEntity> routings = new LinkedHashMap<>();
        private final Map<Long, MesProductionExecutionEntity> executions = new LinkedHashMap<>();
        private boolean closed;

        private BatchReferences(BusinessRuntimeBinding binding, Set<String> commands) {
            this.binding = binding;
            this.commands = Set.copyOf(commands);
        }

        private void verify(BusinessRuntimeBinding actual, String command) {
            if (closed || Thread.currentThread() != owner || !binding.equals(actual) || !commands.contains(command))
                ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        }

        @Override
        public void close() {
            if (Thread.currentThread() != owner) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
            closed = true;
            identities.clear();
            materials.clear();
            units.clear();
            operations.clear();
            routings.clear();
            executions.clear();
        }
    }


    private static final class ReferenceLookup {
        private final BatchReferences references;
        private final Map<String, MappingIdentity> identities = new LinkedHashMap<>();
        private final Map<Long, MaterialEntity> materials = new LinkedHashMap<>();
        private final Map<Long, RoutingEntity> routings = new LinkedHashMap<>();
        private final Set<String> operationCodes = new HashSet<>();

        private ReferenceLookup(BatchReferences references) {
            this.references = references;
            if (references != null) {
                identities.putAll(references.identities);
                materials.putAll(references.materials);
                routings.putAll(references.routings);
            }
        }
    }

    private BusinessRuntimeBinding currentBinding() {
        DataSource actual = Optional.ofNullable(dataSource).map(nullableValue -> nullableValue.getObject()).orElse(null);
        if (!(actual instanceof FactoryBusinessDataSource)) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        return ((FactoryBusinessDataSource) actual).currentBinding();
    }

    private List<String> headerKeys(LegacyImportCommand c) {
        var keys = new TreeSet<String>();
        keys.add(key("WORKORDER", c.code()));
        keys.add(key("ROUTING", c.code()));
        keys.add(key("UNIT", c.unitCode()));
        keys.add(key("MATERIAL", c.productCode()));
        for (var e : c.executions()) for (var n : e.nodes()) keys.add(key("PROCESS", n.processId()));
        return List.copyOf(keys);
    }

    private List<String> factKeys(LegacyImportCommand c) {
        var keys = new ArrayList<String>();
        for (var e : c.executions()) {
            keys.add(key("EXECUTION", e.sourceKey()));
            for (var r : e.reports()) keys.add(key("pro_reporting_work", r.sourceId()));
        }
        return keys;
    }

    private void readIdentities(List<String> ids, Map<String, MappingIdentity> result) {
        for (int from = 0; from < ids.size(); from += BATCH_SIZE)
            for (var row : metadata.findIdentities(ids.subList(from, Math.min(from + BATCH_SIZE, ids.size()))))
                result.put(row.id(), row);
    }

    private List<Long> entityIds(Collection<LegacyImportCommand> commands, String kind,
                                 Function<LegacyImportCommand, String> value,
                                 Map<String, MappingIdentity> identities) {
        return commands.stream().map(c -> identities.get(key(kind, value.apply(c)))).filter(Objects::nonNull)
                .map(MappingIdentity::entityId).filter(Objects::nonNull).distinct().toList();
    }

    private <T> void loadRows(List<Long> ids, Map<Long, T> into, Function<List<Long>, List<T>> reader,
                              Function<T, Long> id) {
        var missing = ids.stream().filter(v -> !into.containsKey(v)).distinct().toList();
        for (int from = 0; from < missing.size(); from += BATCH_SIZE)
            for (var row : reader.apply(missing.subList(from, Math.min(from + BATCH_SIZE, missing.size()))))
                into.put(id.apply(row), row);
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true, propagation = Propagation.REQUIRES_NEW,
            isolation = Isolation.READ_COMMITTED)
    public BatchReferences preloadBatch(List<LegacyImportCommand> commands) {
        if (commands == null || commands.isEmpty() || commands.size() > 10) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        var hashes = new HashSet<String>();
        var keys = new TreeSet<String>();
        for (var c : commands) {
            check(c);
            hashes.add(hash(encode(c)));
            keys.addAll(headerKeys(c));
        }
        if (commands.stream().map(LegacyImportCommand::snapshotHash).distinct().count() != 1) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        var refs = new BatchReferences(currentBinding(), hashes);
        readIdentities(List.copyOf(keys), refs.identities);
        var existing = commands.stream().filter(c -> refs.identities.containsKey(key("WORKORDER", c.code()))).toList();
        readIdentities(existing.stream().flatMap(c -> factKeys(c).stream()).distinct().toList(), refs.identities);
        loadRows(entityIds(commands, "UNIT", LegacyImportCommand::unitCode, refs.identities), refs.units, ids -> {
            var f = new UnitMeasureExample();
            f.createCriteria().andIdIn(ids);
            return units.selectByExample(f);
        }, UnitMeasureEntity::getId);
        loadRows(entityIds(commands, "MATERIAL", LegacyImportCommand::productCode, refs.identities), refs.materials, ids -> {
            var f = new MaterialExample();
            f.createCriteria().andIdIn(ids);
            return materials.selectByExample(f);
        }, MaterialEntity::getId);
        var operationIds = commands.stream().flatMap(c -> c.executions().stream()).flatMap(e -> e.nodes().stream())
                .map(n -> refs.identities.get(key("PROCESS", n.processId()))).filter(Objects::nonNull)
                .map(MappingIdentity::entityId).distinct().toList();
        loadRows(operationIds, refs.operations, ids -> {
            var f = new OperationExample();
            f.createCriteria().andIdIn(ids);
            return operations.selectByExample(f);
        }, OperationEntity::getId);
        loadRows(entityIds(commands, "ROUTING", LegacyImportCommand::code, refs.identities), refs.routings, ids -> {
            var f = new RoutingExample();
            f.createCriteria().andIdIn(ids);
            return routings.selectByExample(f);
        }, RoutingEntity::getId);
        var executionIds = existing.stream().flatMap(c -> c.executions().stream())
                .map(e -> refs.identities.get(key("EXECUTION", e.sourceKey()))).filter(Objects::nonNull)
                .map(MappingIdentity::entityId).distinct().toList();
        loadRows(executionIds, refs.executions, ids -> {
            var f = new MesProductionExecutionExample();
            f.createCriteria().andIdIn(ids);
            return executions.selectByExample(f);
        }, MesProductionExecutionEntity::getId);
        users.requireAllExist(commands.stream().flatMap(c -> c.executions().stream()).flatMap(e -> e.reports().stream())
                .map(LegacyImportCommand.Report::operatorUserId).toList());
        return refs;
    }

    private ReferenceLookup lookup(LegacyImportCommand command, BatchReferences references) {
        if (references != null) references.verify(currentBinding(), hash(encode(command)));
        var lookup = new ReferenceLookup(references);
        // Only unresolved identities are refreshed under the original source locks. Never trust an absence cached in an earlier transaction.
        readIdentities(headerKeys(command).stream().filter(k -> !lookup.identities.containsKey(k))
                .toList(), lookup.identities);
        if (references != null) {
            loadRows(entityIds(List.of(command), "MATERIAL", LegacyImportCommand::productCode, lookup.identities), lookup.materials, ids -> {
                var f = new MaterialExample();
                f.createCriteria().andIdIn(ids);
                return materials.selectByExample(f);
            }, MaterialEntity::getId);
            loadRows(entityIds(List.of(command), "ROUTING", LegacyImportCommand::code, lookup.identities), lookup.routings, ids -> {
                var f = new RoutingExample();
                f.createCriteria().andIdIn(ids);
                return routings.selectByExample(f);
            }, RoutingEntity::getId);
        }
        var codes = command.executions().stream().flatMap(e -> e.nodes().stream())
                .filter(n -> !lookup.identities.containsKey(key("PROCESS", n.processId())))
                .map(LegacyImportCommand.Node::code).distinct().toList();
        for (int from = 0; from < codes.size(); from += BATCH_SIZE) {
            var f = new OperationExample();
            f.createCriteria().andCodeIn(codes.subList(from, Math.min(from + BATCH_SIZE, codes.size())));
            for (var row : operations.selectByExample(f)) lookup.operationCodes.add(row.getCode());
        }
        return lookup;
    }

    private void remember(ReferenceLookup lookup, Mapping row) {
        var identity = new MappingIdentity(row.id(), row.parentId(), row.sourceHash(), row.ruleVersion(), row.entityId());
        lookup.identities.put(identity.id(), identity);
        if (lookup.references != null) commits.afterCommit(() -> {
            if (!lookup.references.closed) lookup.references.identities.put(identity.id(), identity);
        });
    }

    private void rememberRow(ReferenceLookup lookup, RoutingEntity routing) {
        lookup.routings.put(routing.getId(), routing);
        if (lookup.references != null) commits.afterCommit(() -> {
            if (!lookup.references.closed) lookup.references.routings.put(routing.getId(), routing);
        });
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
                .contains(batch.status())) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public Long execute(LegacyImportCommand command) {
        return executeReviewed(command, false, null);
    }

    /**
     * Append explicitly reviewed cards without changing the original order, route or source mapping.
     */
    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public Long append(LegacyImportCommand command) {
        return executeReviewed(command, true, null);
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public Long execute(LegacyImportCommand command, BatchReferences references) {
        return executeReviewed(command, false, references);
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public Long append(LegacyImportCommand command, BatchReferences references) {
        return executeReviewed(command, true, references);
    }

    private Long executeReviewed(LegacyImportCommand c, boolean append, BatchReferences references) {
        check(c);
        if (references != null) references.verify(currentBinding(), hash(encode(c)));
        var locks = new TreeSet<String>();
        locks.add(key("WORKORDER", c.code()));
        locks.add(key("UNIT", c.unitCode()));
        locks.add(key("MATERIAL", c.productCode()));
        for (var e : c.executions()) for (var n : e.nodes()) locks.add(key("PROCESS", n.processId()));
        // Persistent source keys serialize absent mappings as well as existing ones across JVMs/batches.
        for (var identity : locks) {
            metadata.ensureLock(identity);
            if (!identity.equals(metadata.lock(identity))) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        }
        var lookup = lookup(c, references);
        // Triggers read batch authorization rows: never hold a competing batch row while waiting for source ownership.
        var batch = metadata.batch(c.batchId());
        sameBatch(batch, c);
        var existing = lookup.identities.get(key("WORKORDER", c.code()));
        if (existing != null) {
            var order = requireExistingOrder(c, existing, lookup);
            var replay = replayMappings(c, lookup);
            boolean allMapped = c.executions().stream()
                    .allMatch(e -> mappedExecution(c, e, existing.id(), order.getId(), replay));
            if (LegacyBatchStatus.COMPLETED.getValue().equals(batch.status())) {
                if (!allMapped) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
                return order.getId();
            }
            if (!LegacyBatchStatus.REVIEWED.getValue().equals(batch.status()) || (!append && !allMapped)) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
            changed(metadata.transition(batch.id(), LegacyBatchStatus.REVIEWED.getValue(), LegacyBatchStatus.RUNNING.getValue(), LegacyBatchStatus.COMPLETED.getValue()));
            if (!allMapped) {
                if (!WorkOrderStatus.HISTORICAL.getValue().equals(order.getStatus())) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
                var criteria = new RoutingNodeExample();
                criteria.createCriteria().andRoutingIdEqualTo(order.getRoutingId());
                var targetNodes = new LinkedHashMap<String, Long>();
                for (var node : nodes.selectByExample(criteria)) {
                    String process = node.getNodeCode().replaceFirst("^LEGACY_", "");
                    targetNodes.put(process, node.getId());
                    for (var e : c.executions())
                        for (var n : e.nodes())
                            if (process.equals(n.processId()) && (!node.getOperationCode()
                                    .equals(n.code()) || !node.getOperationName().equals(n.name()))) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
                }
                // The formal locked path must still be a complete route. Do not invent unreported nodes for a partial legacy card.
                for (var e : c.executions())
                    if (!replay.mappings().containsKey(key("EXECUTION", e.sourceKey())) && e.nodes()
                            .size() != targetNodes.size()) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
                var existingInputs = metadata.orderInputs(existing.id());
                var added = c.executions().stream()
                        .filter(e -> !replay.mappings().containsKey(key("EXECUTION", e.sourceKey())))
                        .map(e -> e.inputQty()).reduce(BigDecimal.ZERO, BigDecimal::add);
                if (existingInputs.add(added).compareTo(order.getPlannedQty()) > 0) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
                map(lookup, c.batchId(), key("APPEND_AUTHORIZATION", c.batchId() + "/" + existing.id()), existing.id(),
                        "APPEND_AUTHORIZATION", c.batchId() + "/" + existing.id(), c.sourceHash(), "APPEND_AUTHORIZATION", order.getId(),
                        c.sourcePayload(), encode(new AppendAuthorization(hash(encode(c)))));
                appendExecutions(c, order, targetNodes, existing.id(), replay, lookup);
            }
            changed(metadata.transition(batch.id(), LegacyBatchStatus.RUNNING.getValue(), LegacyBatchStatus.COMPLETED.getValue(), LegacyBatchStatus.COMPLETED.getValue()));
            return order.getId();
        }
        if (append || !LegacyBatchStatus.REVIEWED.getValue().equals(batch.status())) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        changed(metadata.transition(batch.id(), LegacyBatchStatus.REVIEWED.getValue(), LegacyBatchStatus.RUNNING.getValue(), LegacyBatchStatus.COMPLETED.getValue()));
        var collision = new WorkorderExample();
        collision.createCriteria().andCodeEqualTo(c.code());
        if (orders.countByExample(collision) != 0) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        Long unit = unit(c, lookup);
        Long product = product(c, unit, lookup);
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
            Long operation = operation(c.batchId(), n, lookup);
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
        map(lookup, c.batchId(), orderKey, null, "WORKORDER", c.code(), c.sourceHash(), "WORKORDER", order.getId(), c.sourcePayload(), "{}");
        map(lookup, c.batchId(), key("ROUTING", c.code()), orderKey, "ROUTING", c.code(), c.sourceHash(), "ROUTING", routing.getId(), c.sourcePayload(), "{}");
        rememberRow(lookup, routing);
        appendExecutions(c, order, targetNodes, orderKey, new ReplayState(Map.of(), Map.of()), lookup);
        changed(metadata.transition(batch.id(), LegacyBatchStatus.RUNNING.getValue(), LegacyBatchStatus.COMPLETED.getValue(), LegacyBatchStatus.COMPLETED.getValue()));
        return order.getId();
    }

    private record AppendAuthorization(String commandHash) {
    }

    private WorkorderEntity requireExistingOrder(LegacyImportCommand c, MappingIdentity existing,
                                                 ReferenceLookup lookup) {
        if (!existing.sourceHash().equals(c.sourceHash()) || !existing.ruleVersion().equals(RULE)) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        var order = orders.selectByPrimaryKeyForUpdate(existing.entityId());
        var route = lookup.identities.get(key("ROUTING", c.code()));
        var product = lookup.identities.get(key("MATERIAL", c.productCode()));
        if (order == null || (!WorkOrderStatus.HISTORICAL.getValue()
                .equals(order.getStatus()) && metadata.activatedOrder(order.getId()) != 1)
                || !BusinessSource.LOCAL.getValue().equals(order.getSource()) || !order.getCode().equals(c.code())
                || order.getPlannedQty().compareTo(c.plannedQty()) != 0
                || !order.getPlannedStartAt().equals(c.plannedStart()) || !order.getPlannedEndAt()
                .equals(c.plannedEnd())
                || route == null || !route.entityId().equals(order.getRoutingId()) || !route.sourceHash()
                .equals(c.sourceHash())
                || product == null || !product.entityId().equals(order.getProductId())
                || !product.sourceHash().equals(hash(encode(List.of(c.productCode(), c.productName(), c.unitCode())))))
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        var material = lookup.references == null ? materials.selectByPrimaryKey(order.getProductId()) : lookup.materials.get(order.getProductId());
        var unit = lookup.identities.get(key("UNIT", c.unitCode()));
        var routeRow = lookup.references == null ? routings.selectByPrimaryKey(order.getRoutingId()) : lookup.routings.get(order.getRoutingId());
        if (material == null || unit == null || !unit.entityId().equals(material.getUnitId())
                || !material.getCode().equals(c.productCode()) || !material.getName().equals(c.productName())
                || !Objects.equals(material.getSpecification(), c.specification())
                || !unit.sourceHash().equals(hash(encode(List.of(c.unitCode(), c.unitName()))))
                || routeRow == null || !RoutingStatus.PUBLISHED.getValue().equals(routeRow.getStatus())
                || !RULE.equals(routeRow.getSourceVersion())) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        return order;
    }

    private boolean mappedExecution(LegacyImportCommand c, LegacyImportCommand.Execution e, String orderKey,
                                    Long orderId,
                                    ReplayState replay) {
        var mapped = replay.mappings().get(key("EXECUTION", e.sourceKey()));
        if (mapped == null) return false;
        if (!mapped.sourceHash().equals(e.sourceHash()) || !mapped.ruleVersion().equals(RULE)
                || !orderKey.equals(mapped.parentId())) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        var row = replay.executions().get(mapped.entityId());
        if (row == null || !row.getWorkorderId().equals(orderId)
                || (!ProductionExecutionStatus.HISTORICAL.getValue()
                .equals(row.getStatus()) && metadata.activatedInput(row.getId()) == null)) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        for (var r : e.reports()) {
            var report = replay.mappings().get(key("pro_reporting_work", r.sourceId()));
            if (report == null || !report.sourceHash().equals(r.sourceHash()) || !RULE.equals(report.ruleVersion())
                    || !key("NODE", e.sourceKey() + "/" + r.processId()).equals(report.parentId())) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        }
        return true;
    }

    private void appendExecutions(LegacyImportCommand c, WorkorderEntity order, Map<String, Long> targetNodes,
                                  String orderKey, ReplayState replay, ReferenceLookup lookup) {
        var pending = c.executions().stream().filter(e -> !mappedExecution(c, e, orderKey, order.getId(), replay))
                .toList();
        if (lookup.references == null)
            users.requireAllExist(pending.stream().flatMap(e -> e.reports().stream())
                    .map(LegacyImportCommand.Report::operatorUserId).toList());
        var imported = new LinkedHashMap<Long, LegacyImportCommand.Execution>();
        for (var e : pending) {
            if (!e.nodes().stream().allMatch(n -> targetNodes.containsKey(n.processId()))) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
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
            map(lookup, c.batchId(), executionKey, orderKey, "EXECUTION", e.sourceKey(), e.sourceHash(), "EXECUTION",
                    execution.getId(), e.sourcePayload(), encode(new ExecutionEvidence(e.inputQty(), "LEGACY_MATERIAL_EVIDENCE")));
            int sequence = 0;
            var nodeMappings = new ArrayList<Mapping>();
            var choices = new ArrayList<MesExecutionPathNodeEntity>();
            for (var n : e.nodes()) {
                Long nodeId = targetNodes.get(n.processId());
                var choice = new MesExecutionPathNodeEntity();
                choice.setExecutionId(execution.getId());
                choice.setRoutingNodeId(nodeId);
                choice.setSequenceNo(++sequence);
                choices.add(choice);
                String nodeKey = key("NODE", e.sourceKey() + "/" + n.processId());
                nodeMappings.add(mapping(c.batchId(), nodeKey, executionKey, "NODE", e.sourceKey() + "/" + n.processId(), hash(n.evidence()),
                        "NODE", nodeId, n.evidence(), encode(n)));
            }
            if (selected.insertBatch(choices) != choices.size()) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
            insertMappings(nodeMappings, lookup);
            for (int from = 0; from < e.reports().size(); from += BATCH_SIZE) {
                var sourceReports = e.reports().subList(from, Math.min(from + BATCH_SIZE, e.reports().size()));
                var reportRows = new ArrayList<MesProductionReportEntity>();
                for (var r : sourceReports) {
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
                    reportRows.add(report);
                }
                if (reports.insertBatch(reportRows) != reportRows.size()
                        || reportRows.stream().anyMatch(r -> r.getId() == null)
                        || reportRows.stream().map(MesProductionReportEntity::getId).distinct()
                        .count() != reportRows.size()) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
                var reportMappings = new ArrayList<Mapping>();
                for (int i = 0; i < sourceReports.size(); i++) {
                    var r = sourceReports.get(i);
                    reportMappings.add(mapping(c.batchId(), key("pro_reporting_work", r.sourceId()), key("NODE", e.sourceKey() + "/" + r.processId()),
                            "pro_reporting_work", r.sourceId(), r.sourceHash(), "REPORT", reportRows.get(i)
                                    .getId(), r.sourcePayload(), "{}"));
                }
                insertMappings(reportMappings, lookup);
            }
            var qualityMappings = new ArrayList<Mapping>();
            for (var q : e.defects())
                qualityMappings.add(mapping(c.batchId(), key("qc_defective", q.sourceId()), executionKey,
                        "qc_defective", q.sourceId(), q.sourceHash(), "QUALITY_EVIDENCE", null, q.sourcePayload(), "{}"));
            insertMappings(qualityMappings, lookup);
            imported.put(execution.getId(), e);
            if (lookup.references != null) commits.afterCommit(() -> {
                if (!lookup.references.closed) lookup.references.executions.put(execution.getId(), execution);
            });
        }
        var projectedBatch = quantities.getHistoricalExecutionQuantities(List.copyOf(imported.keySet()));
        for (var item : imported.entrySet()) {
            var e = item.getValue();
            var projected = projectedBatch.get(item.getKey());
            if (!projected.historical() || projected.writable() || projected.nodes().size() != e.nodes().size())
                ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
            for (int i = 0; i < e.nodes().size(); i++) {
                var q = projected.nodes().get(i);
                if (q.historicalConflict() || q.effectiveOutputQty().compareTo(e.nodes().get(i).legacyNet()) != 0
                        || q.availableQty().signum() != 0) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
            }
        }
    }

    private Long unit(LegacyImportCommand c, ReferenceLookup lookup) {
        var old = lookup.identities.get(key("UNIT", c.unitCode()));
        if (old != null) {
            if (!old.sourceHash()
                    .equals(hash(encode(List.of(c.unitCode(), c.unitName())))) || !RULE.equals(old.ruleVersion()))
                ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
            return old.entityId();
        }
        var f = new UnitMeasureExample();
        f.createCriteria().andCodeEqualTo(c.unitCode());
        if (units.countByExample(f) != 0) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        var row = new UnitMeasureEntity();
        row.setCode(c.unitCode());
        row.setName(c.unitName());
        row.setSymbol(c.unitName());
        row.setStatus(EnableStatus.ENABLED.getValue());
        changed(units.insert(row));
        if (lookup.references != null) commits.afterCommit(() -> {
            if (!lookup.references.closed) lookup.references.units.put(row.getId(), row);
        });
        String payload = encode(List.of(c.unitCode(), c.unitName()));
        map(lookup, c.batchId(), key("UNIT", c.unitCode()), null, "UNIT", c.unitCode(), hash(payload), "UNIT", row.getId(), payload, "{}");
        return row.getId();
    }

    private Long product(LegacyImportCommand c, Long unit, ReferenceLookup lookup) {
        var old = lookup.identities.get(key("MATERIAL", c.productCode()));
        if (old != null) {
            if (!old.sourceHash()
                    .equals(hash(encode(List.of(c.productCode(), c.productName(), c.unitCode())))) || !RULE.equals(old.ruleVersion()))
                ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
            return old.entityId();
        }
        var f = new MaterialExample();
        f.createCriteria().andCodeEqualTo(c.productCode());
        if (materials.countByExample(f) != 0) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        var row = new MaterialEntity();
        row.setCode(c.productCode());
        row.setName(c.productName());
        row.setSpecification(c.specification());
        row.setUnitId(unit);
        row.setMaterialType(MaterialType.PRODUCT.getValue());
        row.setStatus(EnableStatus.ENABLED.getValue());
        changed(materials.insert(row));
        if (lookup.references != null) commits.afterCommit(() -> {
            if (!lookup.references.closed) lookup.references.materials.put(row.getId(), row);
        });
        String payload = encode(List.of(c.productCode(), c.productName(), c.unitCode()));
        map(lookup, c.batchId(), key("MATERIAL", c.productCode()), null, "MATERIAL", c.productCode(), hash(payload), "MATERIAL", row.getId(), payload, "{}");
        return row.getId();
    }

    private Long operation(String batch, LegacyImportCommand.Node n, ReferenceLookup lookup) {
        var old = lookup.identities.get(key("PROCESS", n.processId()));
        if (old != null) {
            if (!old.sourceHash()
                    .equals(hash(encode(List.of(n.processId(), n.code(), n.name())))) || !RULE.equals(old.ruleVersion()))
                ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
            return old.entityId();
        }
        if (!lookup.operationCodes.add(n.code())) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        var row = new OperationEntity();
        row.setCode(n.code());
        row.setName(n.name());
        row.setStatus(EnableStatus.ENABLED.getValue());
        row.setSort(0);
        row.setRevision(0);
        changed(operations.insert(row));
        if (lookup.references != null) commits.afterCommit(() -> {
            if (!lookup.references.closed) lookup.references.operations.put(row.getId(), row);
        });
        String payload = encode(List.of(n.processId(), n.code(), n.name()));
        map(lookup, batch, key("PROCESS", n.processId()), null, "PROCESS", n.processId(), hash(payload), "OPERATION", row.getId(), payload, "{}");
        return row.getId();
    }

    private void check(LegacyImportCommand c) {
        if (c == null || !FactoryContext.required().factoryId().value().equals(c.factoryId())
                || c.executions() == null || c.executions().isEmpty() || c.executions().size() > 100
                || c.plannedQty() == null || c.plannedQty().signum() <= 0 || c.plannedEnd().isBefore(c.plannedStart()))
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        if (StringUtils.isBlank(c.code()) || c.code().length() > 64
                || c.snapshotHash() == null || !c.snapshotHash().matches("[0-9a-f]{64}")
                || c.sourceHash() == null || !c.sourceHash().matches("[0-9a-f]{64}")
                || c.executions().stream().mapToInt(e -> e.reports().size()).sum() > 10000
                || c.executions().stream().map(e -> e.inputQty()).reduce(BigDecimal.ZERO, BigDecimal::add)
                .compareTo(c.plannedQty()) > 0)
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        try {
            var source = json.readTree(c.sourcePayload());
            if (!hash(c.sourcePayload()).equals(c.sourceHash()) || !source.path("code").asText().equals(c.code()))
                ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
            var sourceExecutions = new HashSet<JsonNode>();
            for (var original : source.path("executions")) sourceExecutions.add(original);
            for (var e : c.executions()) {
                var frozen = json.readTree(e.sourcePayload());
                if (!hash(e.sourcePayload()).equals(e.sourceHash()) || !encode(frozen.path("key")).equals(e.sourceKey())
                        || !frozen.path("key").get(0).asText().equals(c.code())
                        || new BigDecimal(frozen.path("input_qty_candidate").asText()).compareTo(e.inputQty()) != 0)
                    ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
                if (!sourceExecutions.contains(frozen)) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
                var sourceReports = new HashSet<JsonNode>();
                for (var original : frozen.path("reports")) sourceReports.add(original.path("raw"));
                for (var r : e.reports()) {
                    var raw = json.readTree(r.sourcePayload()).path("report");
                    if (!hash(encode(raw)).equals(r.sourceHash()) || !raw.path("record_id").asText()
                            .equals(r.sourceId())
                            || !raw.path("process_id").asText().equals(r.processId())
                            || !raw.path("user_id").asText().equals(r.operatorUserId().toString())
                            || new BigDecimal(raw.path("reporting_work_quantity").asText()).compareTo(r.quantity()) != 0
                            || !LocalDateTime.parse(raw.path("create_time").asText().replace(' ', 'T'))
                            .equals(r.reportTime())) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
                    if (!sourceReports.contains(raw)) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
                }
                if (frozen.path("reports").size() != e.reports().size()) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
            }
        } catch (JsonProcessingException failure) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        sources.localCreation();
        SecurityUtils.getUserId();
        for (var e : c.executions()) {
            if (e.nodes().isEmpty() || e.nodes().size() > 100 || e.reports().size() > 10000 || e.inputQty()
                    .signum() <= 0) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
            BigDecimal upstream = e.inputQty();
            for (var n : e.nodes()) {
                if (n.legacyNet().signum() < 0 || n.grossCorrection().signum() < 0 || n.effectiveDefect().signum() < 0
                        || n.grossCorrection().compareTo(n.effectiveDefect()) != 0
                        || n.legacyNet().add(n.grossCorrection()).compareTo(upstream) > 0) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
                var total = e.reports().stream().filter(r -> r.processId().equals(n.processId()))
                        .map(r -> r.quantity()).reduce(BigDecimal.ZERO, BigDecimal::add);
                if (total.compareTo(n.legacyNet()) != 0) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
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
                || !batch.operatorUserId().equals(SecurityUtils.getUserId())) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
    }

    private void map(ReferenceLookup lookup, String batch, String id, String parent, String table, String pk,
                     String sourceHash,
                     String kind, Long target, String raw, String conversion) {
        var row = mapping(batch, id, parent, table, pk, sourceHash, kind, target, raw, conversion);
        changed(metadata.insert(row));
        remember(lookup, row);
    }

    private Mapping mapping(String batch, String id, String parent, String table, String pk, String sourceHash,
                            String kind, Long target, String raw, String conversion) {
        return new Mapping(id, batch, parent, table, pk, sourceHash, RULE, kind, target, raw, conversion, LocalDateTime.now());
    }

    private void insertMappings(List<Mapping> rows, ReferenceLookup lookup) {
        for (int from = 0; from < rows.size(); ) {
            int to = from;
            int bytes = 0;
            while (to < rows.size() && to - from < BATCH_SIZE) {
                var row = rows.get(to);
                int size = row.sourcePayload().getBytes(StandardCharsets.UTF_8).length
                        + row.conversionPayload().getBytes(StandardCharsets.UTF_8).length;
                if (to > from && bytes + size > MAPPING_PAYLOAD_BYTES) break;
                bytes += size;
                to++;
            }
            if (metadata.insertBatch(rows.subList(from, to)) != to - from) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
            for (var row : rows.subList(from, to)) remember(lookup, row);
            from = to;
        }
    }

    private ReplayState replayMappings(LegacyImportCommand c, ReferenceLookup lookup) {
        var keys = new TreeSet<String>();
        for (var e : c.executions()) {
            keys.add(key("EXECUTION", e.sourceKey()));
            for (var r : e.reports()) keys.add(key("pro_reporting_work", r.sourceId()));
        }
        var ids = new ArrayList<>(keys);
        var result = new LinkedHashMap<String, MappingIdentity>();
        for (String id : ids) if (lookup.identities.containsKey(id)) result.put(id, lookup.identities.get(id));
        var missing = ids.stream().filter(id -> !result.containsKey(id)).toList();
        readIdentities(missing, result);
        var executionIds = c.executions().stream().map(e -> result.get(key("EXECUTION", e.sourceKey())))
                .filter(Objects::nonNull).map(MappingIdentity::entityId).filter(Objects::nonNull).distinct().toList();
        var rows = new LinkedHashMap<Long, MesProductionExecutionEntity>();
        if (lookup.references != null)
            for (Long id : executionIds)
                if (lookup.references.executions.containsKey(id)) rows.put(id, lookup.references.executions.get(id));
        executionIds = executionIds.stream().filter(id -> !rows.containsKey(id)).toList();
        for (int from = 0; from < executionIds.size(); from += BATCH_SIZE) {
            var f = new MesProductionExecutionExample();
            f.createCriteria().andIdIn(executionIds.subList(from, Math.min(from + BATCH_SIZE, executionIds.size())));
            for (var row : executions.selectByExample(f)) rows.put(row.getId(), row);
        }
        return new ReplayState(result, rows);
    }

    private record ReplayState(Map<String, MappingIdentity> mappings,
                               Map<Long, MesProductionExecutionEntity> executions) {
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
        if (rows != 1) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
    }

}
