package dev.mesbase.business.production.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.basic.domain.MaterialExample;
import dev.mesbase.business.issue.dao.MesMaterialIssueMapper;
import dev.mesbase.business.issue.domain.MesMaterialIssueExample;
import dev.mesbase.business.production.dao.BatchRouteNodeMapper;
import dev.mesbase.business.production.dao.ProductionBatchMapper;
import dev.mesbase.business.production.dao.RoutingEdgeMapper;
import dev.mesbase.business.production.dao.RoutingMapper;
import dev.mesbase.business.production.dao.RoutingNodeMapper;
import dev.mesbase.business.production.dao.RoutingProductMapper;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.domain.MesProductionExecutionExample;
import dev.mesbase.business.production.dao.WorkOrderExtraMapper;
import dev.mesbase.business.production.domain.RoutingDetail;
import dev.mesbase.business.production.domain.BatchPathPolicy;
import dev.mesbase.business.production.domain.BatchRouteNodeEntity;
import dev.mesbase.business.production.domain.BatchRouteNodeExample;
import dev.mesbase.business.production.domain.ProductionBatchEntity;
import dev.mesbase.business.production.domain.ProductionBatchExample;
import dev.mesbase.business.production.domain.RoutingEdgeExample;
import dev.mesbase.business.production.domain.RoutingEntity;
import dev.mesbase.business.production.domain.RoutingExample;
import dev.mesbase.business.production.domain.RoutingNodeExample;
import dev.mesbase.business.production.domain.RoutingProductExample;
import dev.mesbase.business.production.domain.WorkOrderModels;
import dev.mesbase.business.production.domain.WorkorderEntity;
import dev.mesbase.business.production.domain.WorkorderExample;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Parent serialization protects quantities, batch membership and path/issue transitions.
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
@Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
public class WorkOrderService {
    private final WorkorderMapper orders;
    private final ProductionBatchMapper batches;
    private final BatchRouteNodeMapper paths;
    private final RoutingMapper routings;
    private final RoutingProductMapper products;
    private final RoutingNodeMapper nodes;
    private final RoutingEdgeMapper edges;
    private final MaterialMapper materials;
    private final RoutingReferenceRules references;
    private final MesMaterialIssueMapper issues;
    private final WorkOrderExtraMapper queries;
    private final MesProductionExecutionMapper executions;

    public WorkOrderService(WorkorderMapper orders, ProductionBatchMapper batches, BatchRouteNodeMapper paths,
                            RoutingMapper routings, RoutingProductMapper products, RoutingNodeMapper nodes,
                            RoutingEdgeMapper edges, MaterialMapper materials, RoutingReferenceRules references,
                            MesMaterialIssueMapper issues, WorkOrderExtraMapper queries,
                            MesProductionExecutionMapper executions) {
        this.orders = orders;
        this.batches = batches;
        this.paths = paths;
        this.routings = routings;
        this.products = products;
        this.nodes = nodes;
        this.edges = edges;
        this.materials = materials;
        this.references = references;
        this.issues = issues;
        this.queries = queries;
        this.executions = executions;
    }

    private void require(boolean ok) {
        if (!ok) throw new FoundationException(ErrorCode.WORKORDER_INVALID);
    }

