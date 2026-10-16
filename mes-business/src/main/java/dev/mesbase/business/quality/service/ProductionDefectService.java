package dev.mesbase.business.quality.service;

import dev.mesbase.business.quality.dao.MesProductionDefectMapper;
import dev.mesbase.business.quality.domain.MesProductionDefectEntity;
import dev.mesbase.business.quality.domain.MesProductionDefectExample;
import dev.mesbase.business.quality.domain.ProductionDefectModels.Command;
import dev.mesbase.business.quality.domain.ProductionDefectModels.Facts;
import dev.mesbase.business.quality.domain.ProductionDefectModels.Page;
import dev.mesbase.business.quality.domain.ProductionDefectModels.Node;
import dev.mesbase.business.quality.domain.ProductionDefectModels.NodeTotal;
import dev.mesbase.business.quality.domain.ProductionDefectModels.Warehouse;
import dev.mesbase.business.production.service.ProductionChainLocks;
import dev.mesbase.business.production.service.ExecutionPathService;
import dev.mesbase.business.production.service.ExecutionPathValidator;
import dev.mesbase.business.directory.service.BusinessDirectoryService;
import dev.mesbase.business.wm.dao.WmWarehouseMapper;
import dev.mesbase.business.wm.domain.WmWarehouseExample;
import dev.mesbase.business.quality.integration.ProductionDefectErpGateway;
import dev.mesbase.business.integration.warehouse.ErpActionResult;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import com.github.pagehelper.PageHelper;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.dao.DuplicateKeyException;
import java.math.BigDecimal;
import dev.mesbase.business.shared.enums.EnableStatus;
import dev.mesbase.business.quality.domain.DefectRecordAction;
import dev.mesbase.business.quality.domain.WasteCategory;
import dev.mesbase.business.quality.domain.DefectDestination;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import dev.mesbase.business.quality.domain.ProductionDefectModels.Names;
import dev.mesbase.business.production.dao.RoutingNodeMapper;
import dev.mesbase.business.production.domain.RoutingNodeExample;
import dev.mesbase.business.wm.domain.WmWarehouseEntity;
import java.util.Objects;
import java.util.UUID;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
@Transactional(transactionManager = "businessTransactionManager", readOnly = true)
public class ProductionDefectService {
    private final MesProductionDefectMapper mapper;
    private final ProductionChainLocks locks;
    private final ExecutionPathService paths;
    private final WmWarehouseMapper warehouses;
    private final BusinessDirectoryService directory;
    private final ProductionDefectErpGateway erp;
    private final RoutingNodeMapper routingNodes;

    public ProductionDefectService(MesProductionDefectMapper mapper, ProductionChainLocks locks,
                                   ExecutionPathService paths,
                                   WmWarehouseMapper warehouses, BusinessDirectoryService directory,
                                   ProductionDefectErpGateway erp, RoutingNodeMapper routingNodes) {
        this.mapper = mapper;
        this.locks = locks;
        this.paths = paths;
        this.warehouses = warehouses;
        this.directory = directory;
        this.erp = erp;
        this.routingNodes = routingNodes;
    }

