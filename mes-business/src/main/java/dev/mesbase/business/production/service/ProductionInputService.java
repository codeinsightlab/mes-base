package dev.mesbase.business.production.service;

import dev.mesbase.business.production.domain.ProductionQuantityInput;

import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;
import dev.mesbase.business.issue.domain.MaterialIssueStatus;
import dev.mesbase.business.production.domain.ProductionExecutionStatus;
import dev.mesbase.business.production.domain.WorkOrderStatus;
import dev.mesbase.business.production.domain.ProductionScanCode;
import dev.mesbase.business.production.domain.MesProductionScanIdentityEntity;
import dev.mesbase.business.production.domain.MesProductionExecutionCreateCommand;
import dev.mesbase.business.production.dao.MesProductionScanIdentityMapper;
import dev.mesbase.business.production.service.IMesProductionExecutionService;
import dev.mesbase.business.issue.domain.MesMaterialIssueCreateCommand;
import dev.mesbase.business.issue.domain.MesMaterialIssueItemCreateCommand;
import dev.mesbase.business.issue.domain.MesMaterialIssueInputEntity;
import dev.mesbase.business.issue.dao.MesMaterialIssueInputMapper;
import dev.mesbase.business.issue.service.IMesMaterialIssueService;
import dev.mesbase.business.issue.service.IMesMaterialIssueItemService;
import dev.mesbase.business.issue.service.MaterialIssueSubmissionService;
import dev.mesbase.business.issue.service.MaterialIssueSubmissionService.Prepared;
import dev.mesbase.business.integration.production.ProductionSupply.Calculation;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.dao.WorkOrderExtraMapper;
import dev.mesbase.business.production.domain.WorkorderEntity;
import dev.mesbase.business.issue.dao.MesMaterialIssueMapper;
import dev.mesbase.business.production.dao.RoutingMapper;
import dev.mesbase.business.production.domain.RoutingDetail;
import dev.mesbase.business.production.service.ExecutionPathService.PathModel;
import dev.mesbase.business.basic.dao.MaterialMapper;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.dao.DuplicateKeyException;
import dev.mesbase.business.issue.service.MaterialIssueInputFacts;