    private void conflict() {
        throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public WorkOrderModels.Page list(String code, String status, int offset, int limit) {
        require(offset >= 0 && limit >= 1 && limit <= 100);
        var filter = new WorkorderExample();
        var c = filter.createCriteria();
        if (code != null && !code.isBlank()) c.andCodeLike("%" + code + "%");
        if (status != null && !status.isBlank()) c.andStatusEqualTo(status);
        filter.setOrderByClause("id desc");
        List<WorkorderEntity> rows;
        long total;
        try (Page<WorkorderEntity> page = PageHelper.offsetPage(offset, limit)) {
            rows = orders.selectByExample(filter);
            total = page.getTotal();
        } finally {
            PageHelper.clearPage();
        }
        var children = new ProductionBatchExample();
        List<ProductionBatchEntity> all = List.of();
        if (!rows.isEmpty()) {
            children.createCriteria().andWorkorderIdIn(rows.stream().map(WorkorderEntity::getId).toList());
            all = batches.selectByExample(children);
        }
        var counts = all.stream()
                .collect(Collectors.groupingBy(ProductionBatchEntity::getWorkorderId, Collectors.counting()));
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
        return new WorkOrderModels.Page(rows.stream().map(w -> {
            var m = materialIndex.get(w.getProductId());
            var r = routingIndex.get(w.getRoutingId());
            return new WorkOrderModels.Summary(w, counts.getOrDefault(w.getId(), 0L)
                    .intValue(), m == null ? null : m.getCode(), m == null ? null : m.getName(), r == null ? null : r.getCode(), r == null ? null : r.getVersion());
        }).toList(), total, offset, limit);
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public WorkOrderModels.Detail detail(Long id) {
        var w = order(id, false);
        var m = materials.selectByPrimaryKey(w.getProductId());
        var children = batchRows(id);
        var filter = new BatchRouteNodeExample();
        List<BatchRouteNodeEntity> all = List.of();
        if (!children.isEmpty()) {
            filter.createCriteria().andBatchIdIn(children.stream().map(ProductionBatchEntity::getId).toList());
            filter.setOrderByClause("execution_order,id");
            all = paths.selectByExample(filter);
        }
        var grouped = all.stream().collect(Collectors.groupingBy(BatchRouteNodeEntity::getBatchId));
        return new WorkOrderModels.Detail(w, m == null ? null : m.getCode(), m == null ? null : m.getName(), routings.selectByPrimaryKey(w.getRoutingId()),
                children.stream()
                        .map(b -> new WorkOrderModels.BatchDetail(b, grouped.getOrDefault(b.getId(), List.of())))
                        .toList());
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public WorkOrderModels.Detail create(WorkOrderModels.Save input) {
        validate(input);
        require(input.batches().stream().allMatch(b -> b.id() == null));
        uniqueCode(input.code(), null);
        var row = new WorkorderEntity();
        row.setStatus("DRAFT");
        row.setRevision(0);
        row.setCreateTime(LocalDateTime.now());
        assign(row, input);
        saveOrder(row, true);
        saveBatches(row, input.batches());
        return detail(row.getId());
    }

    public WorkOrderModels.Detail update(Long id, WorkOrderModels.Save input) {
        var row = order(id, true);
        require("DRAFT".equals(row.getStatus()));
        if (!Objects.equals(row.getRevision(), input.revision())) conflict();
        if (!Objects.equals(row.getProductId(), input.productId()) && hasExecutions(id))
            throw new FoundationException(ErrorCode.EXECUTION_FROZEN);
        validate(input);
        uniqueCode(input.code(), id);
        assign(row, input);
        row.setRevision(row.getRevision() + 1);
        saveOrder(row, false);
        saveBatches(row, input.batches());
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
        return new RoutingDetail(routings.selectByPrimaryKey(w.getRoutingId()), products.selectByExample(p), nodes.selectByExample(n), edges.selectByExample(e));
    }

    public WorkOrderModels.Detail release(Long id, int revision) {
        var row = order(id, true);
        if (row.getRevision() != revision) conflict();
        require("DRAFT".equals(row.getStatus()));
        binding(row.getProductId(), row.getRoutingId());
        var children = batchRows(id);
        BatchPathPolicy.allocation(row.getPlannedQty(), children.stream()
                .map(b -> new WorkOrderModels.BatchInput(b.getId(), b.getBatchNo(), b.getPlannedQty(), b.getRemark()))
                .toList());
        row.setStatus("RELEASED");
        row.setRevision(revision + 1);
        row.setUpdateTime(LocalDateTime.now());
        saveOrder(row, false);
        return detail(id);
    }

    public void delete(Long id, int revision) {
        var row = order(id, true);
        if (row.getRevision() != revision) conflict();
        require("DRAFT".equals(row.getStatus()));
        if (hasExecutions(id)) throw new FoundationException(ErrorCode.EXECUTION_REFERENCED);
        var issueFilter = new MesMaterialIssueExample();
        issueFilter.createCriteria().andWorkorderIdEqualTo(id);
        require(issues.countByExample(issueFilter) == 0);
        var children = batchRows(id);
        require(children.stream().allMatch(b -> "UNSELECTED".equals(b.getRouteStatus())));
        var filter = new ProductionBatchExample();
        filter.createCriteria().andWorkorderIdEqualTo(id);
        batches.deleteByExample(filter);
        orders.deleteByPrimaryKey(id);
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public WorkOrderModels.BatchDetail batchDetail(Long id) {
        var b = batch(id, false);
        var filter = new BatchRouteNodeExample();
        filter.createCriteria().andBatchIdEqualTo(id);
        filter.setOrderByClause("execution_order,id");
        return new WorkOrderModels.BatchDetail(b, paths.selectByExample(filter));
    }

    public WorkOrderModels.BatchDetail selectPath(Long id, WorkOrderModels.PathSelection input) {
        var b = batch(id, true);
        var w = order(b.getWorkorderId(), false);
        require("RELEASED".equals(w.getStatus()));
        if (!List.of("UNSELECTED", "SELECTED").contains(b.getRouteStatus()))
            throw new FoundationException(ErrorCode.BATCH_ROUTE_FROZEN);
        if (!Objects.equals(input.revision(), b.getRevision())) conflict();
        var n = new RoutingNodeExample();
        n.createCriteria().andRoutingIdEqualTo(w.getRoutingId());
        var e = new RoutingEdgeExample();
        e.createCriteria().andRoutingIdEqualTo(w.getRoutingId());
        BatchPathPolicy.validate(input.nodeIds(), nodes.selectByExample(n), edges.selectByExample(e));
        var old = new BatchRouteNodeExample();
        old.createCriteria().andBatchIdEqualTo(id);
        paths.deleteByExample(old);
        int sequence = 0;
        for (Long node : input.nodeIds()) {
            var row = new BatchRouteNodeEntity();
            row.setBatchId(id);
            row.setRoutingNodeId(node);
            row.setExecutionOrder(++sequence);
            paths.insert(row);
        }
        b.setRouteStatus("SELECTED");
        b.setIssueCommandId(null);
        touch(b);
        return batchDetail(id);
    }

    /**
     * Trusted server use only: persist BEFORE dispatching an external command. No HTTP endpoint.
     */
    public WorkOrderModels.BatchDetail beginIssue(Long batchId, int revision) {
        String commandId = UUID.randomUUID().toString();
        var b = batch(batchId, true);
        if (b.getRevision() != revision) conflict();
        require("SELECTED".equals(b.getRouteStatus()));
        require(!commandId.equals(b.getIssueCommandId()));
        b.setIssueCommandId(commandId);
        b.setRouteStatus("PENDING");
        touch(b);
        return batchDetail(batchId);
    }

    /**
     * Caller must have authenticated the external result; this service never accepts a browser success claim.
     */
    public WorkOrderModels.BatchDetail confirmIssue(Long batchId, String commandId,
                                                    WorkOrderModels.IssueResult result) {
        require(result != null && commandId != null);
        var b = batch(batchId, true);
        require(commandId.equals(b.getIssueCommandId()));
        if ("LOCKED".equals(b.getRouteStatus())) {
            require(result == WorkOrderModels.IssueResult.SUCCESS);
            return batchDetail(batchId);
        }
        if ("SELECTED".equals(b.getRouteStatus())) {
            require(result == WorkOrderModels.IssueResult.REJECTED);
            return batchDetail(batchId);
        }
        require(List.of("PENDING", "UNKNOWN").contains(b.getRouteStatus()));
        if (result == WorkOrderModels.IssueResult.SUCCESS) {
            b.setRouteStatus("LOCKED");
            b.setStatus("ISSUED");
            b.setLockedAt(LocalDateTime.now());
        } else if (result == WorkOrderModels.IssueResult.REJECTED) b.setRouteStatus("SELECTED");
        else {
            if ("UNKNOWN".equals(b.getRouteStatus())) return batchDetail(batchId);
            b.setRouteStatus("UNKNOWN");
        }
        touch(b);
        return batchDetail(batchId);
    }

    private void touch(ProductionBatchEntity b) {
        b.setRevision(b.getRevision() + 1);
        b.setUpdateTime(LocalDateTime.now());
        if (batches.updateByPrimaryKey(b) != 1) conflict();
    }

    private WorkorderEntity order(Long id, boolean lock) {
        require(id != null);
        if (lock) {
            var f = new WorkorderExample();
            f.createCriteria().andIdEqualTo(id);
            var patch = new WorkorderEntity();
            patch.setId(id);
            if (orders.updateByExampleSelective(patch, f) != 1) throw new FoundationException(ErrorCode.NOT_FOUND);
        }
        var row = orders.selectByPrimaryKey(id);
        if (row == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        return row;
    }

    private ProductionBatchEntity batch(Long id, boolean lock) {
        var row = batches.selectByPrimaryKey(id);
        if (row == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        order(row.getWorkorderId(), lock);
        if (lock) row = batches.selectByPrimaryKey(id);
        return row;
    }

    private List<ProductionBatchEntity> batchRows(Long id) {
        var f = new ProductionBatchExample();
        f.createCriteria().andWorkorderIdEqualTo(id);
        f.setOrderByClause("id");
        return batches.selectByExample(f);
    }

    private void binding(Long product, Long routing) {
        references.lockMaterials(List.of(product), true);
        var m = materials.selectByPrimaryKey(product);
        require("PRODUCT".equals(m.getMaterialType()));
        var f = new RoutingExample();
        f.createCriteria().andIdEqualTo(routing);
        var patch = new RoutingEntity();
        patch.setId(routing);
        require(routings.updateByExampleSelective(patch, f) == 1);
        var r = routings.selectByPrimaryKey(routing);
        require("PUBLISHED".equals(r.getStatus()));
        var p = new RoutingProductExample();
        p.createCriteria().andRoutingIdEqualTo(routing).andMaterialIdEqualTo(product);
        require(products.countByExample(p) > 0);
    }

    private void validate(WorkOrderModels.Save input) {
        require(input != null && input.productId() != null && input.routingId() != null && input.code() != null && !input.code()
                .isBlank() && input.code().length() <= 64);
        BatchPathPolicy.allocation(input.plannedQty(), input.batches());
        require(input.plannedStartAt() == null || input.plannedEndAt() == null || !input.plannedEndAt()
                .isBefore(input.plannedStartAt()));
        binding(input.productId(), input.routingId());
    }

    private boolean hasExecutions(Long id) {
        var filter = new MesProductionExecutionExample();
        filter.createCriteria().andWorkorderIdEqualTo(id);
        return executions.countByExample(filter) > 0;
    }

    private void uniqueCode(String code, Long id) {
        var f = new WorkorderExample();
        var c = f.createCriteria().andCodeEqualTo(code.trim());
        if (id != null) c.andIdNotEqualTo(id);
        if (orders.countByExample(f) > 0) conflict();
    }

    private void assign(WorkorderEntity row, WorkOrderModels.Save input) {
        row.setCode(input.code().trim());
        row.setProductId(input.productId());
        row.setRoutingId(input.routingId());
        row.setPlannedQty(input.plannedQty());
        row.setPlannedStartAt(input.plannedStartAt());
        row.setPlannedEndAt(input.plannedEndAt());
        row.setExternalId(input.externalId());
        row.setExternalCode(input.externalCode());
        row.setRemark(input.remark());
        row.setUpdateTime(LocalDateTime.now());
    }

    private void saveOrder(WorkorderEntity row, boolean created) {
        try {
            if ((created ? orders.insert(row) : orders.updateByPrimaryKey(row)) != 1) conflict();
        } catch (DuplicateKeyException failure) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, failure);
        }
    }

    private void saveBatch(ProductionBatchEntity row) {
        try {
            if ((row.getId() == null ? batches.insert(row) : batches.updateByPrimaryKey(row)) != 1) conflict();
        } catch (DuplicateKeyException failure) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, failure);
        }
    }

    private void saveBatches(WorkorderEntity w, List<WorkOrderModels.BatchInput> input) {
        var old = batchRows(w.getId());
        var seen = new HashSet<Long>();
        require(old.stream().allMatch(b -> "UNSELECTED".equals(b.getRouteStatus())));
        // Remove unreferenced draft children before inserts; preserve stable IDs for unchanged business batch numbers.
        for (var previous : old)
            if (input.stream().noneMatch(b -> Objects.equals(b.id(), previous.getId())
                    || b.id() == null && b.batchNo().trim().equalsIgnoreCase(previous.getBatchNo())))
                batches.deleteByPrimaryKey(previous.getId());
        for (var b : input) {
            ProductionBatchEntity row;
            Long existingId = b.id();
            if (existingId == null)
                existingId = old.stream().filter(x -> x.getBatchNo().equalsIgnoreCase(b.batchNo().trim()))
                        .map(ProductionBatchEntity::getId).findFirst().orElse(null);
            if (existingId != null) {
                Long resolvedId = existingId;
                require(seen.add(resolvedId));
                row = old.stream().filter(x -> x.getId().equals(resolvedId)).findFirst()
                        .orElseThrow(() -> new FoundationException(ErrorCode.WORKORDER_INVALID));
                require("UNSELECTED".equals(row.getRouteStatus()));
                row.setRevision(row.getRevision() + 1);
            } else {
                row = new ProductionBatchEntity();
                row.setWorkorderId(w.getId());
                row.setStatus("PLANNED");
                row.setRouteStatus("UNSELECTED");
                row.setRevision(0);
                row.setCreateTime(LocalDateTime.now());
            }
            row.setBatchNo(b.batchNo().trim());
            row.setPlannedQty(b.plannedQty());
            row.setRemark(b.remark());
            row.setUpdateTime(LocalDateTime.now());
            saveBatch(row);
        }

    }
}