    public record Result(MesProductionDefectEntity record, ErpActionResult erp) {
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public Result create(Command c) {
        return execute(DefectRecordAction.CREATE.getValue(), null, c);
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public Result revise(String id, Command c) {
        return execute(DefectRecordAction.REVISE.getValue(), id, c);
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public Result voidDeclaration(String id, Command c) {
        return execute(DefectRecordAction.VOID.getValue(), id, c);
    }

    private Result execute(String action, String id, Command c) {
        if (c == null || c.requestKey() == null || c.requestKey().isBlank() || c.requestKey().length() > 64) invalid();
        if (action.equals(DefectRecordAction.CREATE.getValue())) {
            if (c.expectedRevision() != null || c.revisionReason() != null || c.facts() == null) invalid();
        } else {
            text(c.revisionReason(), true);
            if (c.expectedRevision() == null || c.expectedRevision() < 1) invalid();
            if (action.equals(DefectRecordAction.VOID.getValue()) && c.facts() != null || action.equals(DefectRecordAction.REVISE.getValue()) && c.facts() == null)
                invalid();
        }
        var fingerprint = fingerprint(action, id, c, SecurityUtils.getUserId());
        var retry = byKey(c.requestKey());
        if (retry != null) return replay(retry, fingerprint);
        var before = id == null ? null : latest(id);
        Long execution = before == null ? c.facts().executionId() : before.getExecutionId();
        locks.execution(execution); // existing WorkOrder -> Execution lock order, then version read
        retry = byKey(c.requestKey());
        if (retry != null) return replay(retry, fingerprint);
        var row = new MesProductionDefectEntity();
        if (before != null) {
            before = latest(id);
            if (DefectRecordAction.VOID.getValue()
                    .equals(before.getRecordAction()) || !Objects.equals(c.expectedRevision(), before.getRevisionNo()))
                conflict();
            if (!action.equals(DefectRecordAction.VOID.getValue()) && (!Objects.equals(c.facts()
                    .executionId(), before.getExecutionId()) || !Objects.equals(c.facts()
                    .affectedNodeId(), before.getAffectedNodeId()))) conflict();
        }
        Facts facts = action.equals(DefectRecordAction.VOID.getValue()) ? facts(before) : normalize(c.facts());
        if (!action.equals(DefectRecordAction.VOID.getValue())) facts = validate(facts);
        apply(row, facts);
        row.setDeclarationId(id == null ? UUID.randomUUID().toString() : id);
        row.setRevisionNo(before == null ? 1 : before.getRevisionNo() + 1);
        row.setRecordAction(action);
        row.setRevisionReason(trim(c.revisionReason()));
        row.setReportedBy(before == null ? SecurityUtils.getUserId() : before.getReportedBy());
        row.setRecordedBy(SecurityUtils.getUserId());
        row.setRecordedAt(LocalDateTime.now());
        row.setRequestKey(c.requestKey());
        row.setRequestFingerprint(fingerprint);
        try {
            if (mapper.insert(row) != 1) conflict();
        } catch (DuplicateKeyException e) {
            var old = byKey(c.requestKey());
            if (old != null) return replay(old, fingerprint);
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, e);
        }
        return new Result(row, erp.acknowledge(row));
    }

    private Result replay(MesProductionDefectEntity row, String fp) {
        if (!fp.equals(row.getRequestFingerprint()))
            throw new FoundationException(ErrorCode.DEFECT_IDEMPOTENCY_CONFLICT);
        // Acknowledgement is local and has no external side effects, including retries.
        return new Result(row, erp.acknowledge(row));
    }

    private MesProductionDefectEntity byKey(String key) {
        var e = new MesProductionDefectExample();
        e.createCriteria().andRequestKeyEqualTo(key);
        var rows = mapper.selectByExample(e);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public MesProductionDefectEntity latest(String id) {
        if (id == null || id.isBlank()) invalid();
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
        paging(offset, limit);
        try (var page = PageHelper.offsetPage(offset, limit)) {
            var rows = mapper.selectLatest(executionId, workorderId, affectedNodeId);
            return new Page(List.copyOf(rows), page.getTotal(), offset, limit, names(rows));
        }
    }

    public Page history(String id, int offset, int limit) {
        paging(offset, limit);
        latest(id);
        var e = new MesProductionDefectExample();
        e.createCriteria().andDeclarationIdEqualTo(id);
        e.setOrderByClause("revision_no desc");
        try (var page = PageHelper.offsetPage(offset, limit)) {
            var rows = mapper.selectByExample(e);
            return new Page(List.copyOf(rows), page.getTotal(), offset, limit, names(rows));
        }
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
        if (executionId == null || affectedNodeId == null) invalid();
        return mapper.sumEffective(executionId, affectedNodeId);
    }

    public List<NodeTotal> nodeTotals(Long executionId) {
        if (executionId == null) invalid();
        return mapper.nodeTotals(executionId);
    }

    public List<Node> nodes(Long executionId) {
        var p = paths.view(executionId);
        if (p.path() == null || !Objects.equals(p.path().getRoutingId(), p.routing().id())) invalid();
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
        if (keyword != null && !keyword.isBlank()) c.andNameLike("%" + keyword + "%");
        e.setOrderByClause("id");
        try (var page = PageHelper.offsetPage(0, 50)) {
            return warehouses.selectByExample(e).stream()
                    .map(w -> new Warehouse(w.getId().toString(), w.getCode(), w.getName())).toList();
        }
    }

    private Facts validate(Facts f) {
        if (f.executionId() == null || f.affectedNodeId() == null || f.discoveredNodeId() == null || f.defectQty() == null || f.defectQty()
                .signum() <= 0 || f.defectQty().scale() > 6 || f.defectQty().precision() - f.defectQty().scale() > 14)
            invalid();
        if (!WasteCategory.PROCESS.getValue().equals(f.wasteCategory()) && !WasteCategory.MATERIAL.getValue()
                .equals(f.wasteCategory())) invalid();
        text(f.defectReason(), true);
        text(f.responsibilityRemark(), false);
        text(f.destinationRemark(), false);
        if (f.occurredAt() == null || f.occurredAt().isAfter(LocalDateTime.now())) invalid();
        var ids = nodes(f.executionId()).stream().map(n -> Long.valueOf(n.id())).toList();
        if (!ids.contains(f.affectedNodeId()) || !ids.contains(f.discoveredNodeId()) || f.responsibleNodeId() != null && !ids.contains(f.responsibleNodeId()))
            invalid();
        if (f.responsibleUserId() != null && !directory.historicalUserExists(f.responsibleUserId())) invalid();
        if (DefectDestination.ROUTING_NODE.getValue().equals(f.destinationType())) {
            if (f.targetWarehouseId() != null) invalid();
            int index = ids.indexOf(f.discoveredNodeId());
            Long target = f.targetNodeId();
            if (target == null && index > 0) target = ids.get(index - 1);
            if (target == null || ids.indexOf(target) < 0 || ids.indexOf(target) >= index) invalid();
            return new Facts(f.executionId(), f.affectedNodeId(), f.discoveredNodeId(), f.responsibleNodeId(), f.defectQty(), f.wasteCategory(), f.defectReason(), f.responsibleUserId(), f.responsibilityRemark(), f.destinationType(), target, null, f.destinationRemark(), f.occurredAt());
        }
        if (!DefectDestination.WAREHOUSE.getValue()
                .equals(f.destinationType()) || f.targetNodeId() != null || f.targetWarehouseId() == null)
            invalid();
        var w = warehouses.selectByPrimaryKey(f.targetWarehouseId());
        if (w == null || !EnableStatus.ENABLED.getValue().equals(w.getStatus())) invalid();
        return f;
    }

    public static Facts normalize(Facts f) {
        if (f == null) return null;
        return new Facts(f.executionId(), f.affectedNodeId(), f.discoveredNodeId(), f.responsibleNodeId(), f.defectQty() == null ? null : f.defectQty()
                .stripTrailingZeros(), f.wasteCategory(), trim(f.defectReason()), f.responsibleUserId(), trim(f.responsibilityRemark()), f.destinationType(), f.targetNodeId(), f.targetWarehouseId(), trim(f.destinationRemark()), f.occurredAt() == null ? null : f.occurredAt()
                .withNano(f.occurredAt().getNano() / 1000 * 1000));
    }

    public static String fingerprint(String action, String id, Command c, Long actor) {
        // Length-prefixed typed fields avoid JSON ordering, decimal scale and delimiter ambiguity.
        var f = normalize(c.facts());
        var b = new StringBuilder();
        for (Object v : new Object[]{action, id, actor, c.expectedRevision(), trim(c.revisionReason()), f == null ? null : f.executionId(), f == null ? null : f.affectedNodeId(), f == null ? null : f.discoveredNodeId(), f == null ? null : f.responsibleNodeId(), f == null ? null : f.defectQty(), f == null ? null : f.wasteCategory(), f == null ? null : f.defectReason(), f == null ? null : f.responsibleUserId(), f == null ? null : f.responsibilityRemark(), f == null ? null : f.destinationType(), f == null ? null : f.targetNodeId(), f == null ? null : f.targetWarehouseId(), f == null ? null : f.destinationRemark(), f == null ? null : f.occurredAt()}) {
            String s = v == null ? null : v.toString();
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

    private static String trim(String s) {
        return s == null ? null : s.trim().isEmpty() ? null : s.trim();
    }

    private static void text(String s, boolean required) {
        if (required && (s == null || s.isBlank()) || s != null && s.length() > 500) invalid();
    }

    private static void paging(int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > 100) invalid();
    }

    private static void invalid() {
        throw new FoundationException(ErrorCode.BAD_REQUEST);
    }

    private static void conflict() {
        throw new FoundationException(ErrorCode.DEFECT_REVISION_CONFLICT);
    }
}
