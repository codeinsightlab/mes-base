package dev.mesbase.business.production.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.production.dao.RoutingMapper;
import dev.mesbase.business.production.domain.RoutingCreateCommand;
import dev.mesbase.business.production.domain.RoutingEntity;
import dev.mesbase.business.production.domain.RoutingExample;
import dev.mesbase.business.production.domain.RoutingPage;
import dev.mesbase.business.production.domain.RoutingQueryDto;
import dev.mesbase.business.production.domain.RoutingResource;
import dev.mesbase.business.production.domain.RoutingUpdateCommand;
import dev.mesbase.business.production.service.IRoutingService;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.infrastructure.cache.annotation.BusinessCache;
import dev.mesbase.infrastructure.cache.annotation.BusinessCacheEvict;
import dev.mesbase.platform.execution.FactoryContext;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import dev.mesbase.business.production.dao.RoutingNodeMapper;
import dev.mesbase.business.production.dao.RoutingEdgeMapper;
import dev.mesbase.business.production.domain.RoutingEdgeEntity;
import dev.mesbase.business.production.domain.RoutingEdgeExample;
import dev.mesbase.business.production.domain.RoutingEdgeCommand;
import dev.mesbase.business.production.domain.RoutingGraph;
import dev.mesbase.business.production.service.RoutingGraphValidator;
import dev.mesbase.business.production.dao.RoutingProductMapper;
import dev.mesbase.business.production.dao.OperationMapper;
import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.basic.domain.MaterialExample;
import dev.mesbase.business.basic.domain.MaterialEntity;
import dev.mesbase.business.production.domain.RoutingNodeEntity;
import dev.mesbase.business.production.domain.RoutingNodeExample;
import dev.mesbase.business.production.domain.RoutingProductEntity;
import dev.mesbase.business.production.domain.RoutingProductExample;
import dev.mesbase.business.production.domain.OperationExample;
import dev.mesbase.business.production.domain.OperationEntity;
import dev.mesbase.business.production.domain.RoutingDetail;
import dev.mesbase.business.production.domain.RoutingReference;
import dev.mesbase.business.production.domain.RoutingContentCommand;
import dev.mesbase.business.production.domain.RoutingNodeCommand;
import dev.mesbase.business.production.domain.RoutingCopyCommand;
import dev.mesbase.business.production.service.RoutingReferenceRules;
import dev.mesbase.business.production.sync.RoutingQueryPort;
import dev.mesbase.business.production.sync.UnconfiguredRoutingQueryAdapter;
import dev.mesbase.business.production.sync.ExternalRouting;
import dev.mesbase.business.production.sync.ProductionSyncResult;
import dev.mesbase.framework.sync.SyncCoordinator;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.infrastructure.cache.BusinessCacheProcessor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Isolation;
import jakarta.validation.Validator;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.Locale;
import java.util.Comparator;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