@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ProductionInputService {
    private final WorkOrderExtraMapper allocation;
    private final BusinessSourceRules sources;

    public record Command(String requestId, String code, Long workorderId, String stockReference,
                          BigDecimal outputQty, Long routingId, List<Long> routingNodeIds, String inputKind,
                          String calculationKey) {
    }

    public record Result(String requestId, String issueId, String executionId, String executionNo, String code,
                         String status, String productionInputQty, String availableQty) {
    }

    private final ProductionInputSupplyService supply;
    private final MesProductionScanIdentityMapper bindings;
    private final MesMaterialIssueInputMapper inputs;
    private final MesMaterialIssueMapper issueMapper;
    private final IMesProductionExecutionService executions;
    private final MesProductionExecutionMapper executionMapper;
    private final IMesMaterialIssueService issues;
    private final IMesMaterialIssueItemService items;
    private final MaterialIssueSubmissionService submissions;
    private final MaterialIssueInputFacts inputFacts;
    private final ExecutionPathService paths;
    private final ProductionChainLocks locks;
    private final ProductionQuantityService quantities;
    private final WorkorderMapper orderMapper;
    private final MaterialMapper materials;
    private final RoutingMapper routings;
    private final IRoutingService routingService;
    private final TransactionTemplate transaction;
    private final ObjectMapper json = new ObjectMapper();

    public ProductionInputService(ProductionInputSupplyService supply, MesProductionScanIdentityMapper bindings,
                                  MesMaterialIssueInputMapper inputs, MesMaterialIssueMapper issueMapper,
                                  IMesProductionExecutionService executions,
                                  MesProductionExecutionMapper executionMapper, IMesMaterialIssueService issues,
                                  IMesMaterialIssueItemService items,
                                  MaterialIssueSubmissionService submissions, MaterialIssueInputFacts inputFacts,
                                  ExecutionPathService paths,
                                  ProductionChainLocks locks, ProductionQuantityService quantities,
                                  WorkorderMapper orderMapper,
                                  MaterialMapper materials, RoutingMapper routings, IRoutingService routingService,
                                  @Qualifier("businessTransactionManager") PlatformTransactionManager manager,
                                  BusinessSourceRules sources, WorkOrderExtraMapper allocation) {
        this.allocation = allocation;
        this.sources = sources;
        this.supply = supply;
        this.bindings = bindings;
        this.inputs = inputs;
        this.issueMapper = issueMapper;
        this.executions = executions;
        this.executionMapper = executionMapper;
        this.issues = issues;
        this.items = items;
        this.submissions = submissions;
        this.inputFacts = inputFacts;
        this.paths = paths;
        this.locks = locks;
        this.quantities = quantities;
        this.orderMapper = orderMapper;
        this.materials = materials;
        this.routings = routings;
        this.routingService = routingService;
        transaction = new TransactionTemplate(manager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public record Preview(RoutingDetail definition, PathModel actual) {
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public Preview preview(String code, Long workorderId) {
        var order = readOrder(workorderId);
        sources.order(workorderId);
        var material = ProductionScanCode.material(code);
        var product = materials.selectByPrimaryKey(order.getProductId());
        if (product == null) fail();
        var identity = new ProductionScanCode(product.getCode(), material.materialCode(), material.lotCode());
        var binding = bindings.selectByPrimaryKey(identity.key());
        if (binding == null) return new Preview(routingService.detail(order.getRoutingId()), null);
        if (!identity.encode().equals(binding.getEncodedCode())) fail();
        var execution = executionMapper.selectByPrimaryKey(binding.getExecutionId());
        if (!Objects.equals(execution.getWorkorderId(), workorderId))
            throw new FoundationException(ErrorCode.PRODUCTION_IDENTITY_CONFLICT);
        return new Preview(routingService.detail(execution.getRoutingId()), paths.view(execution.getId()));
    }

    private WorkorderEntity readOrder(Long id) {
        var order = orderMapper.selectByPrimaryKey(id);
        if (order == null) fail();
        return order;
    }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.NOT_SUPPORTED)
    public Result submit(Command command) {
        validate(command);
        var scope = FactoryContext.required();
        String hash = ProductionScanCode.digest(json.writeValueAsString(command));
        var previous = transaction.execute(s -> inputs.selectByPrimaryKey(command.requestId()));
        if (previous != null) return replay(previous, hash); // Never repeat an external create on replay.
        ProductionQuantityInput.check(command.outputQty());
        var calculation = supply.calculate(command.code(), command.workorderId(), command.stockReference(), command.outputQty());
        if (!Objects.equals(command.calculationKey(), ProductionInputSupplyService.calculationKey(calculation)))
            throw new FoundationException(ErrorCode.PRODUCTION_INPUT_STALE);
        Prepared prepared;
        try {
            prepared = transaction.execute(s -> prepare(command, calculation, hash));
        } catch (DuplicateKeyException conflict) {
            // Entire losing transaction rolled back, including its provisional execution/issue.
            var saved = transaction.execute(s -> inputs.selectByPrimaryKey(command.requestId()));
            if (saved != null) return replay(saved, hash);
            // A different valid request won this production identity; retry preparation once against its permanent binding.
            prepared = transaction.execute(s -> prepare(command, calculation, hash));
        }
        var row = submissions.executePrepared(scope, prepared);
        return transaction.execute(s -> result(inputs.selectByPrimaryKey(command.requestId()), row.getStatus()));
    }

    private Prepared prepare(Command c, Calculation calculation, String hash) {
        var order = locks.order(c.workorderId());
        supply.validateFrozenCalculation(calculation);
        var previous = inputs.selectByPrimaryKey(c.requestId());
        if (previous != null) {
            if (!previous.getRequestHash().equals(hash)) fail();
            return submissions.prepare(previous.getIssueId(), FactoryContext.required());
        }
        var product = materials.selectByPrimaryKey(order.getProductId());
        if (!WorkOrderStatus.RELEASED.getValue()
                .equals(order.getStatus()) || product == null || !Objects.equals(product.getCode(), calculation.order()
                .productCode())
                || order.getPlannedQty().compareTo(calculation.order().plannedQty()) != 0) fail();
        if (c.code().startsWith("MES1.") && !ProductionScanCode.parse(c.code()).productCode().equals(product.getCode()))
            fail();
        var identity = new ProductionScanCode(product.getCode(), calculation.stock().materialCode(), calculation.stock()
                .lotCode());
        var binding = bindings.selectByPrimaryKey(identity.key());
        Long executionId;
        if (binding == null) {
            var execution = executions.insert(new MesProductionExecutionCreateCommand(order.getId(), c.routingId(), c.outputQty(), ProductionExecutionStatus.READY.getValue(), null, null, "单材料扫码投产"));
            executionId = execution.getId();
            binding = new MesProductionScanIdentityEntity();
            binding.setId(identity.key());
            binding.setEncodedCode(identity.encode());
            binding.setProductCode(identity.productCode());
            binding.setMaterialCode(identity.materialCode());
            binding.setMaterialLotCode(identity.lotCode());
            binding.setExecutionId(executionId);
            binding.setCreatedAt(LocalDateTime.now());
            if (bindings.insert(binding) != 1) fail();
            paths.select(executionId, 0, c.routingNodeIds());
        } else {
            if (!identity.encode().equals(binding.getEncodedCode())) fail();
            executionId = binding.getExecutionId();
            var e = locks.execution(executionId);
            if (!Objects.equals(e.getWorkorderId(), order.getId()))
                throw new FoundationException(ErrorCode.PRODUCTION_IDENTITY_CONFLICT);
            if (!Objects.equals(e.getRoutingId(), c.routingId()) || !List.of(ProductionExecutionStatus.READY.getValue(), ProductionExecutionStatus.RUNNING.getValue())
                    .contains(e.getStatus())) fail();
            var path = paths.view(executionId);
            if (!path.selectedNodeIds().equals(c.routingNodeIds())) fail();
        }
        // Reserve output capacity before any external submission. Parent lock serializes concurrent intents.
        var execution = locks.execution(executionId);
        var required = quantities.getExecutionQuantities(executionId).confirmedInputQty()
                .add(allocation.pendingExecutionInput(executionId)).add(c.outputQty());
        if (required.compareTo(execution.getTargetQty()) > 0) {
            var capacity = allocation.activeAllocation(order.getId(), executionId);
            if (capacity == null || capacity.allocatedTargetQty().add(required).compareTo(capacity.plannedQty()) > 0)
                fail();
            execution.setTargetQty(required);
            execution.setUpdateTime(LocalDateTime.now());
            if (executionMapper.updateByPrimaryKey(execution) != 1) fail();
        }
        var issue = issues.insert(new MesMaterialIssueCreateCommand("INPUT-" + c.requestId(), order.getId(), null, order.getProductId(), null,
                null, c.routingId(), LocalDateTime.now(), MaterialIssueStatus.DRAFT.getValue(), null, executionId));
        var stock = calculation.stock();
        items.insert(new MesMaterialIssueItemCreateCommand(issue.getId(), stock.materialId(), stock.lotCode(), calculation.issueQty(), stock.unitId(), stock.warehouseId(), null));
        var f = new MesMaterialIssueInputEntity();
        f.setId(c.requestId());
        f.setRequestHash(hash);
        f.setIssueId(issue.getId());
        f.setExecutionId(executionId);
        f.setInputKind(c.inputKind());
        f.setOutputQty(c.outputQty());
        f.setSourceMode(calculation.sourceMode());
        f.setCalculationJson(json.writeValueAsString(calculation));
        f.setStockId(Mode.LOCAL_ONLY.getValue()
                .equals(calculation.sourceMode()) ? Long.valueOf(stock.reference()) : null);
        f.setStockApplied(false);
        f.setCreatedAt(LocalDateTime.now());
        inputFacts.freeze(f, calculation);
        return submissions.prepare(issue.getId(), FactoryContext.required());
    }

    private Result replay(MesMaterialIssueInputEntity previous, String hash) {
        if (!previous.getRequestHash().equals(hash)) fail();
        return transaction.execute(s -> result(previous, issueMapper.selectByPrimaryKey(previous.getIssueId())
                .getStatus()));
    }

    private Result result(MesMaterialIssueInputEntity f, String status) {
        var execution = executionMapper.selectByPrimaryKey(f.getExecutionId());
        var product = materials.selectByPrimaryKey(execution.getProductId());
        var c = json.readValue(f.getCalculationJson(), Calculation.class);
        String code = new ProductionScanCode(c.order().productCode(), c.stock().materialCode(), c.stock()
                .lotCode()).encode();
        var q = quantities.getExecutionQuantities(execution.getId());
        return new Result(f.getId(), f.getIssueId().toString(), f.getExecutionId()
                .toString(), execution.getExecutionNo(), code, status, q.productionInputQty().toPlainString(), q.nodes()
                .isEmpty() ? "0" : q.nodes().getFirst().availableQty().toPlainString());
    }

    private static void validate(Command c) {
        if (c == null || c.requestId() == null || !c.requestId()
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                || c.workorderId() == null || c.routingId() == null || c.routingNodeIds() == null || c.routingNodeIds()
                .isEmpty() || c.routingNodeIds().size() > 100) fail();
        if (!"PRODUCTION_INPUT".equals(c.inputKind()))
            throw new FoundationException(ErrorCode.PRODUCTION_SUPPLEMENT_UNSUPPORTED);
    }

    private static void fail() {
        throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
    }
}
