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
import dev.mesbase.business.production.domain.BatchRouteNodeEntity;
import java.math.BigDecimal;
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
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * WorkOrder owns demand. Legacy batches and selected paths are read-only historical references.
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
        var allocations = queries.allocationForOrders(rows.stream().map(WorkorderEntity::getId).toList()).stream()
                .collect(Collectors.toMap(WorkOrderModels.Allocation::workorderId, a -> a));
        return new WorkOrderModels.Page(rows.stream().map(w -> {
            var m = materialIndex.get(w.getProductId());
            var r = routingIndex.get(w.getRoutingId());
            return new WorkOrderModels.Summary(w, counts.getOrDefault(w.getId(), 0L)
                    .intValue(), m == null ? null : m.getCode(), m == null ? null : m.getName(), r == null ? null : r.getCode(), r == null ? null : r.getVersion(), allocations.get(w.getId()));
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
                        .toList(), allocation(id));
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public WorkOrderModels.Detail create(WorkOrderModels.Save input) {
        validate(input);
        uniqueCode(input.code(), null);
        var row = new WorkorderEntity();
        row.setStatus("DRAFT");
        row.setRevision(0);
        row.setCreateTime(LocalDateTime.now());
        assign(row, input);
        saveOrder(row, true);
        return detail(row.getId());
    }

    public WorkOrderModels.Detail update(Long id, WorkOrderModels.Save input) {
        var row = order(id, true);
        require("DRAFT".equals(row.getStatus()));
        if (!Objects.equals(row.getRevision(), input.revision())) conflict();
        if (!Objects.equals(row.getProductId(), input.productId()) && hasExecutions(id))
            throw new FoundationException(ErrorCode.EXECUTION_FROZEN);
        if (hasLegacyBatches(id) && (!Objects.equals(row.getProductId(), input.productId())
                || !Objects.equals(row.getRoutingId(), input.routingId())))
            throw new FoundationException(ErrorCode.WORKORDER_LEGACY_REFERENCED);
        validate(input);
        uniqueCode(input.code(), id);
        assign(row, input);
        row.setRevision(row.getRevision() + 1);
        saveOrder(row, false);
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
        if (hasLegacyBatches(id)) throw new FoundationException(ErrorCode.WORKORDER_LEGACY_REFERENCED);
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

    /**
     * Compatibility endpoint: historical selected paths can no longer be written.
     */
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public WorkOrderModels.BatchDetail selectPath(Long id, WorkOrderModels.PathSelection input) {
        batch(id, false);
        throw new FoundationException(ErrorCode.LEGACY_BATCH_READ_ONLY);
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public WorkOrderModels.Allocation allocation(Long id) {
        var rows = queries.allocationForOrders(List.of(id));
        if (rows.isEmpty()) throw new FoundationException(ErrorCode.NOT_FOUND);
        return rows.getFirst();
    }

    private boolean hasLegacyBatches(Long id) {
        var filter = new ProductionBatchExample();
        filter.createCriteria().andWorkorderIdEqualTo(id);
        return batches.countByExample(filter) > 0;
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
        BigDecimal quantity = input.plannedQty();
        require(quantity != null && quantity.signum() > 0 && quantity.scale() <= 6
                && quantity.precision() - quantity.scale() <= 14);
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

}
