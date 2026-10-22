package dev.mesbase.business.quality.service;

import com.github.pagehelper.PageHelper;
import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.directory.service.BusinessDirectoryService;
import dev.mesbase.business.integration.warehouse.ErpActionResult;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.RoutingNodeMapper;
import dev.mesbase.business.production.domain.ExecutionPathStatus;
import dev.mesbase.business.production.domain.ProductionCompletionRequested;
import dev.mesbase.business.production.domain.ProductionQuantityInput;
import dev.mesbase.business.production.domain.RoutingNodeEntity;
import dev.mesbase.business.production.domain.RoutingNodeExample;
import dev.mesbase.business.production.service.ExecutionPathService;
import dev.mesbase.business.production.service.ExecutionPathValidator;
import dev.mesbase.business.production.service.ProductionChainLocks;
import dev.mesbase.business.production.service.ProductionQuantityService;
import dev.mesbase.business.quality.dao.MesProductionDefectMapper;
import dev.mesbase.business.quality.dao.ProductionDefectListExtraMapper;
import dev.mesbase.business.quality.domain.ProductionDefectModels.Processing;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Attribution;
import dev.mesbase.business.quality.domain.DefectDestination;
import dev.mesbase.business.quality.domain.DefectRecordAction;
import dev.mesbase.business.quality.domain.MesProductionDefectEntity;
import dev.mesbase.business.quality.domain.MesProductionDefectExample;
import dev.mesbase.business.quality.domain.ProductionDefectModels.Command;
import dev.mesbase.business.quality.domain.ProductionDefectModels.Facts;
import dev.mesbase.business.quality.domain.ProductionDefectModels.ListContext;
import dev.mesbase.business.quality.domain.ProductionDefectModels.Names;
import dev.mesbase.business.quality.domain.ProductionDefectModels.Node;
import dev.mesbase.business.quality.domain.ProductionDefectModels.NodeTotal;
import dev.mesbase.business.quality.domain.ProductionDefectModels.Page;
import dev.mesbase.business.quality.domain.ProductionDefectModels.Warehouse;
import dev.mesbase.business.quality.domain.ProductionDefectV2Models;
import dev.mesbase.business.quality.domain.WasteCategory;
import dev.mesbase.business.quality.integration.ProductionDefectErpGateway;
import dev.mesbase.business.shared.enums.EnableStatus;
import dev.mesbase.business.wm.dao.WmWarehouseMapper;
import dev.mesbase.business.wm.domain.WmWarehouseEntity;
import dev.mesbase.business.wm.domain.WmWarehouseExample;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.common.utils.StringUtils;
import dev.mesbase.common.validation.ParameterChecks;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.security.AuthenticatedActor;
import dev.mesbase.core.security.AuthorizationService;
import dev.mesbase.core.security.Permission;
import dev.mesbase.platform.execution.FactoryContext;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
@Transactional(transactionManager = "businessTransactionManager", readOnly = true)
public class ProductionDefectService {
    private final ApplicationEventPublisher completionPublisher;
    private final ObjectProvider<AuthorizationService> authorization;
    private final ObjectProvider<OperationDefectTypeService> defectTypes;
    private final MesProductionExecutionMapper executions;
    private final MaterialMapper materials;
    private final MesProductionDefectMapper mapper;
    private final ProductionDefectListExtraMapper listQueries;
    private final ProductionChainLocks locks;
    private final ExecutionPathService paths;
    private final WmWarehouseMapper warehouses;
    private final BusinessDirectoryService directory;
    private final ProductionDefectErpGateway erp;
    private final RoutingNodeMapper routingNodes;
    private final ProductionQuantityService quantities;
    private final QualityTreatmentService treatments;

