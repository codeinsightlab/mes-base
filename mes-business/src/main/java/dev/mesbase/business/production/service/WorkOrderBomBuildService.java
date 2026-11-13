package dev.mesbase.business.production.service;

import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import dev.mesbase.business.production.domain.RoutingEntity;
import java.math.BigDecimal;
import org.springframework.transaction.annotation.Propagation;
import java.util.Optional;
import dev.mesbase.business.basic.dao.MaterialBomMapper;
import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.business.basic.domain.MaterialBomEntity;
import dev.mesbase.business.basic.domain.MaterialEntity;
import dev.mesbase.business.basic.domain.MaterialBomExplosion;
import dev.mesbase.business.basic.domain.MaterialBomStatus;
import dev.mesbase.business.basic.service.MaterialBomService;
import dev.mesbase.business.production.domain.WorkOrderBomBuildModels;
import dev.mesbase.business.production.domain.WorkOrderModels;
import dev.mesbase.business.production.domain.WorkorderMaterialModels;
import dev.mesbase.business.production.service.WorkOrderService.BomBuildOrder;
import dev.mesbase.business.production.service.WorkOrderService.BomBuildCommand;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.system.service.AutoCodeNumbering;
import dev.mesbase.system.service.AutoCodeRuleType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class WorkOrderBomBuildService {
    private static final String ROOT = "ROOT";
    private final MaterialBomService bomService;
    private final MaterialBomMapper boms;
    private final MaterialMapper materials;
    private final WorkOrderService workOrders;
    private final AutoCodeNumbering autoCodes;
    private final TransactionTemplate previewTransactions;

    public WorkOrderBomBuildService(MaterialBomService bomService, MaterialBomMapper boms, MaterialMapper materials,
                                    WorkOrderService workOrders, AutoCodeNumbering autoCodes,
                                    @Qualifier("businessTransactionManager") PlatformTransactionManager businessTransactionManager) {
        this.bomService = bomService;
        this.boms = boms;
        this.materials = materials;
        this.workOrders = workOrders;
        this.autoCodes = autoCodes;
        this.previewTransactions = new TransactionTemplate(businessTransactionManager);
        this.previewTransactions.setReadOnly(true);
        this.previewTransactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public WorkOrderBomBuildModels.Preview preview(WorkOrderBomBuildModels.PreviewRequest request) {
        if (request == null || request.productId() == null || request.bomId() == null
                || request.plannedQty() == null || request.plannedQty().signum() <= 0)
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        var product = materials.selectByPrimaryKey(request.productId());
        if (product == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        var explosion = bomService.explode(product.getCode(), request.bomId(), request.plannedQty(), 0, 1);
        var problems = new ArrayList<WorkOrderBomBuildModels.Problem>();
        explosion.problems()
                .forEach(p -> problems.add(new WorkOrderBomBuildModels.Problem(p.errorCode(), p.message())));
        if (explosion.selectedVersion() == null) {
            if (problems.isEmpty()) problems.add(problem("所选BOM版本不可用"));
            return new WorkOrderBomBuildModels.Preview(hash(problems.toString()), false, List.of(), List.copyOf(problems));
        }
        var orders = new ArrayList<WorkOrderBomBuildModels.OrderPreview>();
        var root = appendOrder(ROOT, null, null, explosion.material().getId(), explosion.material().getCode(),
                explosion.material().getName(), request.plannedQty(), explosion.selectedVersion(), explosion.items(),
                orders, problems);
        if (root != null) appendChildren(root, explosion.items(), orders, problems);
        String fingerprint = explosion.complete() && problems.isEmpty() ? fingerprint(orders) : hash(problems.toString());
        return new WorkOrderBomBuildModels.Preview(fingerprint, explosion.complete() && problems.isEmpty(), List.copyOf(orders),
                List.copyOf(problems));
    }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.NOT_SUPPORTED)
    public WorkOrderBomBuildModels.Result confirm(WorkOrderBomBuildModels.PreviewRequest request,
                                                  WorkOrderBomBuildModels.Confirm command) {
        if (command == null) throw new FoundationException(ErrorCode.BAD_REQUEST);
        String requestHash = confirmationHash(request, command);
        var replay = workOrders.bomBuildReplay(command.requestId(), requestHash);
        if (replay != null) return new WorkOrderBomBuildModels.Result(replay.buildId(), replay.orders());
        // confirm suspends business transactions so numbering can use its own database.
        // A self-invocation cannot activate preview's transactional annotation.
        var fresh = previewTransactions.execute(status -> preview(request));
        if (!fresh.complete() || !Objects.equals(fresh.fingerprint(), command.fingerprint()))
            throw new FoundationException(ErrorCode.PRODUCTION_INPUT_STALE);
        var choices = new HashMap<String, WorkOrderBomBuildModels.OrderChoice>();
        for (var choice : command.orders()) {
            if (choice == null || choices.put(choice.path(), choice) != null)
                throw new FoundationException(ErrorCode.BAD_REQUEST);
        }
        if (choices.size() != fresh.orders().size()
                || !choices.keySet().equals(fresh.orders().stream().map(WorkOrderBomBuildModels.OrderPreview::path)
                .collect(Collectors.toSet()))) throw new FoundationException(ErrorCode.BAD_REQUEST);
        var availableCodes = autoCodes.rules(AutoCodeRuleType.WORK_ORDER.name()).stream()
                .map(rule -> rule.ruleCode()).collect(Collectors.toSet());
        var records = new ArrayList<BomBuildOrder>();
        for (var pending : fresh.orders()) {
            var choice = choices.get(pending.path());
            String ruleCode = choice.ruleCode() == null || choice.ruleCode().isBlank()
                    ? pending.defaultRuleCode() : choice.ruleCode();
            if (ruleCode == null || !availableCodes.contains(ruleCode))
                throw new FoundationException(ErrorCode.WORKORDER_INVALID);
            if (!pending.routings().isEmpty() && (choice.routingId() == null || pending.routings().stream()
                    .noneMatch(route -> Objects.equals(Long.valueOf(route.id()), choice.routingId()))))
                throw new FoundationException(ErrorCode.WORKORDER_INVALID);
            if (choice.plannedStartAt() != null && choice.plannedEndAt() != null
                    && choice.plannedEndAt().isBefore(choice.plannedStartAt()))
                throw new FoundationException(ErrorCode.WORKORDER_INVALID);
            var lines = pending.materials().stream().map(line -> new WorkorderMaterialModels.Line(null,
                    Long.valueOf(line.lineId()), Long.valueOf(line.materialId()), Long.valueOf(line.unitId()),
                    line.quantity(), false)).toList();
            var material = new WorkorderMaterialModels.Save(0L, Long.valueOf(pending.bomId()),
                    pending.bomRevision(), List.of(), "", true, lines, true);
            var values = new WorkOrderModels.Save(null, Long.valueOf(pending.productId()), pending.plannedQty(),
                    choice.routingId(), choice.plannedStartAt(), choice.plannedEndAt(), null, null, choice.remark(),
                    null, ruleCode, material);
            records.add(new BomBuildOrder(values, pending.path(), pending.parentPath(),
                    Optional.ofNullable(pending.sourceLineId()).map(Long::valueOf).orElse(null)));
        }
        String buildId = UUID.randomUUID().toString();
        var created = workOrders.createBomBuild(new BomBuildCommand(buildId, command.requestId(), requestHash,
                List.copyOf(records)));
        String actualBuildId = created.isEmpty() ? buildId : created.getFirst().workorder().getBomBuildId();
        return new WorkOrderBomBuildModels.Result(actualBuildId, created);
    }

    private static String confirmationHash(WorkOrderBomBuildModels.PreviewRequest request,
                                           WorkOrderBomBuildModels.Confirm command) {
        if (request == null || command.orders() == null) throw new FoundationException(ErrorCode.BAD_REQUEST);
        var value = new StringBuilder(command.fingerprint()).append('|').append(request.productId()).append('|')
                .append(request.bomId()).append('|').append(request.plannedQty());
        command.orders().stream().sorted(Comparator.comparing(WorkOrderBomBuildModels.OrderChoice::path,
                Comparator.nullsFirst(Comparator.naturalOrder()))).forEach(choice -> value.append('|')
                .append(choice.path()).append('|').append(choice.ruleCode()).append('|').append(choice.routingId())
                .append('|').append(choice.plannedStartAt()).append('|').append(choice.plannedEndAt())
                .append('|').append(choice.remark()));
        return hash(value.toString());
    }

    private WorkOrderBomBuildModels.OrderPreview appendOrder(String path, String parentPath, Long sourceLineId,
                                                             Long productId, String code, String name,
                                                             BigDecimal quantity, MaterialBomEntity version,
                                                             List<MaterialBomExplosion.Node> tree,
                                                             List<WorkOrderBomBuildModels.OrderPreview> orders,
                                                             List<WorkOrderBomBuildModels.Problem> problems) {
        var issues = new ArrayList<WorkOrderBomBuildModels.Problem>();
        if (version == null || !MaterialBomStatus.PUBLISHED.getValue().equals(version.getStatus())
                || !Objects.equals(version.getProductId(), productId)) {
            issues.add(problem("此组件关联的BOM版本未发布或产出物料不匹配"));
        }
        if (issues.isEmpty()) {
            try {
                var direct = bomService.direct(version.getId(), quantity);
                if (direct.items().size() != tree.size()) issues.add(problem("BOM直接用料与展阶结果不一致"));
                else for (int i = 0; i < tree.size(); i++) {
                    var component = direct.items().get(i);
                    var node = tree.get(i);
                    if (!Objects.equals(component.lineId(), node.lineId())
                            || !Objects.equals(component.materialId(), node.materialId())
                            || component.quantity().compareTo(node.quantity()) != 0) {
                        issues.add(problem("BOM直接用料与展阶结果不一致"));
                        break;
                    }
                }
            } catch (FoundationException failure) {
                issues.add(new WorkOrderBomBuildModels.Problem(failure.code().name(), failure.safeMessage()));
            }
        }
        var product = materials.selectByPrimaryKey(productId);
        var rule = Optional.ofNullable(product).map(MaterialEntity::getWorkOrderRuleCode).orElse(null);
        var routes = (productId == null ? List.<RoutingEntity>of()
                : workOrders.applicableRoutings(productId, "")).stream().map(r -> new WorkOrderBomBuildModels.Route(
                r.getId().toString(), r.getCode(), r.getName(), r.getVersion())).toList();
        String selectedRoute = routes.size() == 1 ? routes.getFirst().id() : null;
        var materialLines = tree.stream()
                .map(node -> new WorkOrderBomBuildModels.Material(Objects.toString(node.lineId(), null),
                        Objects.toString(node.materialId(), null), node.code(), node.name(), Objects.toString(node.unitId(), null), node.quantity(),
                        node.componentQty(), node.baseQty())).toList();
        var preview = new WorkOrderBomBuildModels.OrderPreview(path, parentPath,
                Objects.toString(sourceLineId, null), Objects.toString(productId, null), code, name, quantity,
                Optional.ofNullable(version).map(value -> value.getId().toString())
                        .orElse(null), Optional.ofNullable(version).map(MaterialBomEntity::getCode).orElse(null),
                Optional.ofNullable(version).map(MaterialBomEntity::getVersion)
                        .orElse(null), Optional.ofNullable(version).map(MaterialBomEntity::getRevision)
                .orElse(null), rule,
                selectedRoute, routes, materialLines, List.copyOf(issues));
        orders.add(preview);
        problems.addAll(issues);
        return preview;
    }

    private void appendChildren(WorkOrderBomBuildModels.OrderPreview parent, List<MaterialBomExplosion.Node> nodes,
                                List<WorkOrderBomBuildModels.OrderPreview> orders,
                                List<WorkOrderBomBuildModels.Problem> problems) {
        for (var node : nodes) {
            if (node.childBomId() == null) continue;
            var childVersion = boms.selectByPrimaryKey(node.childBomId());
            var child = appendOrder(node.path(), parent.path(), node.lineId(), node.materialId(), node.code(),
                    node.name(), node.quantity(), childVersion, node.children(), orders, problems);
            if (child != null) appendChildren(child, node.children(), orders, problems);
        }
    }

    private static String fingerprint(List<WorkOrderBomBuildModels.OrderPreview> orders) {
        var value = new StringBuilder();
        for (var order : orders) {
            value.append(order.path()).append('|').append(order.parentPath()).append('|').append(order.sourceLineId())
                    .append('|').append(order.productId()).append('|').append(order.plannedQty().toPlainString())
                    .append('|').append(order.bomId()).append('|').append(order.bomRevision())
                    .append('|').append(order.defaultRuleCode()).append('|').append(order.routingId());
            order.routings().forEach(route -> value.append("|ROUTE:").append(route.id()));
            for (var line : order.materials())
                value.append(';').append(line.lineId()).append(':')
                        .append(line.materialId()).append(':').append(line.unitId()).append(':')
                        .append(line.quantity().toPlainString());
            value.append('\n');
        }
        return hash(value.toString());
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static WorkOrderBomBuildModels.Problem problem(String message) {
        return new WorkOrderBomBuildModels.Problem(ErrorCode.MATERIAL_BOM_INVALID.name(), message);
    }
}
