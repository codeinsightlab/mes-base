package dev.mesbase.business.production.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.basic.domain.MaterialExample;
import dev.mesbase.business.directory.service.BusinessDirectoryService;
import dev.mesbase.business.production.dao.MesProductionCombinationMapper;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.MesWorkorderMaterialMapper;
import dev.mesbase.business.production.dao.RoutingEdgeMapper;
import dev.mesbase.business.production.dao.RoutingMapper;
import dev.mesbase.business.production.dao.RoutingNodeMapper;
import dev.mesbase.business.production.dao.RoutingProductMapper;
import dev.mesbase.business.production.dao.WorkOrderExtraMapper;
import dev.mesbase.business.production.dao.WorkorderErpExtraMapper;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.domain.MesProductionCombinationExample;
import dev.mesbase.business.production.domain.MesProductionExecutionExample;
import dev.mesbase.business.production.domain.MesWorkorderMaterialExample;
import dev.mesbase.business.production.domain.RoutingDetail;
import dev.mesbase.business.production.domain.RoutingEdgeExample;
import dev.mesbase.business.production.domain.RoutingEntity;
import dev.mesbase.business.production.domain.RoutingExample;
import dev.mesbase.business.production.domain.RoutingNodeExample;
import dev.mesbase.business.production.domain.RoutingProductEntity;
import dev.mesbase.business.production.domain.RoutingProductExample;
import dev.mesbase.business.production.domain.RoutingStatus;
import dev.mesbase.business.production.domain.WorkOrderModels;
import dev.mesbase.business.production.domain.WorkOrderStatus;
import dev.mesbase.business.production.domain.WorkorderEntity;
import dev.mesbase.business.production.domain.WorkorderErpExtraEntity;
import dev.mesbase.business.production.domain.WorkorderErpExtraExample;
import dev.mesbase.business.production.domain.WorkorderExample;
import dev.mesbase.business.production.service.BusinessSourceRules;
import dev.mesbase.business.shared.ExternalSyncAction;
import dev.mesbase.business.shared.ExternalSyncCheck;
import dev.mesbase.business.shared.enums.BusinessSource;
import dev.mesbase.business.shared.enums.EnableStatus;
import dev.mesbase.business.wm.dao.WmStockDocumentMapper;
import dev.mesbase.business.wm.domain.StockDocumentType;
import dev.mesbase.business.wm.domain.WmStockDocumentExample;
import dev.mesbase.common.utils.StringUtils;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.error.SafeBusinessMessage;
import dev.mesbase.framework.sync.SyncCoordinator;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.platform.execution.FactoryIntegrationContext;
import dev.mesbase.system.service.AutoCodeNumbering;
import dev.mesbase.system.service.AutoCodeRuleType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * WorkOrder owns demand; current production facts belong to formal Execution records.
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
@Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
public class WorkOrderService {
    private final BusinessSourceRules sources;
    private final WorkorderErpExtraMapper external;
    private final SyncCoordinator sync;
    private final MesWorkorderMaterialMapper requirements;
    private final WorkorderMaterialService materialDemands;
    private final WorkorderMapper orders;
    private final WorkorderErpFacts facts;
    private final BusinessDirectoryService directory;
    private final RoutingMapper routings;
    private final RoutingProductMapper products;
    private final RoutingNodeMapper nodes;
    private final RoutingEdgeMapper edges;
    private final MaterialMapper materials;
    private final MaterialQuantityRules quantityRules;
    private final RoutingReferenceRules references;
    private final WmStockDocumentMapper issues;
    private final WorkOrderExtraMapper queries;
    private final MesProductionExecutionMapper executions;
    private final MesProductionCombinationMapper combinations;
    private final AutoCodeNumbering autoCodes;
    private final TransactionTemplate createTransactions;