    public ProductionDefectService(MesProductionDefectMapper mapper, ProductionChainLocks locks,
                                   ExecutionPathService paths,
                                   WmWarehouseMapper warehouses, BusinessDirectoryService directory,
                                   ProductionDefectErpGateway erp, RoutingNodeMapper routingNodes,
                                   ProductionQuantityService quantities, QualityTreatmentService treatments,
                                   ObjectProvider<AuthorizationService> authorization,
                                   ObjectProvider<OperationDefectTypeService> defectTypes,
                                   MesProductionExecutionMapper executions, MaterialMapper materials,
                                   ApplicationEventPublisher completionPublisher,
                                   ProductionDefectListExtraMapper listQueries) {
        this.listQueries = listQueries;
        this.completionPublisher = completionPublisher;
        this.authorization = authorization;
        this.defectTypes = defectTypes;
        this.executions = executions;
        this.materials = materials;
        this.mapper = mapper;
        this.locks = locks;
        this.paths = paths;
        this.warehouses = warehouses;
        this.directory = directory;
        this.erp = erp;
        this.routingNodes = routingNodes;
        this.quantities = quantities;
        this.treatments = treatments;
    }

    public record Result(MesProductionDefectEntity record, ErpActionResult erp) {
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public Result create(Command c) {
        authorizeAssessment(c);
        return execute(DefectRecordAction.CREATE.getValue(), null, c);
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public Result revise(String id, Command c) {
        authorizeAssessment(c);
        return execute(DefectRecordAction.REVISE.getValue(), id, c);
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public Result voidDeclaration(String id, Command c) {
        return execute(DefectRecordAction.VOID.getValue(), id, c);
    }

    private Result execute(String action, String id, Command c) {
        return execute(action, id, c, null, null);
    }

    private Result execute(String action, String id, Command c, MesProductionDefectEntity typed,
                           String typedFingerprint) {
        if (c == null || StringUtils.isBlank(c.requestKey()) || c.requestKey().length() > 64)
            ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        if (action.equals(DefectRecordAction.CREATE.getValue())) {
            if (c.expectedRevision() != null || c.revisionReason() != null || c.facts() == null)
                ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        } else {
            ParameterChecks.text(c.revisionReason(), true, 500, ErrorCode.BAD_REQUEST);
            if (c.expectedRevision() == null || c.expectedRevision() < 1) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
            if (action.equals(DefectRecordAction.VOID.getValue()) && c.facts() != null || action.equals(DefectRecordAction.REVISE.getValue()) && c.facts() == null)
                ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        }
        var fingerprint = typedFingerprint == null ? fingerprint(action, id, c, SecurityUtils.getUserId()) : typedFingerprint;
        var retry = byKey(c.requestKey());
        if (retry != null) return replay(retry, fingerprint);
        var before = Optional.ofNullable(id).map(nullableValue -> latest(nullableValue)).orElse(null);
        Long execution = before == null ? c.facts().executionId() : before.getExecutionId();
        locks.execution(execution); // existing WorkOrder -> Execution lock order, then version read
        retry = byKey(c.requestKey());
        if (retry != null) return replay(retry, fingerprint);
        if (c.facts() != null)
            ProductionQuantityInput.changed(c.facts().defectQty(), Optional.ofNullable(before)
                    .map(nullableValue -> nullableValue.getDefectQty()).orElse(null));
        var row = new MesProductionDefectEntity();
        if (before != null) {
            before = latest(id);
            if (DefectRecordAction.VOID.getValue()
                    .equals(before.getRecordAction()) || !Objects.equals(c.expectedRevision(), before.getRevisionNo()))
                ParameterChecks.reject(ErrorCode.DEFECT_REVISION_CONFLICT);
            if (!action.equals(DefectRecordAction.VOID.getValue()) && (!Objects.equals(c.facts()
                    .executionId(), before.getExecutionId()) || !Objects.equals(c.facts()
                    .affectedNodeId(), before.getAffectedNodeId())))
                ParameterChecks.reject(ErrorCode.DEFECT_REVISION_CONFLICT);
        }
        treatments.assertMutable(before);
        Facts facts = action.equals(DefectRecordAction.VOID.getValue()) ? facts(before) : normalize(c.facts());
        if (!action.equals(DefectRecordAction.VOID.getValue())) {
            facts = validate(facts);
        }
        if (!action.equals(DefectRecordAction.VOID.getValue()) && facts.defectQty()
                .compareTo(quantities.getNodeQuantity(execution, facts.affectedNodeId()).reportedQty()) > 0)
            throw new FoundationException(ErrorCode.REPORT_QUANTITY_CONFLICT);
        apply(row, facts);
        if (typed != null) copyType(typed, row);
        else if (before != null) copyType(before, row);
        row.setCommandVersion(Optional.ofNullable(typed).map(nullableValue -> "V2").orElse(null));
        row.setDeclarationId(id == null ? UUID.randomUUID().toString() : id);
        row.setRevisionNo(before == null ? 1 : before.getRevisionNo() + 1);
        row.setRecordAction(action);
        row.setRevisionReason(StringUtils.trimToNull(c.revisionReason()));
        row.setReportedBy(before == null ? SecurityUtils.getUserId() : before.getReportedBy());
        row.setRecordedBy(SecurityUtils.getUserId());
        row.setRecordedAt(LocalDateTime.now());
        row.setRequestKey(c.requestKey());
        row.setRequestFingerprint(fingerprint);
        try {
            if (mapper.insert(row) != 1) ParameterChecks.reject(ErrorCode.DEFECT_REVISION_CONFLICT);
        } catch (DuplicateKeyException e) {
            var old = byKey(c.requestKey());
            if (old != null) return replay(old, fingerprint);
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, e);
        }
        if (!action.equals(DefectRecordAction.VOID.getValue())) treatments.assessCreatedVersion(row, c.assessment());
        quantities.validateDefectChange(execution, facts.affectedNodeId(), BigDecimal.ZERO);
        completionPublisher.publishEvent(new ProductionCompletionRequested(FactoryContext.required(), execution));
        return new Result(row, erp.acknowledge(row));
    }


    private boolean permitted(String permission) {
        var service = authorization.getIfAvailable();
        return service != null && service.hasPermission(new AuthenticatedActor(SecurityUtils.getAuthentication()
                        .getName()),
                FactoryContext.required(), new Permission(permission));
    }

    private void authorizeAssessment(Command c) {
        if (c != null && c.assessment() != null && !permitted("business:quality:defect:revise"))
            throw new FoundationException(ErrorCode.FORBIDDEN);
    }

    public ProductionDefectV2Models.Context declarationContext(Long executionId, Long nodeId, int offset, int limit) {
        var node = actualNode(executionId, nodeId);
        var execution = executions.selectByPrimaryKey(executionId);
        var product = materials.selectByPrimaryKey(execution.getProductId());
        if (product == null) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        var page = defectTypes.getObject().page(node.getOperationId(), true, offset, limit);
        return new ProductionDefectV2Models.Context(executionId, nodeId, node.getOperationId(), node.getOperationName(),
                product.getId(), product.getCode(), product.getName(), page.items(), page.total(), offset, limit,
                permitted("business:quality:defect:waste:override"));
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public Result declare(ProductionDefectV2Models.Command c) {
        if (c == null || c.requestId() == null) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        try {
            if (!UUID.fromString(c.requestId()).toString().equals(c.requestId()))
                ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        } catch (IllegalArgumentException e) {
            ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        }
        // Authorization is shared by old/new entry points; no caller can self-assess using create alone.
        authorizeAssessment(new Command(c.requestId(), null, null, null, c.assessment()));
        String fp = v2Fingerprint(c);
        var old = byKey(c.requestId());
        if (old != null) return replay(old, fp);
        locks.execution(c.executionId());
        old = byKey(c.requestId());
        if (old != null) return replay(old, fp);
        ProductionQuantityInput.check(c.quantity());
        if (c.defectTypeId() == null || c.expectedTypeRevision() == null || c.wasteCategory() == null)
            ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        var node = actualNode(c.executionId(), c.nodeId());
        var type = defectTypes.getObject()
                .lockForDeclaration(node.getOperationId(), c.defectTypeId(), c.expectedTypeRevision(), c.wasteCategory());
        if (!Objects.equals(type.getDefaultWasteCategory(), c.wasteCategory()) && !permitted("business:quality:defect:waste:override"))
            throw new FoundationException(ErrorCode.FORBIDDEN);
        var typed = new MesProductionDefectEntity();
        typed.setDefectTypeId(type.getId());
        typed.setDefectTypeCodeSnapshot(type.getCode());
        typed.setDefectTypeNameSnapshot(type.getName());
        typed.setDefectTypeRevision(type.getRevision());
        String reason = StringUtils.trimToNull(c.remark());
        if (reason == null) reason = type.getName();
        var facts = new Facts(c.executionId(), c.nodeId(), c.nodeId(), null, c.quantity(), c.wasteCategory(), reason,
                null, null, DefectDestination.SAME_NODE.getValue(), c.nodeId(), null, null, c.occurredAt());
        return execute(DefectRecordAction.CREATE.getValue(), null,
                new Command(c.requestId(), null, null, facts, c.assessment()), typed, fp);
    }

    private RoutingNodeEntity actualNode(Long executionId, Long nodeId) {
        if (executionId == null || nodeId == null) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        var p = paths.view(executionId);
        if (p.path() == null || !ExecutionPathStatus.LOCKED.getValue().equals(p.path().getPathStatus())
                || !p.selectedNodeIds().contains(nodeId)) throw new FoundationException(ErrorCode.REPORT_PATH_REQUIRED);
        if (p.routing() == null || !Objects.equals(p.path().getRoutingId(), p.routing().id()))
            throw new FoundationException(ErrorCode.REPORT_PATH_REQUIRED);
        ExecutionPathValidator.validate(p.selectedNodeIds(), p.nodes(), p.edges());
        return p.nodes().stream().filter(n -> n.getId().equals(nodeId)).findFirst()
                .orElseThrow(() -> new FoundationException(ErrorCode.DEFECT_TYPE_INVALID));
    }

    private String v2Fingerprint(ProductionDefectV2Models.Command c) {
        var b = new StringBuilder("V2:");
        for (Object value : new Object[]{SecurityUtils.getUserId(), c.requestId(), c.executionId(), c.nodeId(), c.defectTypeId(),
                c.expectedTypeRevision(), Optional.ofNullable(c.quantity())
                .map(BigDecimal::stripTrailingZeros).orElse(null), c.wasteCategory(),
                StringUtils.trimToNull(c.remark()), Optional.ofNullable(c.occurredAt())
                .map(nullableValue -> nullableValue
                        .withNano(nullableValue.getNano() / 1000 * 1000)).orElse(null), c.assessment()}) {
            String v = Objects.toString(value, null);
            b.append(v == null ? "-1:" : v.length() + ":" + v);
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(b.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void copyType(MesProductionDefectEntity from, MesProductionDefectEntity to) {
        to.setDefectTypeId(from.getDefectTypeId());
        to.setDefectTypeCodeSnapshot(from.getDefectTypeCodeSnapshot());
        to.setDefectTypeNameSnapshot(from.getDefectTypeNameSnapshot());
        to.setDefectTypeRevision(from.getDefectTypeRevision());
    }

    private Result replay(MesProductionDefectEntity row, String fp) {
        if (!fp.equals(row.getRequestFingerprint()))
            throw new FoundationException(ErrorCode.DEFECT_IDEMPOTENCY_CONFLICT);
        // Acknowledgement is local and has no external side effects, including retries.
        return new Result(row, erp.acknowledge(row));
    }

    public MesProductionDefectEntity ownRequest(String key) {
        var row = byKey(key);
        if (row == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        if (!Objects.equals(row.getRecordedBy(), SecurityUtils.getUserId()))
            throw new FoundationException(ErrorCode.FORBIDDEN);
        return row;
    }

    private MesProductionDefectEntity byKey(String key) {
        var e = new MesProductionDefectExample();
        e.createCriteria().andRequestKeyEqualTo(key);
        var rows = mapper.selectByExample(e);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public MesProductionDefectEntity latest(String id) {
        if (StringUtils.isBlank(id)) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        var e = new MesProductionDefectExample();
        e.createCriteria().andDeclarationIdEqualTo(id);
        e.setOrderByClause("revision_no desc");
        try (var page = PageHelper.offsetPage(0, 1)) {
            var rows = mapper.selectByExample(e);
            if (rows.isEmpty()) throw new FoundationException(ErrorCode.NOT_FOUND);
            return rows.getFirst();
        }
    }

    public Page page(Long executionId, Long workorderId, Long affectedNodeId, int offset, int limit) {
        ParameterChecks.paging(offset, limit);
        List<MesProductionDefectEntity> rows;
        long total;
        try (var page = PageHelper.offsetPage(offset, limit)) {
            rows = List.copyOf(mapper.selectLatest(executionId, workorderId, affectedNodeId));
            total = page.getTotal();
        }
        var contexts = rows.isEmpty() ? Map.<String, ListContext>of() : listQueries.selectContexts(rows.stream()
                        .map(MesProductionDefectEntity::getId).toList()).stream()
                .collect(Collectors.toMap(ListContext::declarationId, value -> value));
        var states = new HashMap<String, Processing>();
        if (!rows.isEmpty()) {
            var totals = listQueries.selectTreatmentTotals(rows.stream().map(MesProductionDefectEntity::getId).toList())
                    .stream().collect(Collectors.toMap(t -> t.declarationId(), t -> t));
            for (var row : rows) {
                var t = totals.get(row.getDeclarationId());
                if (t == null) throw new FoundationException(ErrorCode.QUALITY_TREATMENT_CONFLICT);
                if (t.invalidActions() > 0) throw new FoundationException(ErrorCode.QUALITY_TREATMENT_CONFLICT);
                var attribution = Attribution.UNVERIFIED;
                if (t.attribution() != null) attribution = Attribution.valueOf(t.attribution());
                states.put(row.getDeclarationId(), Processing.of(row.getRecordAction(), attribution,
                        row.getDefectQty(), t.returnedQty(), t.transferredQty()));
            }
        }
        return new Page(rows, total, offset, limit, names(rows), Map.copyOf(states), contexts);
    }

    public Page history(String id, int offset, int limit) {
        ParameterChecks.paging(offset, limit);
        latest(id);
        var e = new MesProductionDefectExample();
        e.createCriteria().andDeclarationIdEqualTo(id);
        e.setOrderByClause("id desc");
        List<MesProductionDefectEntity> rows;
        long total;
        try (var page = PageHelper.offsetPage(offset, limit)) {
            rows = List.copyOf(mapper.selectByExample(e));
            total = page.getTotal();
        }
        return new Page(rows, total, offset, limit, names(rows), Map.of(), Map.of());
    }

    private Names names(List<MesProductionDefectEntity> rows) {
        var ids = rows.stream()
                .flatMap(r -> Stream.of(r.getAffectedNodeId(), r.getDiscoveredNodeId(), r.getResponsibleNodeId(), r.getTargetNodeId()))
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, String> nodeNames = new HashMap<>();
        if (!ids.isEmpty()) {
            var e = new RoutingNodeExample();
            e.createCriteria().andIdIn(ids);
            // Routing nodes retain the published operation-name snapshot; no parallel name cache.
            routingNodes.selectByExample(e)
                    .forEach(n -> nodeNames.put(n.getId(), n.getOperationName() == null ? n.getNodeCode() : n.getOperationName()));
        }
        var userIds = rows.stream()
                .flatMap(r -> Stream.of(r.getResponsibleUserId(), r.getReportedBy(), r.getRecordedBy()))
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, String> userNames = new HashMap<>();
        // A page has at most 100 rows / 300 distinct users: at most two shared directory calls.
        for (int i = 0; i < userIds.size(); i += 200)
            directory.userLabels(userIds.subList(i, Math.min(i + 200, userIds.size()))).forEach((id, label) -> {
                if (label.name() != null) userNames.put(id, label.name());
            });
        var warehouseIds = rows.stream().map(MesProductionDefectEntity::getTargetWarehouseId).filter(Objects::nonNull)
                .distinct().toList();
        Map<Long, String> warehouseNames = Map.of();
        if (!warehouseIds.isEmpty()) {
            var e = new WmWarehouseExample();
            e.createCriteria().andIdIn(warehouseIds);
            warehouseNames = warehouses.selectByExample(e).stream()
                    .collect(Collectors.toMap(WmWarehouseEntity::getId, WmWarehouseEntity::getName));
        }
        return new Names(nodeNames, userNames, warehouseNames);
    }

    public BigDecimal sumEffectiveDefectQty(Long executionId, Long affectedNodeId) {
        if (executionId == null || affectedNodeId == null) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        return treatments.nodeTotals(executionId).stream().filter(t -> t.affectedNodeId().equals(affectedNodeId))
                .map(NodeTotal::effectiveDefectQty).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<NodeTotal> nodeTotals(Long executionId) {
        if (executionId == null) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        return treatments.nodeTotals(executionId);
    }

    public List<Node> nodes(Long executionId) {
        var p = paths.view(executionId);
        if (p.path() == null || !Objects.equals(p.path().getRoutingId(), p.routing().id()))
            ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        ExecutionPathValidator.validate(p.selectedNodeIds(), p.nodes(), p.edges());
        return p.selectedNodeIds().stream().map(id -> {
            var n = p.nodes().stream().filter(x -> x.getId().equals(id)).findFirst().orElseThrow();
            return new Node(id.toString(), n.getOperationName() != null ? n.getOperationName() : n.getNodeCode(), p.selectedNodeIds()
                    .indexOf(id) + 1);
        }).toList();
    }

    public List<Warehouse> warehouses(String keyword) {
        var e = new WmWarehouseExample();
        var c = e.createCriteria().andStatusEqualTo(EnableStatus.ENABLED.getValue());
        if (StringUtils.isNotBlank(keyword)) c.andNameLike("%" + keyword + "%");
        e.setOrderByClause("id desc");
        try (var page = PageHelper.offsetPage(0, 50)) {
            return warehouses.selectByExample(e).stream()
                    .map(w -> new Warehouse(w.getId().toString(), w.getCode(), w.getName())).toList();
        }
    }

    private Facts validate(Facts f) {
        if (f.executionId() == null || f.affectedNodeId() == null || f.discoveredNodeId() == null || f.defectQty() == null || f.defectQty()
                .signum() <= 0 || f.defectQty().scale() > 6 || f.defectQty().precision() - f.defectQty().scale() > 14)
            ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        if (WasteCategory.from(f.wasteCategory()) == null) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        ParameterChecks.text(f.defectReason(), true, 500, ErrorCode.BAD_REQUEST);
        ParameterChecks.text(f.responsibilityRemark(), false, 500, ErrorCode.BAD_REQUEST);
        ParameterChecks.text(f.destinationRemark(), false, 500, ErrorCode.BAD_REQUEST);
        if (f.occurredAt() == null || f.occurredAt().isAfter(LocalDateTime.now()))
            ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        var ids = nodes(f.executionId()).stream().map(n -> Long.valueOf(n.id())).toList();
        if (!ids.contains(f.affectedNodeId()) || !ids.contains(f.discoveredNodeId()) || f.responsibleNodeId() != null && !ids.contains(f.responsibleNodeId()))
            ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        if (f.responsibleUserId() != null && !directory.historicalUserExists(f.responsibleUserId()))
            ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        if (DefectDestination.SAME_NODE.getValue().equals(f.destinationType())) {
            if (f.targetWarehouseId() != null || f.targetNodeId() != null && !f.targetNodeId()
                    .equals(f.affectedNodeId())) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
            return new Facts(f.executionId(), f.affectedNodeId(), f.discoveredNodeId(), f.responsibleNodeId(), f.defectQty(), f.wasteCategory(), f.defectReason(), f.responsibleUserId(), f.responsibilityRemark(), f.destinationType(), f.affectedNodeId(), null, f.destinationRemark(), f.occurredAt());
        }
        throw new FoundationException(ErrorCode.BAD_REQUEST);
    }

    public static Facts normalize(Facts f) {
        if (f == null) return null;
        return new Facts(f.executionId(), f.affectedNodeId(), f.discoveredNodeId(), f.responsibleNodeId(), Optional.ofNullable(f.defectQty())
                .map(nullableValue -> nullableValue
                        .stripTrailingZeros())
                .orElse(null), f.wasteCategory(), StringUtils.trimToNull(f.defectReason()), f.responsibleUserId(), StringUtils.trimToNull(f.responsibilityRemark()), f.destinationType(), f.targetNodeId(), f.targetWarehouseId(), StringUtils.trimToNull(f.destinationRemark()), Optional.ofNullable(f.occurredAt())
                .map(nullableValue -> nullableValue
                        .withNano(nullableValue.getNano() / 1000 * 1000)).orElse(null));
    }

    public static String fingerprint(String action, String id, Command c, Long actor) {
        // Length-prefixed typed fields avoid JSON ordering, decimal scale and delimiter ambiguity.
        var f = normalize(c.facts());
        var b = new StringBuilder();
        Object[] factFields = new Object[14];
        if (f != null) {
            factFields = new Object[]{f.executionId(), f.affectedNodeId(), f.discoveredNodeId(), f.responsibleNodeId(),
                    f.defectQty(), f.wasteCategory(), f.defectReason(), f.responsibleUserId(), f.responsibilityRemark(),
                    f.destinationType(), f.targetNodeId(), f.targetWarehouseId(), f.destinationRemark(), f.occurredAt()};
        }
        var values = new ArrayList<Object>(Arrays.asList(action, id, actor, c.expectedRevision(), StringUtils.trimToNull(c.revisionReason())));
        Collections.addAll(values, factFields);
        values.add(c.assessment());
        for (Object v : values) {
            String s = Objects.toString(v, null);
            b.append(s == null ? "-1:" : s.length() + ":" + s);
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(b.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Facts facts(MesProductionDefectEntity r) {
        return new Facts(r.getExecutionId(), r.getAffectedNodeId(), r.getDiscoveredNodeId(), r.getResponsibleNodeId(), r.getDefectQty(), r.getWasteCategory(), r.getDefectReason(), r.getResponsibleUserId(), r.getResponsibilityRemark(), r.getDestinationType(), r.getTargetNodeId(), r.getTargetWarehouseId(), r.getDestinationRemark(), r.getOccurredAt());
    }

    private static void apply(MesProductionDefectEntity r, Facts f) {
        r.setExecutionId(f.executionId());
        r.setAffectedNodeId(f.affectedNodeId());
        r.setDiscoveredNodeId(f.discoveredNodeId());
        r.setResponsibleNodeId(f.responsibleNodeId());
        r.setDefectQty(f.defectQty());
        r.setWasteCategory(f.wasteCategory());
        r.setDefectReason(f.defectReason());
        r.setResponsibleUserId(f.responsibleUserId());
        r.setResponsibilityRemark(f.responsibilityRemark());
        r.setDestinationType(f.destinationType());
        r.setTargetNodeId(f.targetNodeId());
        r.setTargetWarehouseId(f.targetWarehouseId());
        r.setDestinationRemark(f.destinationRemark());
        r.setOccurredAt(f.occurredAt());
    }


}
