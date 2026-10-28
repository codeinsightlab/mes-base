package dev.mesbase.business.production.service;

import dev.mesbase.common.utils.RequestFingerprint;
import tools.jackson.core.JacksonException;
import dev.mesbase.business.wm.service.StockDocumentService;
import dev.mesbase.business.wm.domain.StockDocumentModels;
import dev.mesbase.business.wm.domain.StockDocumentStatus;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.core.security.AuthenticatedActor;
import dev.mesbase.core.security.AuthorizationService;
import dev.mesbase.core.security.Permission;
import org.springframework.beans.factory.ObjectProvider;

import dev.mesbase.common.validation.ParameterChecks;

import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;
import dev.mesbase.business.shared.enums.BusinessSource;
import dev.mesbase.business.issue.domain.MaterialIssueStatus;
import dev.mesbase.business.production.domain.ProductionExecutionStatus;
import dev.mesbase.business.production.domain.WorkOrderStatus;
import dev.mesbase.business.production.domain.ProductionScanCode;
import dev.mesbase.business.production.domain.ProductionCodeRules;
import dev.mesbase.business.production.domain.MesProductionExecutionCreateCommand;
import dev.mesbase.business.production.domain.MesProductionExecutionResource;
import dev.mesbase.infrastructure.cache.BusinessCacheProcessor;
import dev.mesbase.infrastructure.cache.annotation.BusinessCacheEvict;
import dev.mesbase.business.production.service.IMesProductionExecutionService;
import dev.mesbase.business.issue.domain.MesMaterialIssueCreateCommand;
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
import dev.mesbase.system.service.AutoCodeNumbering;
import dev.mesbase.system.service.AutoCodeRuleType;
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
    private final ObjectProvider<StockDocumentService> documents;
    private final AuthorizationService authorization;
    private final WorkOrderExtraMapper allocation;
    private final BusinessSourceRules sources;

    public record Command(String requestId, String code, Long workorderId, String stockReference,
                          BigDecimal outputQty, Long routingId, List<Long> routingNodeIds, String inputKind,
                          String calculationKey, String ruleCode) {
        public Command(String requestId, String code, Long workorderId, String stockReference,
                       BigDecimal outputQty, Long routingId, List<Long> routingNodeIds, String inputKind,
                       String calculationKey) {
            this(requestId, code, workorderId, stockReference, outputQty, routingId, routingNodeIds, inputKind,
                    calculationKey, null);
        }
    }

    public record Result(String requestId, String issueId, String executionId, String code,
                         String status, String productionInputQty, String availableQty) {
    }

    private final ProductionInputSupplyService supply;
    private final ProductionIdentityService identities;
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
    private final ProductionExecutionRules executionRules;
    private final BusinessCacheProcessor caches;
    private final WorkorderMapper orderMapper;
    private final MaterialMapper materials;
    private final RoutingMapper routings;
    private final IRoutingService routingService;
    private final TransactionTemplate transaction;
    private final AutoCodeNumbering autoCodes;
    private final ObjectMapper json = new ObjectMapper();

    public ProductionInputService(ProductionInputSupplyService supply, ProductionIdentityService identities,
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
                                  BusinessSourceRules sources, WorkOrderExtraMapper allocation,
                                  ProductionExecutionRules executionRules, BusinessCacheProcessor caches,
                                  ObjectProvider<StockDocumentService> documents, AuthorizationService authorization,
                                  AutoCodeNumbering autoCodes) {
        this.documents = documents;
        this.authorization = authorization;
        this.autoCodes = autoCodes;
        this.caches = caches;
        this.executionRules = executionRules;
        this.allocation = allocation;
        this.sources = sources;
        this.supply = supply;
        this.identities = identities;
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
        var scanned = ProductionCodeRules.parse(code);
        var order = identities.requireInputOrder(scanned);
        if (supply.query(code, workorderId, 0, 1).orders().isEmpty())
            ParameterChecks.reject(ErrorCode.PRODUCTION_INPUT_STALE);
        if (!Objects.equals(order.getId(), workorderId)) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        sources.order(workorderId);
        var product = materials.selectByPrimaryKey(order.getProductId());
        if (product == null) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        var execution = identities.findExecution(workorderId, product.getId(), scanned.batchNo());
        if (execution == null) return new Preview(routingService.detail(order.getRoutingId()), null);
        return new Preview(routingService.detail(execution.getRoutingId()), paths.view(execution.getId()));
    }

    private WorkorderEntity readOrder(Long id) {
        var order = orderMapper.selectByPrimaryKey(id);
        if (order == null) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        return order;
    }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.NOT_SUPPORTED)
    public Result submit(Command command) {
        validate(command);
        var scope = FactoryContext.required();
        String hash = RequestFingerprint.sha256(json.writeValueAsString(command));
        var previous = transaction.execute(s -> inputs.selectByPrimaryKey(command.requestId()));
        if (previous != null) return replay(previous, hash); // Never repeat an external create on replay.
        supply.validateOutputQuantity(command.outputQty(), command.workorderId());
        var sourceOrder = transaction.execute(s -> sources.order(command.workorderId()));
        if (BusinessSource.LOCAL.getValue().equals(sourceOrder.getSource())) {
            requireDocumentPermissions(true);
            // The fast path also creates a formal stock-out document. Reserve it before
            // entering the factory transaction, through the same numbered draft path.
            if (documents.getObject().findMaterialIssueDraft(command) == null) saveDraft(command);
            return transaction.execute(s -> {
                locks.order(command.workorderId());
                var saved = inputs.selectByPrimaryKey(command.requestId());
                if (saved != null) return replay(saved, hash);
                var draft = documents.getObject().findMaterialIssueDraft(command);
                if (draft != null) {
                    approveDraft(draft.document().getId(), new StockDocumentModels.Post(command.requestId(),
                            draft.document().getRevision()));
                    return result(inputs.selectByPrimaryKey(command.requestId()), MaterialIssueStatus.CONFIRMED.getValue());
                }
                var calculation = supply.calculateLocal(command.code(), command.workorderId(), command.stockReference(), command.outputQty());
                if (!Objects.equals(command.calculationKey(), ProductionInputSupplyService.calculationKey(calculation)))
                    throw new FoundationException(ErrorCode.PRODUCTION_INPUT_STALE);
                var localPrepared = prepare(command, calculation, hash);
                var row = submissions.executePrepared(scope, localPrepared);
                if (!MaterialIssueStatus.CONFIRMED.getValue().equals(row.getStatus()))
                    ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
                return result(inputs.selectByPrimaryKey(command.requestId()), row.getStatus());
            });
        }
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
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, conflict);
        }
        var row = submissions.executePrepared(scope, prepared);
        return transaction.execute(s -> result(inputs.selectByPrimaryKey(command.requestId()), row.getStatus()));
    }

    public void requireDocumentPermissions(boolean approve) {
        permit("business:mes:material:issue:update");
        permit("business:wm:stock:document:create");
        permit("business:wm:stock:document:update");
        if (approve) permit("business:wm:stock:document:post");
    }

    public void requireApprovalPermissions() {
        permit("business:mes:material:issue:update");
        permit("business:wm:stock:document:post");
    }

    private void permit(String permission) {
        if (!authorization.hasPermission(new AuthenticatedActor(SecurityUtils.getAuthentication().getName()),
                FactoryContext.required(), new Permission(permission)))
            throw new FoundationException(ErrorCode.FORBIDDEN);
    }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.NOT_SUPPORTED,
            rollbackFor = Exception.class)
    public StockDocumentModels.Detail saveDraft(Command command) {
        validate(command);
        requireDocumentPermissions(false);
        var existing = documents.getObject().findMaterialIssueDraft(command);
        if (existing != null) return existing;
        String ruleCode = command.ruleCode() == null || command.ruleCode().isBlank()
                ? autoCodes.uniqueRuleCode(AutoCodeRuleType.STOCK_OUT.name()) : command.ruleCode();
        String documentNo = autoCodes.getDocNo(ruleCode, AutoCodeRuleType.STOCK_OUT.name());
        return transaction.execute(status -> saveDraftInTransaction(command, documentNo));
    }

    private StockDocumentModels.Detail saveDraftInTransaction(Command command, String documentNo) {
        validate(command);
        requireDocumentPermissions(false);
        locks.order(command.workorderId());
        supply.validateOutputQuantity(command.outputQty(), command.workorderId());
        var existing = documents.getObject().findMaterialIssueDraft(command);
        if (existing != null) return existing;
        if (inputs.selectByPrimaryKey(command.requestId()) != null) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        var calculation = supply.calculateLocal(command.code(), command.workorderId(), command.stockReference(), command.outputQty());
        if (!Objects.equals(command.calculationKey(), ProductionInputSupplyService.calculationKey(calculation)))
            throw new FoundationException(ErrorCode.PRODUCTION_INPUT_STALE);
        return documents.getObject().createMaterialIssueDraft(command, calculation, documentNo);
    }

    /**
     * The warehouse owner enters this method before an input/Execution exists.
     */
    @Transactional(transactionManager = "businessTransactionManager", rollbackFor = Exception.class)
    public StockDocumentModels.Detail approveDraft(Long documentId, StockDocumentModels.Post approval) {
        requireApprovalPermissions();
        var owner = documents.getObject();
        var before = owner.detail(documentId).document();
        if (before.getSourceRequestJson() == null) ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_SOURCE_PENDING);
        Command command;
        try {
            command = json.readValue(before.getSourceRequestJson(), Command.class);
        } catch (JacksonException failure) {
            throw new FoundationException(ErrorCode.STOCK_DOCUMENT_CONFLICT, failure);
        }
        validate(command);
        if (!Objects.equals(command.requestId(), approval.requestId()))
            ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
        locks.order(command.workorderId());
        // Order serializes all allocation and UUID decisions, then the document is locked by bind/approve.
        var current = owner.detail(documentId).document();
        if (current.getSourceMaterialIssueId() != null) return owner.approve(documentId, approval);
        if (!Objects.equals(current.getRevision(), approval.expectedRevision())
                || !StockDocumentStatus.DRAFT.getValue().equals(current.getStatus()))
            ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
        var calculation = supply.calculateLocal(command.code(), command.workorderId(), command.stockReference(), command.outputQty());
        if (!Objects.equals(command.calculationKey(), ProductionInputSupplyService.calculationKey(calculation)))
            throw new FoundationException(ErrorCode.PRODUCTION_INPUT_STALE);
        String hash = RequestFingerprint.sha256(json.writeValueAsString(command));
        if (inputs.selectByPrimaryKey(command.requestId()) != null)
            ParameterChecks.reject(ErrorCode.STOCK_DOCUMENT_CONFLICT);
        // Current BOM, cumulative issue difference and order/stock balances are recalculated at approval.
        var prepared = prepare(command, calculation, hash);
        var input = inputs.selectByPrimaryKey(command.requestId());
        owner.bindMaterialIssue(documentId, input, calculation);
        var result = submissions.executePrepared(FactoryContext.required(), prepared);
        if (!MaterialIssueStatus.CONFIRMED.getValue().equals(result.getStatus()))
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        return owner.detail(documentId);
    }

    private Prepared prepare(Command c, Calculation calculation, String hash) {
        var order = locks.order(c.workorderId());
        supply.lockQuantityMaterials(calculation);
        supply.validateFrozenCalculation(calculation);
        var previous = inputs.selectByPrimaryKey(c.requestId());
        if (previous != null) {
            if (!previous.getRequestHash().equals(hash)) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
            return submissions.prepare(previous.getIssueId(), FactoryContext.required());
        }
        var product = materials.selectByPrimaryKey(order.getProductId());
        if (!WorkOrderStatus.RELEASED.getValue()
                .equals(order.getStatus()) || product == null || !Objects.equals(product.getCode(), calculation.order()
                .productCode())
                || order.getPlannedQty().compareTo(calculation.order().plannedQty()) != 0)
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        String batchNo = calculation.stock().lotCode();
        // The source stock supplies the permanent batch property. The barcode is only a lookup representation.
        var scanned = ProductionCodeRules.parse(c.code());
        if (!order.getCode().equals(scanned.workorderCode()) || !calculation.stock().materialCode()
                .equals(scanned.productCode())
                || !Objects.equals(batchNo, scanned.batchNo())) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        var existingExecution = identities.findExecution(order.getId(), product.getId(), batchNo);
        Long executionId;
        if (existingExecution == null) {
            var execution = executions.insert(new MesProductionExecutionCreateCommand(order.getId(), c.routingId(), c.outputQty(), ProductionExecutionStatus.READY.getValue(), null, null, "单材料正式投产"));
            execution.setBatchNo(batchNo);
            if (executionMapper.updateByPrimaryKey(execution) != 1) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
            executionId = execution.getId();
            paths.select(executionId, 0, c.routingNodeIds());
        } else {
            executionId = existingExecution.getId();
            var e = locks.execution(executionId);
            if (!Objects.equals(e.getRoutingId(), c.routingId()) || !List.of(ProductionExecutionStatus.READY.getValue(), ProductionExecutionStatus.RUNNING.getValue(), ProductionExecutionStatus.FULLY_RECEIVED.getValue())
                    .contains(e.getStatus())) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
            var path = paths.view(executionId);
            if (!path.selectedNodeIds().equals(c.routingNodeIds())) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        }
        // Reserve output capacity before any external submission. Parent lock serializes concurrent intents.
        var execution = locks.execution(executionId);
        var required = quantities.getExecutionQuantities(executionId).confirmedInputQty()
                .add(allocation.pendingExecutionInput(executionId)).add(c.outputQty());
        if (required.compareTo(execution.getTargetQty()) > 0) {
            var capacity = allocation.activeAllocation(order.getId(), executionId);
            if (capacity == null || capacity.allocatedTargetQty().add(required).compareTo(capacity.plannedQty()) > 0)
                ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
            execution.setTargetQty(required);
            execution.setUpdateTime(LocalDateTime.now());
            if (executionMapper.updateByPrimaryKey(execution) != 1) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        }
        if (Mode.LOCAL_ONLY.getValue().equals(calculation.sourceMode())
                && ProductionExecutionStatus.FULLY_RECEIVED.getValue().equals(execution.getStatus()))
            executionRules.resumeAfterInput(execution);
        var issue = issues.insert(new MesMaterialIssueCreateCommand("INPUT-" + c.requestId(), order.getId(), null, order.getProductId(), null,
                null, c.routingId(), LocalDateTime.now(), MaterialIssueStatus.DRAFT.getValue(), null, executionId));
        var stock = calculation.stock();
        items.insertCalculated(issue.getId(), calculation);
        var f = new MesMaterialIssueInputEntity();
        f.setId(c.requestId());
        f.setRequestHash(hash);
        f.setIssueId(issue.getId());
        f.setExecutionId(executionId);
        f.setInputKind(c.inputKind());
        f.setOutputQty(c.outputQty());
        f.setSourceMode(calculation.sourceMode());
        f.setCalculationJson(json.writeValueAsString(calculation));
        if (Mode.LOCAL_ONLY.getValue().equals(calculation.sourceMode()))
            f.setStockId(Long.valueOf(stock.reference()));
        f.setStockApplied(false);
        f.setCreatedAt(LocalDateTime.now());
        inputFacts.freeze(f, calculation);
        // Capacity and resumed status are written through locked Mappers; publish neither stale lists nor details.
        // The shared processor defers invalidation until this enclosing transaction commits.
        caches.evict(MesProductionExecutionResource.PREFIX, BusinessCacheEvict.Scope.LIST_AND_DETAIL);
        return submissions.prepare(issue.getId(), FactoryContext.required());
    }

    private Result replay(MesMaterialIssueInputEntity previous, String hash) {
        if (!previous.getRequestHash().equals(hash)) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        return transaction.execute(s -> result(previous, issueMapper.selectByPrimaryKey(previous.getIssueId())
                .getStatus()));
    }

    private Result result(MesMaterialIssueInputEntity f, String status) {
        var execution = executionMapper.selectByPrimaryKey(f.getExecutionId());
        var product = materials.selectByPrimaryKey(execution.getProductId());
        String code = null;
        if (execution.getBatchNo() != null)
            code = identities.read(execution.getId()).qrCode();
        var q = quantities.getExecutionQuantities(execution.getId());
        return new Result(f.getId(), f.getIssueId().toString(), f.getExecutionId()
                .toString(), code, status, q.productionInputQty().toPlainString(), q.nodes()
                .isEmpty() ? "0" : q.nodes().getFirst().availableQty().toPlainString());
    }

    private static void validate(Command c) {
        if (c == null || c.requestId() == null || !c.requestId()
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                || c.workorderId() == null || c.routingId() == null || c.routingNodeIds() == null || c.routingNodeIds()
                .isEmpty() || c.routingNodeIds().size() > 100) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        if (!"PRODUCTION_INPUT".equals(c.inputKind()))
            throw new FoundationException(ErrorCode.PRODUCTION_SUPPLEMENT_UNSUPPORTED);
    }

}