    public WorkOrderService(WorkorderMapper orders, RoutingMapper routings, RoutingProductMapper products,
                            RoutingNodeMapper nodes,
                            RoutingEdgeMapper edges, MaterialMapper materials, RoutingReferenceRules references,
                            WmStockDocumentMapper issues, WorkOrderExtraMapper queries,
                            MesProductionExecutionMapper executions, WorkorderErpFacts facts,
                            BusinessDirectoryService directory, MesWorkorderMaterialMapper requirements,
                            BusinessSourceRules sources, WorkorderErpExtraMapper external, SyncCoordinator sync,
                            MaterialQuantityRules quantityRules, AutoCodeNumbering autoCodes,
                            WorkorderMaterialService materialDemands,
                            MesProductionCombinationMapper combinations,
                            @Qualifier("businessTransactionManager") PlatformTransactionManager businessTransactionManager) {
        this.combinations = combinations;
        this.external = external;
        this.sync = sync;
        this.sources = sources;
        this.requirements = requirements;
        this.materialDemands = materialDemands;
        this.orders = orders;
        this.facts = facts;
        this.directory = directory;
        this.routings = routings;
        this.products = products;
        this.nodes = nodes;
        this.edges = edges;
        this.materials = materials;
        this.quantityRules = quantityRules;
        this.references = references;
        this.issues = issues;
        this.queries = queries;
        this.executions = executions;
        this.autoCodes = autoCodes;
        this.createTransactions = new TransactionTemplate(businessTransactionManager);
        this.createTransactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    private void require(boolean ok) {
        if (!ok) throw new FoundationException(ErrorCode.WORKORDER_INVALID);
    }

    private void conflict() {
        throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public WorkOrderModels.Page list(String code, String status, int offset, int limit, String source) {
        require(offset >= 0 && limit >= 1 && limit <= 100);
        var filter = new WorkorderExample();
        var c = filter.createCriteria();
        if (StringUtils.isNotBlank(code)) c.andCodeLike("%" + code + "%");
        if (StringUtils.isNotBlank(status)) c.andStatusEqualTo(status);
        else c.andStatusNotEqualTo(WorkOrderStatus.CANCELLED.getValue());
        if (source != null) c.andSourceEqualTo(BusinessSourceRules.required(source).getValue());
        filter.setOrderByClause("id desc");
        List<WorkorderEntity> rows;
        long total;
        try (Page<WorkorderEntity> page = PageHelper.offsetPage(offset, limit)) {
            rows = orders.selectByExample(filter);
            total = page.getTotal();
        }
        var missingExtras = facts.hydrateAvailable(rows);
        var materialFilter = new MaterialExample();
        var routingFilter = new RoutingExample();
        if (rows.isEmpty()) return new WorkOrderModels.Page(List.of(), total, offset, limit);
        materialFilter.createCriteria()
                .andIdIn(rows.stream().map(WorkorderEntity::getProductId).distinct().toList());
        routingFilter.createCriteria()
                .andIdIn(rows.stream().map(WorkorderEntity::getRoutingId).distinct().toList());
        var materialIndex = materials.selectByExample(materialFilter).stream()
                .collect(Collectors.toMap(m -> m.getId(), m -> m));
        var routingIndex = routings.selectByExample(routingFilter).stream()
                .collect(Collectors.toMap(r -> r.getId(), r -> r));
        var allocations = queries.allocationForOrders(rows.stream().map(WorkorderEntity::getId).toList()).stream()
                .collect(Collectors.toMap(WorkOrderModels.Allocation::workorderId, a -> a));
        return new WorkOrderModels.Page(rows.stream().map(w -> {
            var m = materialIndex.get(w.getProductId());
            var r = routingIndex.get(w.getRoutingId());
            return new WorkOrderModels.Summary(w, Optional.ofNullable(m).map(nullableValue -> nullableValue.getCode())
                    .orElse(null), Optional.ofNullable(m).map(nullableValue -> nullableValue.getName())
                    .orElse(null), Optional.ofNullable(r).map(nullableValue -> nullableValue.getCode())
                    .orElse(null), Optional.ofNullable(r).map(nullableValue -> nullableValue.getVersion())
                    .orElse(null), allocations.get(w.getId()), missingExtras.contains(w.getId()));
        }).toList(), total, offset, limit);
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public WorkOrderModels.Detail detail(Long id) {
        var w = order(id, false);
        var m = materials.selectByPrimaryKey(w.getProductId());
        return new WorkOrderModels.Detail(w, Optional.ofNullable(m).map(nullableValue -> nullableValue.getCode())
                .orElse(null), Optional.ofNullable(m).map(nullableValue -> nullableValue.getName()).orElse(null),
                routings.selectByPrimaryKey(w.getRoutingId()), allocation(id));
    }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.NOT_SUPPORTED)
    public WorkOrderModels.Detail create(WorkOrderModels.Save input) {
        sources.localCreation();
        if (input == null) throw new FoundationException(ErrorCode.WORKORDER_INVALID);
        if (input.externalId() != null || input.externalCode() != null)
            throw new FoundationException(ErrorCode.EXTERNAL_SOURCE_READ_ONLY);
        validateValues(input);
        if (input.ruleCode() == null || input.ruleCode().isBlank())
            throw new FoundationException(ErrorCode.WORKORDER_INVALID);
        String code = autoCodes.getDocNo(input.ruleCode(), AutoCodeRuleType.WORK_ORDER.name());
        return createTransactions.execute(status -> createLocal(input, code));
    }

    private WorkOrderModels.Detail createLocal(WorkOrderModels.Save input, String code) {
        sources.localCreation();
        if (input.externalId() != null || input.externalCode() != null)
            throw new FoundationException(ErrorCode.EXTERNAL_SOURCE_READ_ONLY);
        validate(input);
        quantityRules.checkForWrite(input.plannedQty(), input.productId());
        var row = new WorkorderEntity();
        row.setCode(code);
        row.setSource(BusinessSource.LOCAL.getValue());
        row.setStatus(WorkOrderStatus.DRAFT.getValue());
        row.setRevision(0);
        row.setMaterialRevision(0L);
        row.setCreateTime(LocalDateTime.now());
        assign(row, input);
        saveOrder(row, true);
        materialDemands.replace(row, input.material());
        return detail(row.getId());
    }

    /**
     * Trusted in-process ERP synchronization. No HTTP endpoint accepts this command.
     */
    public record ExternalPlan(WorkOrderModels.Save values, String externalId, String externalCode,
                               WorkOrderStatus status) {
    }

    public WorkOrderModels.Detail syncExternal(ExternalPlan plan, SyncCoordinator.Run run) {
        if (run == null || !FactoryContext.required().equals(run.scope()) || !"workorder.erp".equals(run.syncKey()))
            throw new FoundationException(ErrorCode.SYNC_EXECUTION_LOST);
        sync.assertOwner(run);
        var checked = checkExternal(plan, true);
        var input = plan.values();
        if (input.code() == null || input.code().isBlank() || input.code().length() > 64) conflict();
        var externalId = plan.externalId();
        var externalCode = plan.externalCode();
        var snapshot = FactoryIntegrationContext.required();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void beforeCommit(boolean readOnly) {
                sync.assertOwner(run);
            }
        });
        if (checked.action() == ExternalSyncAction.UNCHANGED) return detail(checked.localId());
        boolean created = checked.action() == ExternalSyncAction.CREATE;
        var row = created ? new WorkorderEntity() : orders.selectByPrimaryKey(checked.localId());
        if (created) {
            row.setSource(BusinessSource.EXTERNAL.getValue());
            row.setStatus(WorkOrderStatus.DRAFT.getValue());
            row.setRevision(0);
            row.setMaterialRevision(0L);
            row.setCreateTime(LocalDateTime.now());
        }
        row.setStatus(plan.status().getValue());
        row.setCode(input.code());
        row.setProductId(input.productId());
        row.setPlannedQty(input.plannedQty());
        row.setRoutingId(input.routingId());
        row.setPlannedStartAt(input.plannedStartAt());
        row.setPlannedEndAt(input.plannedEndAt());
        row.setRemark(input.remark());
        row.setRevision(row.getRevision() + 1);
        row.setUpdateTime(LocalDateTime.now());
        if ((created ? orders.insert(row) : orders.updateByPrimaryKey(row)) != 1) conflict();
        var mapping = created ? new WorkorderErpExtraEntity() : external.selectByPrimaryKey(row.getId());
        mapping.setId(row.getId());
        mapping.setExternalId(externalId);
        mapping.setExternalCode(externalCode);
        mapping.setProvider(snapshot.erpType());
        mapping.setBindingKey(snapshot.configurationRef());
        mapping.setConfigurationRevision(snapshot.configurationRevision());
        if ((created ? external.insert(mapping) : external.updateByPrimaryKey(mapping)) != 1) conflict();
        sync.assertOwner(run);
        return detail(row.getId());
    }