/**
 * 工艺路线 CRUD：规则与事务由 Service 负责，Mapper 通过 MyBatis-Spring 注入。
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class RoutingServiceImpl implements IRoutingService {
    private final RoutingMapper mapper;
    private final RoutingNodeMapper nodes;
    private final RoutingEdgeMapper edges;
    private final RoutingProductMapper products;
    private final OperationMapper operations;
    private final MaterialMapper materials;
    private final RoutingReferenceRules references;
    private final RoutingQueryPort query;
    private final SyncCoordinator coordinator;
    private final ObjectProvider<IRoutingService> self;
    private final Validator validator;
    private final BusinessCacheProcessor caches;

    public RoutingServiceImpl(RoutingMapper mapper, RoutingNodeMapper nodes, RoutingEdgeMapper edges,
                              RoutingProductMapper products,
                              OperationMapper operations, MaterialMapper materials, RoutingReferenceRules references,
                              ObjectProvider<RoutingQueryPort> queries, SyncCoordinator coordinator,
                              ObjectProvider<IRoutingService> self, Validator validator,
                              BusinessCacheProcessor caches) {
        this.nodes = nodes;
        this.edges = edges;
        this.products = products;
        this.operations = operations;
        this.materials = materials;
        this.references = references;
        this.query = queries.getIfAvailable(UnconfiguredRoutingQueryAdapter::new);
        this.coordinator = coordinator;
        this.self = self;
        this.validator = validator;
        this.caches = caches;
        this.mapper = mapper;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    @BusinessCache(business = RoutingResource.PREFIX,
            type = BusinessCache.Type.LIST)
    public RoutingPage selectPage(RoutingQueryDto query) {
        int offset = query.offset() == null ? 0 : query.offset();
        int limit = query.limit() == null ? 20 : query.limit();
        if (offset < 0 || limit < 1 || limit > 100) {
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        RoutingExample example = new RoutingExample();
        RoutingExample.Criteria criteria = example.createCriteria();
        if (query.code() != null) {
            criteria.andCodeLike("%" + query.code() + "%");
        }
        if (query.name() != null) {
            criteria.andNameLike("%" + query.name() + "%");
        }
        if (query.version() != null) {
            criteria.andVersionEqualTo(query.version());
        }
        if (query.status() != null) {
            criteria.andStatusLike("%" + query.status() + "%");
        }
        if (query.description() != null) {
            criteria.andDescriptionLike("%" + query.description() + "%");
        }
        if (query.externalId() != null) {
            criteria.andExternalIdLike("%" + query.externalId() + "%");
        }
        if (query.sourceVersion() != null) {
            criteria.andSourceVersionLike("%" + query.sourceVersion() + "%");
        }
        if (query.revision() != null) {
            criteria.andRevisionEqualTo(query.revision());
        }
        if (query.publishedAt() != null) {
            criteria.andPublishedAtEqualTo(query.publishedAt());
        }
        example.setOrderByClause("id");
        try (Page<RoutingEntity> page = PageHelper.offsetPage(offset, limit)) {
            List<RoutingEntity> rows = mapper.selectByExample(example);
            return new RoutingPage(rows, page.getTotal(), offset, limit);
        } finally {
            PageHelper.clearPage();
        }
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED,
            rollbackFor = Exception.class)
    public RoutingEntity insert(RoutingCreateCommand input) {
        var row = new RoutingEntity();

        row.setCode(input.code().strip());
        row.setName(input.name().strip());
        row.setDescription(input.description());
        row.setVersion(1);
        row.setStatus("DRAFT");
        row.setRevision(0);
        insertRow(row);
        return row;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED,
            rollbackFor = Exception.class)
    public RoutingEntity updateById(Long id, RoutingUpdateCommand input) {
        var row = touch(id, input.revision(), true);
        row.setName(input.name().strip());
        row.setDescription(input.description());
        saveRow(row);
        return row;
    }

    @Override
    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED,
            rollbackFor = Exception.class)
    public void deleteByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(Objects::isNull)
                || new HashSet<>(ids).size() != ids.size()) throw new FoundationException(ErrorCode.BAD_REQUEST);
        // Lock and re-read every parent before deleting any children; whole batch rolls back on failure.
        for (Long id : ids.stream().sorted().toList()) {
            var row = requireRecord(id);
            touch(id, row.getRevision(), true);
        }
        var stepFilter = new RoutingNodeExample();
        stepFilter.createCriteria().andRoutingIdIn(ids);
        var edgeFilter = new RoutingEdgeExample();
        edgeFilter.createCriteria().andRoutingIdIn(ids);
        edges.deleteByExample(edgeFilter);
        nodes.deleteByExample(stepFilter);
        var productFilter = new RoutingProductExample();
        productFilter.createCriteria().andRoutingIdIn(ids);
        products.deleteByExample(productFilter);
        var filter = new RoutingExample();
        filter.createCriteria().andIdIn(ids).andStatusEqualTo("DRAFT");
        if (mapper.deleteByExample(filter) != ids.size()) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        dirty();
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public RoutingDetail detail(Long id) {
        return detailRows(requireRecord(id));
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED,
            rollbackFor = Exception.class)
    public RoutingDetail saveContent(Long id, RoutingContentCommand input) {
        var row = touch(id, input.revision(), true);
        replaceContent(row, input.materialIds(), input.nodes(), input.edges());
        return detailRows(row);
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED,
            rollbackFor = Exception.class)
    public RoutingEntity publish(Long id, Integer revision) {
        var row = touch(id, revision, true);
        var detail = detailRows(row);
        if (detail.products().isEmpty() || detail.nodes().isEmpty()) {
            throw new FoundationException(ErrorCode.ROUTING_PUBLISH_INCOMPLETE);
        }
        var graph = graph(detail);
        RoutingGraphValidator.validate(graph.nodes(), graph.edges(), true);
        var materialRows = references.lockMaterials(detail.products().stream()
                .map(RoutingProductEntity::getMaterialId).sorted().toList(), true);
        var operationRows = references.lockOperations(detail.nodes().stream()
                .map(RoutingNodeEntity::getOperationId).distinct().sorted().toList(), true);
        var materialIndex = new HashMap<Long, MaterialEntity>();
        for (var material : materialRows) materialIndex.put(material.getId(), material);
        var operationIndex = new HashMap<Long, OperationEntity>();
        for (var operation : operationRows) operationIndex.put(operation.getId(), operation);
        // Bounded maximum: 100 products + 100 steps, each update checks its exact generated primary key.
        for (var product : detail.products()) {
            var material = materialIndex.get(product.getMaterialId());
            product.setMaterialCode(material.getCode());
            product.setMaterialName(material.getName());
            changed(products.updateByPrimaryKey(product));
        }
        for (var step : detail.nodes()) {
            var operation = operationIndex.get(step.getOperationId());
            step.setOperationCode(operation.getCode());
            step.setOperationName(operation.getName());
            step.setOperationDescription(operation.getDescription());
            changed(nodes.updateByPrimaryKey(step));
        }
        row.setStatus("PUBLISHED");
        row.setPublishedAt(LocalDateTime.now());
        saveRow(row);
        return row;
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED,
            rollbackFor = Exception.class)
    public RoutingEntity availability(Long id, Integer revision, boolean enabled) {
        var row = touch(id, revision, false);
        if (row.getPublishedAt() == null || !Set.of("PUBLISHED", "DISABLED").contains(row.getStatus())) {
            throw new FoundationException(ErrorCode.ROUTING_FROZEN);
        }
        row.setStatus(enabled ? "PUBLISHED" : "DISABLED");
        saveRow(row);
        return row;
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED,
            rollbackFor = Exception.class)
    public RoutingEntity newVersion(Long id, Integer revision) {
        var source = requireRecord(id);
        if (!Objects.equals(source.getRevision(), revision))
            throw new FoundationException(ErrorCode.ROUTING_REVISION_CONFLICT);
        if (source.getPublishedAt() == null) throw new FoundationException(ErrorCode.ROUTING_FROZEN);
        var latest = latest(source.getCode());
        if ("DRAFT".equals(latest.getStatus())) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        return cloneVersion(source, source.getCode(), source.getName(), latest.getVersion() + 1, false);
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED,
            rollbackFor = Exception.class)
    public RoutingEntity copy(Long id, RoutingCopyCommand input) {
        var source = requireRecord(id);
        // Acquire a parent lock so copying a draft cannot interleave with a content replacement.
        source = touch(id, input.revision(), false);
        return cloneVersion(source, input.code().strip(), input.name().strip(), 1, true);
    }

    private RoutingEntity cloneVersion(RoutingEntity source, String code, String name, int version, boolean copy) {
        var sourceDetail = detailRows(source);
        var row = new RoutingEntity();

        row.setCode(code);
        row.setName(name);
        row.setVersion(version);
        row.setStatus("DRAFT");
        row.setRevision(0);
        row.setDescription(source.getDescription());
        if (!copy) {
            row.setExternalId(source.getExternalId());
            row.setSourceVersion(source.getSourceVersion());
        }
        insertRow(row);
        var graph = graph(sourceDetail);
        replaceContent(row, sourceDetail.products().stream().map(RoutingProductEntity::getMaterialId).toList(),
                graph.nodes(), graph.edges());
        return row;
    }

    private void replaceContent(RoutingEntity parent, List<Long> materialIds, List<RoutingNodeCommand> input,
                                List<RoutingEdgeCommand> connections) {
        if (!"DRAFT".equals(parent.getStatus())) throw new FoundationException(ErrorCode.ROUTING_FROZEN);
        if (materialIds == null || materialIds.size() > 100 || materialIds.stream().anyMatch(Objects::isNull)
                || new HashSet<>(materialIds).size() != materialIds.size())
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        RoutingGraphValidator.validate(input, connections, false);
        references.lockMaterials(materialIds.stream().sorted().toList(), true);
        references.lockOperations(input.stream().map(RoutingNodeCommand::operationId).distinct().sorted()
                .toList(), true);
        var existing = detailRows(parent).nodes();
        var nodeIndex = new HashMap<String, RoutingNodeEntity>();
        for (var node : existing) nodeIndex.put(node.getNodeCode(), node);
        var edgeFilter = new RoutingEdgeExample();
        edgeFilter.createCriteria().andRoutingIdEqualTo(parent.getId());
        edges.deleteByExample(edgeFilter);
        var retained = input.stream().map(RoutingNodeCommand::nodeCode).collect(Collectors.toSet());
        var removed = existing.stream().filter(node -> !retained.contains(node.getNodeCode()))
                .map(RoutingNodeEntity::getId).toList();
        if (!removed.isEmpty()) {
            var filter = new RoutingNodeExample();
            filter.createCriteria().andRoutingIdEqualTo(parent.getId()).andIdIn(removed);
            if (nodes.deleteByExample(filter) != removed.size())
                throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        var productFilter = new RoutingProductExample();
        productFilter.createCriteria().andRoutingIdEqualTo(parent.getId());
        products.deleteByExample(productFilter);
        for (Long material : materialIds) {
            var product = new RoutingProductEntity();
            product.setRoutingId(parent.getId());
            product.setMaterialId(material);
            changed(products.insert(product));
        }
        // At most 100 nodes and 400 edges; generated Mapper writes remain inside the parent CAS transaction.
        for (var item : input) {
            var node = nodeIndex.get(item.nodeCode());
            boolean inserting = node == null;
            if (inserting) node = new RoutingNodeEntity();
            node.setRoutingId(parent.getId());
            node.setNodeCode(item.nodeCode());
            node.setOperationId(item.operationId());
            node.setIsKey(item.isKey());
            node.setDescription(item.description());
            node.setDisplayOrder(item.displayOrder());
            if (inserting) changed(nodes.insert(node));
            else changed(nodes.updateByPrimaryKey(node));
            nodeIndex.put(item.nodeCode(), node);
        }
        for (var item : connections) {
            var edge = new RoutingEdgeEntity();
            edge.setRoutingId(parent.getId());
            edge.setFromNodeId(nodeIndex.get(item.fromNodeCode()).getId());
            edge.setToNodeId(nodeIndex.get(item.toNodeCode()).getId());
            changed(edges.insert(edge));
        }
        dirty();
    }

    private RoutingGraph graph(RoutingDetail detail) {
        var index = new HashMap<Long, String>();
        for (var node : detail.nodes()) {
            if (!Objects.equals(node.getRoutingId(), detail.routing().getId()))
                throw new FoundationException(ErrorCode.ROUTING_GRAPH_INVALID);
            index.put(node.getId(), node.getNodeCode());
        }
        var commands = detail.nodes().stream().map(node -> new RoutingNodeCommand(node.getNodeCode(),
                node.getOperationId(), node.getIsKey(), node.getDescription(), node.getDisplayOrder())).toList();
        var connections = detail.edges().stream().map(edge -> {
            if (!Objects.equals(edge.getRoutingId(), detail.routing().getId()))
                throw new FoundationException(ErrorCode.ROUTING_GRAPH_INVALID);
            return new RoutingEdgeCommand(index.get(edge.getFromNodeId()), index.get(edge.getToNodeId()));
        }).toList();
        RoutingGraphValidator.validate(commands, connections, false);
        return new RoutingGraph(commands, connections);
    }

    private RoutingEntity touch(Long id, Integer revision, boolean draftOnly) {
        var old = requireRecord(id);
        if (draftOnly && !"DRAFT".equals(old.getStatus())) throw new FoundationException(ErrorCode.ROUTING_FROZEN);
        if (!Objects.equals(old.getRevision(), revision))
            throw new FoundationException(ErrorCode.ROUTING_REVISION_CONFLICT);
        var filter = byId(id);
        filter.getOredCriteria().getFirst().andRevisionEqualTo(revision);
        if (draftOnly) filter.getOredCriteria().getFirst().andStatusEqualTo("DRAFT");
        var update = new RoutingEntity();
        update.setRevision(revision + 1);
        if (mapper.updateByExampleSelective(update, filter) != 1)
            throw new FoundationException(ErrorCode.ROUTING_REVISION_CONFLICT);
        dirty();
        return requireRecord(id);
    }

    private RoutingDetail detailRows(RoutingEntity row) {
        var stepFilter = new RoutingNodeExample();
        stepFilter.createCriteria().andRoutingIdEqualTo(row.getId());
        stepFilter.setOrderByClause("display_order, node_code");
        List<RoutingNodeEntity> stepRows;
        try (Page<RoutingNodeEntity> page = PageHelper.offsetPage(0, 101, false)) {
            stepRows = nodes.selectByExample(stepFilter);
        } finally {
            PageHelper.clearPage();
        }
        var productFilter = new RoutingProductExample();
        productFilter.createCriteria().andRoutingIdEqualTo(row.getId());
        List<RoutingProductEntity> productRows;
        try (Page<RoutingProductEntity> page = PageHelper.offsetPage(0, 101, false)) {
            productRows = products.selectByExample(productFilter);
        } finally {
            PageHelper.clearPage();
        }
        if (stepRows.size() > 100 || productRows.size() > 100)
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        if ("DRAFT".equals(row.getStatus())) {
            if (!stepRows.isEmpty()) {
                var filter = new OperationExample();
                filter.createCriteria()
                        .andIdIn(stepRows.stream().map(RoutingNodeEntity::getOperationId).toList());
                var index = new HashMap<Long, OperationEntity>();
                for (var op : operations.selectByExample(filter)) index.put(op.getId(), op);
                for (var step : stepRows) {
                    var op = index.get(step.getOperationId());
                    if (op != null) {
                        step.setOperationCode(op.getCode());
                        step.setOperationName(op.getName());
                        step.setOperationDescription(op.getDescription());
                    }
                }
            }
            if (!productRows.isEmpty()) {
                var filter = new MaterialExample();
                filter.createCriteria()
                        .andIdIn(productRows.stream().map(RoutingProductEntity::getMaterialId).toList());
                var index = new HashMap<Long, MaterialEntity>();
                for (var material : materials.selectByExample(filter)) index.put(material.getId(), material);
                for (var product : productRows) {
                    var material = index.get(product.getMaterialId());
                    if (material != null) {
                        product.setMaterialCode(material.getCode());
                        product.setMaterialName(material.getName());
                    }
                }
            }
        }
        var edgeFilter = new RoutingEdgeExample();
        edgeFilter.createCriteria().andRoutingIdEqualTo(row.getId());
        List<RoutingEdgeEntity> edgeRows;
        try (Page<RoutingEdgeEntity> page = PageHelper.offsetPage(0, 401, false)) {
            edgeRows = edges.selectByExample(edgeFilter);
        } finally {
            PageHelper.clearPage();
        }
        if (edgeRows.size() > 400) throw new FoundationException(ErrorCode.ROUTING_GRAPH_INVALID);
        var detail = new RoutingDetail(row, productRows, stepRows, edgeRows);
        graph(detail);
        return detail;
    }

    private String factory() {
        return FactoryContext.required().factoryId().value();
    }

    private RoutingExample byId(Long id) {
        var filter = new RoutingExample();
        filter.createCriteria().andIdEqualTo(id);
        return filter;
    }

    private RoutingEntity requireRecord(Long id) {
        var row = mapper.selectByPrimaryKey(id);
        if (row == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        return row;
    }

    private RoutingEntity latest(String code) {
        var filter = new RoutingExample();
        filter.createCriteria().andCodeEqualTo(code);
        filter.setOrderByClause("version desc");
        try (Page<RoutingEntity> page = PageHelper.offsetPage(0, 1, false)) {
            return mapper.selectByExample(filter).stream().findFirst().orElse(null);
        } finally {
            PageHelper.clearPage();
        }
    }

    private void insertRow(RoutingEntity row) {
        try {
            changed(mapper.insert(row));
        } catch (DuplicateKeyException failure) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, failure);
        }
        dirty();
    }

    private void saveRow(RoutingEntity row) {
        changed(mapper.updateByExample(row, byId(row.getId())));
        dirty();
    }

    private void changed(int count) {
        if (count != 1) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
    }

    private void dirty() {
        caches.evict(RoutingResource.PREFIX, BusinessCacheEvict.Scope.LIST_AND_DETAIL);
    }

    public ProductionSyncResult sync(FactoryScope scope) {
        if (!FactoryContext.required().equals(scope)) throw new FoundationException(ErrorCode.CONFLICT);
        var run = coordinator.claim(scope, "routing.erp", Duration.ofMinutes(5));
        try {
            coordinator.progress(run, 0);
            var inputs = query.query(scope, 100);
            coordinator.progress(run, 0);
            var result = self.getObject().upsert(scope, inputs);
            coordinator.progress(run, result.fetched());
            coordinator.succeed(run, null);
            return result;
        } catch (RuntimeException failure) {
            try {
                coordinator.fail(run);
            } catch (RuntimeException delivery) {
                failure.addSuppressed(delivery);
            }
            throw failure;
        }
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED,
            rollbackFor = Exception.class)
    public ProductionSyncResult upsert(FactoryScope scope, List<ExternalRouting> inputs) {
        if (!FactoryContext.required().equals(scope)) throw new FoundationException(ErrorCode.CONFLICT);
        if (inputs == null || inputs.size() > 100) throw new FoundationException(ErrorCode.BAD_REQUEST);
        var routeKeys = new HashSet<String>();
        var materialCodes = new HashSet<String>();
        var operationCodes = new HashSet<String>();
        for (var input : inputs) {
            if (input == null || !validator.validate(input).isEmpty() || !routeKeys.add(key(input.code()))) {
                throw new FoundationException(ErrorCode.BAD_REQUEST);
            }
            input.productCodes().forEach(code -> materialCodes.add(code.strip()));
            input.graph().nodes().forEach(node -> operationCodes.add(node.operationCode().strip()));
        }
        if (inputs.isEmpty()) return new ProductionSyncResult(0, 0, 0, 0);
        // Two bounded set queries replace one external/code lookup per step or product.
        var materialFilter = new MaterialExample();
        materialFilter.createCriteria()
                .andCodeIn(List.copyOf(materialCodes));
        var materialLock = new MaterialEntity();

        for (var reference : materials.selectByExample(materialFilter).stream()
                .sorted((left, right) -> left.getId().compareTo(right.getId())).toList()) {
            materialLock.setId(reference.getId());
            var rowFilter = new MaterialExample();
            rowFilter.createCriteria().andIdEqualTo(reference.getId());
            if (materials.updateByExampleSelective(materialLock, rowFilter) != 1) {
                throw new FoundationException(ErrorCode.NOT_FOUND);
            }
        }
        var materialIndex = new HashMap<String, Long>();
        for (var row : materials.selectByExample(materialFilter)) materialIndex.put(key(row.getCode()), row.getId());
        var operationFilter = new OperationExample();
        operationFilter.createCriteria()
                .andCodeIn(List.copyOf(operationCodes));
        var operationLock = new OperationEntity();

        for (var reference : operations.selectByExample(operationFilter).stream()
                .sorted((left, right) -> left.getId().compareTo(right.getId())).toList()) {
            operationLock.setId(reference.getId());
            var rowFilter = new OperationExample();
            rowFilter.createCriteria().andIdEqualTo(reference.getId());
            if (operations.updateByExampleSelective(operationLock, rowFilter) != 1) {
                throw new FoundationException(ErrorCode.NOT_FOUND);
            }
        }
        var operationIndex = new HashMap<String, Long>();
        for (var row : operations.selectByExample(operationFilter)) operationIndex.put(key(row.getCode()), row.getId());
        int created = 0, updated = 0, skipped = 0;
        // Each route has at most 100 products/steps; maximum batch is 100 routes, one local transaction.
        for (var input : inputs) {
            var ids = input.productCodes().stream().map(code -> materialIndex.get(key(code))).toList();
            var graph = input.graph();
            var commands = graph.nodes().stream().map(node -> new RoutingNodeCommand(node.nodeCode(),
                            operationIndex.get(key(node.operationCode())), node.isKey(), node.description(), node.displayOrder()))
                    .toList();
            var connections = graph.edges().stream()
                    .map(edge -> new RoutingEdgeCommand(edge.fromNodeCode(), edge.toNodeCode())).toList();
            if (ids.stream().anyMatch(Objects::isNull) || commands.stream()
                    .anyMatch(node -> node.operationId() == null)) {
                throw new FoundationException(ErrorCode.ROUTING_REFERENCE_INVALID);
            }
            RoutingGraphValidator.validate(commands, connections, false);
            var old = latest(input.code().strip());
            if (old != null && same(old, input, ids, commands, connections)) {
                skipped++;
                continue;
            }
            boolean insert = old == null || !"DRAFT".equals(old.getStatus());
            RoutingEntity row;
            if (insert) {
                row = new RoutingEntity();

                row.setCode(input.code().strip());
                row.setVersion(old == null ? 1 : old.getVersion() + 1);
                row.setStatus("DRAFT");
                row.setRevision(0);
            } else row = touch(old.getId(), old.getRevision(), true);
            row.setName(input.name().strip());
            row.setDescription(input.description());
            row.setExternalId(input.externalId());
            row.setSourceVersion(input.sourceVersion());
            if (insert) insertRow(row);
            else saveRow(row);
            replaceContent(row, ids, commands, connections);
            if (insert) created++;
            else updated++;
        }
        return new ProductionSyncResult(inputs.size(), created, updated, skipped);
    }

    private boolean same(RoutingEntity row, ExternalRouting input, List<Long> materialIds,
                         List<RoutingNodeCommand> commands, List<RoutingEdgeCommand> connections) {
        if (!Objects.equals(row.getName(), input.name()
                .strip()) || !Objects.equals(row.getDescription(), input.description())
                || !Objects.equals(row.getExternalId(), input.externalId())
                || !Objects.equals(row.getSourceVersion(), input.sourceVersion())) return false;
        var detail = detailRows(row);
        if (!detail.products().stream().map(RoutingProductEntity::getMaterialId).sorted().toList()
                .equals(materialIds.stream().sorted().toList())) return false;
        var actual = graph(detail);
        var nodeOrder = Comparator.comparing(RoutingNodeCommand::nodeCode);
        var edgeOrder = Comparator.comparing(RoutingEdgeCommand::fromNodeCode)
                .thenComparing(RoutingEdgeCommand::toNodeCode);
        return actual.nodes().stream().sorted(nodeOrder).toList().equals(commands.stream().sorted(nodeOrder).toList())
                && actual.edges().stream().sorted(edgeOrder).toList()
                .equals(connections.stream().sorted(edgeOrder).toList());
    }

    private String key(String code) {
        return code.strip().toLowerCase(Locale.ROOT);
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public List<RoutingReference> operationOptions(String search, int limit) {
        if (limit < 1 || limit > 100 || search != null && search.length() > 255)
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        var filter = new OperationExample();
        var criteria = filter.createCriteria().andStatusEqualTo("ENABLED");
        if (search != null && !search.isBlank()) {
            criteria.andCodeLike("%" + search + "%");
            filter.or().andStatusEqualTo("ENABLED").andNameLike("%" + search + "%");
        }
        filter.setOrderByClause("code");
        try (Page<OperationEntity> page = PageHelper.offsetPage(0, limit, false)) {
            return operations.selectByExample(filter).stream()
                    .map(row -> new RoutingReference(row.getId(), row.getCode(), row.getName())).toList();
        } finally {
            PageHelper.clearPage();
        }
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public List<RoutingReference> materialOptions(String search, int limit) {
        if (limit < 1 || limit > 100 || search != null && search.length() > 255)
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        var filter = new MaterialExample();
        var criteria = filter.createCriteria().andStatusEqualTo("ENABLED");
        if (search != null && !search.isBlank()) {
            criteria.andCodeLike("%" + search + "%");
            filter.or().andStatusEqualTo("ENABLED").andNameLike("%" + search + "%");
        }
        filter.setOrderByClause("code");
        try (Page<MaterialEntity> page = PageHelper.offsetPage(0, limit, false)) {
            return materials.selectByExample(filter).stream()
                    .map(row -> new RoutingReference(row.getId(), row.getCode(), row.getName())).toList();
        } finally {
            PageHelper.clearPage();
        }
    }
}