    /**
     * Exact same preconditions as syncExternal, with SELECT-only reference checks for Dry Run.
     */
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true, noRollbackFor = FoundationException.class)
    public ExternalSyncCheck inspectExternal(ExternalPlan plan) {
        return checkExternal(plan, false);
    }

    private ExternalSyncCheck checkExternal(ExternalPlan plan, boolean lock) {
        sources.current(BusinessSource.EXTERNAL.getValue());
        if (plan == null || plan.values() == null || plan.status() == null
                || plan.status() == WorkOrderStatus.CANCELLED || StringUtils.isBlank(plan.externalId()) || plan.externalId()
                .length() > 128
                || plan.externalCode() == null || plan.externalCode().length() > 128
                || plan.values().externalId() != null || plan.values().externalCode() != null) conflict();
        var input = plan.values();
        if (StringUtils.isBlank(input.code()) || input.code().length() > 64) conflict();
        validate(input, lock);
        var snapshot = FactoryIntegrationContext.required();
        var filter = new WorkorderExample();
        filter.createCriteria().andCodeEqualTo(input.code());
        var found = orders.selectByExample(filter);
        if (found.size() > 1) conflict();
        var identities = new WorkorderErpExtraExample();
        identities.createCriteria().andExternalIdEqualTo(plan.externalId()).andProviderEqualTo(snapshot.erpType())
                .andBindingKeyEqualTo(snapshot.configurationRef());
        var known = external.selectByExample(identities);
        if (known.size() > 1 || known.stream().anyMatch(x -> found.isEmpty()
                || !Objects.equals(x.getId(), found.getFirst().getId()))) conflict();
        if (found.isEmpty()) return new ExternalSyncCheck(ExternalSyncAction.CREATE, null);
        var row = lock ? order(found.getFirst().getId(), true) : found.getFirst();
        if (BusinessSourceRules.required(row.getSource()) != BusinessSource.EXTERNAL
                || WorkOrderStatus.CANCELLED.getValue().equals(row.getStatus())) conflict();
        var mapping = external.selectByPrimaryKey(row.getId());
        if (mapping == null || !Objects.equals(mapping.getExternalId(), plan.externalId())) conflict();
        BusinessSourceRules.binding(mapping.getProvider(), mapping.getBindingKey(), mapping.getConfigurationRevision());
        if (Objects.equals(row.getProductId(), input.productId()) && Objects.equals(row.getRoutingId(), input.routingId())
                && row.getPlannedQty()
                .compareTo(input.plannedQty()) == 0 && Objects.equals(row.getStatus(), plan.status().getValue())
                && Objects.equals(row.getPlannedStartAt(), input.plannedStartAt()) && Objects.equals(row.getPlannedEndAt(), input.plannedEndAt())
                && Objects.equals(row.getRemark(), input.remark()) && Objects.equals(mapping.getExternalCode(), plan.externalCode()))
            return new ExternalSyncCheck(ExternalSyncAction.UNCHANGED, row.getId());
        if (hasExecutions(row.getId()) || queries.countBlockingIssues(row.getId()) > 0)
            throw new FoundationException(ErrorCode.EXECUTION_FROZEN);
        if (!WorkOrderStatus.DRAFT.getValue().equals(row.getStatus()))
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT,
                    new SafeBusinessMessage("工单不处于草稿状态，当前不能修改"));
        return new ExternalSyncCheck(ExternalSyncAction.UPDATE, row.getId());
    }

    public WorkOrderModels.Detail update(Long id, WorkOrderModels.Save input) {
        var row = order(id, true);
        BusinessSourceRules.localEditable(row.getSource());
        sources.current(row.getSource());
        require(WorkOrderStatus.DRAFT.getValue().equals(row.getStatus()));
        require(input != null);
        if (!Objects.equals(row.getRevision(), input.revision())) conflict();
        boolean identityChanged = !Objects.equals(row.getProductId(), input.productId())
                || !Objects.equals(row.getRoutingId(), input.routingId());
        boolean quantityChanged = input.plannedQty() == null || row.getPlannedQty() == null
                || input.plannedQty().compareTo(row.getPlannedQty()) != 0;
        if (identityChanged && hasExecutions(id))
            throw new FoundationException(ErrorCode.EXECUTION_FROZEN);
        if (!Objects.equals(row.getProductId(), input.productId())) {
            if (queries.countBlockingIssues(id) > 0)
                throw new FoundationException(ErrorCode.WORKORDER_ISSUE_REFERENCED);
        }
        validateValues(input);
        if (identityChanged) binding(input.productId(), input.routingId());
        materialDemands.requireUpdatePermission();
        if (!Objects.equals(input.productId(), row.getProductId())
                || input.plannedQty() == null || row.getPlannedQty() == null
                || input.plannedQty().compareTo(row.getPlannedQty()) != 0)
            quantityRules.checkForWrite(input.plannedQty(), input.productId());
        if (quantityChanged) {
            var allocation = queries.activeAllocation(id, null);
            if (allocation == null || allocation.allocatedTargetQty().compareTo(input.plannedQty()) > 0)
                throw new FoundationException(ErrorCode.WORKORDER_QUANTITY_CONFLICT);
        }
        assign(row, input);
        row.setRevision(row.getRevision() + 1);
        saveOrder(row, false);
        materialDemands.replace(row, input.material());
        return detail(id);
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public List<RoutingEntity> applicableRoutings(Long productId, String search) {
        require(productId != null && (search == null || search.length() <= 100));
        return queries.publishedForProduct(productId, search);
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public RoutingDetail graph(Long id) {
        var w = order(id, false);
        var n = new RoutingNodeExample();
        n.createCriteria().andRoutingIdEqualTo(w.getRoutingId());
        var e = new RoutingEdgeExample();
        e.createCriteria().andRoutingIdEqualTo(w.getRoutingId());
        var p = new RoutingProductExample();
        p.createCriteria().andRoutingIdEqualTo(w.getRoutingId());
        var graphNodes = nodes.selectByExample(n);
        var labels = directory.operationLabels(graphNodes.stream().map(node -> node.getOperationId()).toList());
        for (var node : graphNodes) {
            if (node.getOperationName() == null && labels.containsKey(node.getOperationId()))
                node.setOperationName(labels.get(node.getOperationId()).name() + "（当前名称）");
        }
        var graphProducts = products.selectByExample(p);
        var currentProducts = directory.materialEntities(graphProducts.stream().map(RoutingProductEntity::getMaterialId)
                .toList());
        return new RoutingDetail(routings.selectByPrimaryKey(w.getRoutingId()), graphProducts, graphNodes,
                edges.selectByExample(e), currentProducts);
    }

    public WorkOrderModels.Detail release(Long id, int revision) {
        var row = order(id, true);
        BusinessSourceRules.localEditable(row.getSource());
        sources.current(row.getSource());
        if (row.getRevision() != revision) conflict();
        require(WorkOrderStatus.DRAFT.getValue().equals(row.getStatus()));
        binding(row.getProductId(), row.getRoutingId());
        row.setStatus(WorkOrderStatus.RELEASED.getValue());
        row.setRevision(revision + 1);
        row.setUpdateTime(LocalDateTime.now());
        saveOrder(row, false);
        return detail(id);
    }

    public void delete(Long id, int revision) {
        var row = order(id, true);
        BusinessSourceRules.localEditable(row.getSource());
        sources.current(row.getSource());
        if (row.getRevision() != revision) conflict();
        ensureNoDownstream(id);
        var combinationFilter = new MesProductionCombinationExample();
        combinationFilter.createCriteria().andWorkorderIdEqualTo(id);
        if (combinations.countByExample(combinationFilter) > 0)
            throw new FoundationException(ErrorCode.WORKORDER_REFERENCED);
        var materialFilter = new MesWorkorderMaterialExample();
        materialFilter.createCriteria().andWorkorderIdEqualTo(id);
        requirements.deleteByExample(materialFilter);
        facts.delete(id);
        if (orders.deleteByPrimaryKey(id) != 1) conflict();
    }

    public WorkOrderModels.Detail recall(Long id, int revision) {
        var row = order(id, true);
        BusinessSourceRules.localEditable(row.getSource());
        sources.current(row.getSource());
        if (row.getRevision() != revision) conflict();
        require(WorkOrderStatus.RELEASED.getValue().equals(row.getStatus()));
        ensureNoDownstream(id);
        row.setStatus(WorkOrderStatus.DRAFT.getValue());
        row.setRevision(revision + 1);
        row.setUpdateTime(LocalDateTime.now());
        saveOrder(row, false);
        return detail(id);
    }

    private void ensureNoDownstream(Long id) {
        if (hasExecutions(id)) throw new FoundationException(ErrorCode.EXECUTION_REFERENCED);
        var issueFilter = new WmStockDocumentExample();
        issueFilter.createCriteria().andSourceWorkorderIdEqualTo(id)
                .andDocumentTypeEqualTo(StockDocumentType.MATERIAL_ISSUE.getValue());
        if (issues.countByExample(issueFilter) > 0)
            throw new FoundationException(ErrorCode.WORKORDER_ISSUE_REFERENCED);
        if (queries.hasOrderDependents(id))
            throw new FoundationException(ErrorCode.WORKORDER_REFERENCED);
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public WorkOrderModels.Allocation allocation(Long id) {
        var rows = queries.allocationForOrders(List.of(id));
        if (rows.isEmpty()) throw new FoundationException(ErrorCode.NOT_FOUND);
        return rows.getFirst();
    }

    private WorkorderEntity order(Long id, boolean lock) {
        require(id != null);
        var row = lock ? orders.selectByPrimaryKeyForUpdate(id) : orders.selectByPrimaryKey(id);
        if (row == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        facts.hydrate(List.of(row));
        return row;
    }

    private void binding(Long product, Long routing) {
        binding(product, routing, true);
    }

    private void binding(Long product, Long routing, boolean lock) {
        if (lock) {
            quantityRules.lockForWrite(List.of(product));
            references.lockMaterials(List.of(product), true);
        } else {
            var material = materials.selectByPrimaryKey(product);
            require(material != null && EnableStatus.ENABLED.getValue().equals(material.getStatus()));
        }
        var r = lock ? routings.selectByPrimaryKeyForUpdate(routing) : routings.selectByPrimaryKey(routing);
        require(r != null && RoutingStatus.PUBLISHED.getValue().equals(r.getStatus()));
        var p = new RoutingProductExample();
        p.createCriteria().andRoutingIdEqualTo(routing).andMaterialIdEqualTo(product);
        require(products.countByExample(p) > 0);
    }

    private void validate(WorkOrderModels.Save input) {
        validate(input, true);
    }

    private void validate(WorkOrderModels.Save input, boolean lock) {
        validateValues(input);
        binding(input.productId(), input.routingId(), lock);
    }

    private void validateValues(WorkOrderModels.Save input) {
        require(input != null && input.productId() != null && input.routingId() != null);
        BigDecimal quantity = input.plannedQty();
        require(quantity != null && quantity.signum() > 0 && quantity.scale() <= 6
                && quantity.precision() - quantity.scale() <= 14);
        require(input.plannedStartAt() == null || input.plannedEndAt() == null || !input.plannedEndAt()
                .isBefore(input.plannedStartAt()));
    }

    private boolean hasExecutions(Long id) {
        var filter = new MesProductionExecutionExample();
        filter.createCriteria().andWorkorderIdEqualTo(id);
        return executions.countByExample(filter) > 0;
    }

    private void assign(WorkorderEntity row, WorkOrderModels.Save input) {
        row.setProductId(input.productId());
        row.setRoutingId(input.routingId());
        row.setPlannedQty(input.plannedQty());
        row.setPlannedStartAt(input.plannedStartAt());
        row.setPlannedEndAt(input.plannedEndAt());
        if (!Objects.equals(row.getExternalId(), input.externalId()) || !Objects.equals(row.getExternalCode(), input.externalCode()))
            throw new FoundationException(ErrorCode.EXTERNAL_SOURCE_READ_ONLY);
        row.setRemark(input.remark());
        row.setUpdateTime(LocalDateTime.now());
    }

    private void saveOrder(WorkorderEntity row, boolean created) {
        try {
            if ((created ? orders.insert(row) : orders.updateByPrimaryKey(row)) != 1) conflict();
            facts.save(row, created);
        } catch (DuplicateKeyException failure) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, failure);
        }
    }

}
