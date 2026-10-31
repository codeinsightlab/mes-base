package dev.mesbase.business.production.service;


import dev.mesbase.business.issue.domain.MesMaterialIssueInputEntity;
import dev.mesbase.business.issue.service.MaterialIssueInputFacts.CombinationInput;
import dev.mesbase.business.issue.service.MaterialIssueInputFacts;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.ProductionInputExtraMapper;
import dev.mesbase.business.production.dao.WorkorderMapper;
import dev.mesbase.business.production.domain.MesProductionExecutionCreateCommand;
import dev.mesbase.business.production.domain.MesProductionExecutionResource;
import dev.mesbase.business.production.domain.ProductionCombinationModels.Confirm;
import dev.mesbase.business.production.domain.ProductionCombinationModels;
import dev.mesbase.business.production.domain.ProductionCombinationRules;
import dev.mesbase.business.production.domain.ProductionExecutionStatus;
import dev.mesbase.business.production.domain.ProductionInputKind;
import dev.mesbase.business.production.domain.RoutingDetail;
import dev.mesbase.business.production.service.ExecutionPathService.PathModel;
import dev.mesbase.business.wm.domain.StockDocumentDirection;
import dev.mesbase.business.wm.domain.StockDocumentType;
import dev.mesbase.business.wm.service.StockDocumentService;
import dev.mesbase.common.utils.RequestFingerprint;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.common.validation.ParameterChecks;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.security.AuthenticatedActor;
import dev.mesbase.core.security.AuthorizationService;
import dev.mesbase.core.security.Permission;
import dev.mesbase.infrastructure.cache.BusinessCacheProcessor;
import dev.mesbase.infrastructure.cache.annotation.BusinessCacheEvict;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.system.service.AutoCodeNumbering;
import dev.mesbase.system.service.AutoCodeRuleType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * MES owns the command; the stock document owner never calls this service in reverse.
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ProductionInputService {
    /**
     * Original frozen request shape retained solely for immutable document history and UUID reads.
     */
    public record Command(String requestId, String code, Long workorderId, String stockReference,
                          BigDecimal outputQty, Long routingId, List<Long> routingNodeIds, String inputKind,
                          String calculationKey, String ruleCode) {
        public Command(String requestId, String code, Long workorderId, String stockReference,
                       BigDecimal outputQty, Long routingId, List<Long> routingNodeIds, String inputKind,
                       String calculationKey) {
            this(requestId, code, workorderId, stockReference, outputQty, routingId, routingNodeIds, inputKind, calculationKey, null);
        }
    }

    public record Preview(RoutingDetail definition, PathModel actual) {
    }

    private final ProductionCombinationService combinations;
    private final StockDocumentService documents;
    private final MaterialIssueInputFacts facts;
    private final ProductionInputExtraMapper inputQueries;
    private final ProductionChainLocks locks;
    private final IMesProductionExecutionService executions;
    private final MesProductionExecutionMapper executionMapper;
    private final WorkorderMapper orders;
    private final ExecutionPathService paths;
    private final IRoutingService routings;
    private final MaterialQuantityRules quantityRules;
    private final AuthorizationService authorization;
    private final AutoCodeNumbering autoCodes;
    private final BusinessCacheProcessor caches;
    private final TransactionTemplate transaction;
    private final ObjectMapper json = new ObjectMapper();

    public ProductionInputService(ProductionCombinationService combinations, StockDocumentService documents,
                                  MaterialIssueInputFacts facts, ProductionInputExtraMapper inputQueries,
                                  ProductionChainLocks locks,
                                  IMesProductionExecutionService executions,
                                  MesProductionExecutionMapper executionMapper, WorkorderMapper orders,
                                  ExecutionPathService paths, IRoutingService routings,
                                  MaterialQuantityRules quantityRules,
                                  AuthorizationService authorization, AutoCodeNumbering autoCodes,
                                  BusinessCacheProcessor caches,
                                  @Qualifier("businessTransactionManager") PlatformTransactionManager manager) {
        this.combinations = combinations;
        this.documents = documents;
        this.facts = facts;
        this.inputQueries = inputQueries;
        this.locks = locks;
        this.executions = executions;
        this.executionMapper = executionMapper;
        this.orders = orders;
        this.paths = paths;
        this.routings = routings;
        this.quantityRules = quantityRules;
        this.authorization = authorization;
        this.autoCodes = autoCodes;
        this.caches = caches;
        transaction = new TransactionTemplate(manager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public Preview preview(String code, Long workorderId) {
        var combination = combinations.detail(ProductionCombinationRules.parse(code, FactoryContext.required()
                .factoryId().value()));
        if (!Objects.equals(combination.workorderId(), workorderId)) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        var order = orders.selectByPrimaryKey(workorderId);
        if (order == null) ParameterChecks.reject(ErrorCode.NOT_FOUND);
        if (combination.executionId() == null) return new Preview(routings.detail(order.getRoutingId()), null);
        var execution = executionMapper.selectByPrimaryKey(combination.executionId());
        if (execution == null) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        return new Preview(routings.detail(execution.getRoutingId()), paths.view(execution.getId()));
    }

    public void requireDocumentPermission() {
        permit("business:mes:material:issue:update");
    }

    private void permit(String permission) {
        if (!authorization.hasPermission(new AuthenticatedActor(SecurityUtils.getAuthentication().getName()),
                FactoryContext.required(), new Permission(permission)))
            throw new FoundationException(ErrorCode.FORBIDDEN);
    }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.NOT_SUPPORTED)
    public ProductionCombinationModels.Result confirm(Confirm command) {
        requireDocumentPermission();
        validate(command);
        Long combinationId = ProductionCombinationRules.parse(command.code(), FactoryContext.required().factoryId()
                .value());
        record Intent(Long actor, String code, String quantity, Long routingId, List<Long> nodes, String key) {
        }
        String hash = RequestFingerprint.sha256(json.writeValueAsString(new Intent(SecurityUtils.getUserId(), command.code(),
                command.outputQty().stripTrailingZeros()
                        .toPlainString(), command.routingId(), command.routingNodeIds(), command.calculationKey())));
        var previous = transaction.execute(s -> facts.findByRequestId(command.requestId()));
        if (previous != null) return replay(previous, hash, command.code());
        documents.requireTypeActive(StockDocumentDirection.OUT.getValue(), StockDocumentType.MATERIAL_ISSUE.getValue());
        String number = autoCodes.getBusinessDocNo(StockDocumentType.MATERIAL_ISSUE.getValue(), AutoCodeRuleType.STOCK_OUT.name());
        try {
            return transaction.execute(status -> confirmInTransaction(combinationId, command, hash, number));
        } catch (DuplicateKeyException failure) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT, failure);
        }
    }

    private ProductionCombinationModels.Result confirmInTransaction(Long combinationId, Confirm command, String hash,
                                                                    String number) {
        var before = combinations.require(combinationId);
        var order = locks.order(before.getWorkorderId());
        var existing = facts.findByRequestId(command.requestId());
        if (existing != null) return replay(existing, hash, command.code());
        var header = combinations.require(combinationId);
        var detail = combinations.detail(combinationId);
        if (detail.closed()) ParameterChecks.reject(ErrorCode.PRODUCTION_COMBINATION_CLOSED);
        var materialIds = new ArrayList<>(detail.items().stream().map(i -> i.materialId()).distinct().toList());
        materialIds.add(order.getProductId());
        quantityRules.lockForWrite(materialIds);
        var calculation = combinations.calculate(header, order, command.outputQty());
        if (!Objects.equals(command.calculationKey(), calculation.calculationKey()))
            ParameterChecks.reject(ErrorCode.PRODUCTION_INPUT_STALE);
        if (!Objects.equals(command.routingId(), order.getRoutingId()))
            ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        Long executionId = header.getExecutionId();
        if (executionId == null) {
            var created = executions.insert(new MesProductionExecutionCreateCommand(order.getId(), command.routingId(),
                    command.outputQty(), ProductionExecutionStatus.READY.getValue(), null, null, "组合二维码MES扫码投产"));
            // A logical output batch is generated once, independently of every material lot.
            created.setBatchNo("P-" + UUID.randomUUID());
            if (executionMapper.updateByPrimaryKey(created) != 1) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
            executionId = created.getId();
            paths.select(executionId, 0, command.routingNodeIds());
            combinations.bind(combinationId, executionId);
        } else {
            var execution = locks.execution(executionId);
            if (!List.of(ProductionExecutionStatus.READY.getValue(), ProductionExecutionStatus.RUNNING.getValue())
                    .contains(execution.getStatus())
                    || !Objects.equals(execution.getRoutingId(), command.routingId()))
                ParameterChecks.reject(ErrorCode.PRODUCTION_COMBINATION_CLOSED);
            if (!paths.view(executionId).selectedNodeIds().equals(command.routingNodeIds()))
                ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
            var target = inputQueries.confirmedExecutionOutput(executionId).add(command.outputQty());
            if (target.compareTo(execution.getTargetQty()) > 0) {
                execution.setTargetQty(target);
                execution.setUpdateTime(LocalDateTime.now());
                if (executionMapper.updateByPrimaryKey(execution) != 1)
                    ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
            }
        }
        if (order.getMaterialFrozenAt() == null) {
            order.setMaterialFrozenAt(LocalDateTime.now());
            if (orders.updateByPrimaryKey(order) != 1) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        }
        var frozen = new Command(command.requestId(), command.code(), order.getId(), null, command.outputQty(),
                command.routingId(), command.routingNodeIds(), ProductionInputKind.PRODUCTION_INPUT.getValue(), command.calculationKey(), StockDocumentType.MATERIAL_ISSUE.getValue());
        var document = documents.createMesIssue(order.getId(), executionId, number, command.requestId(),
                json.writeValueAsString(frozen), calculation.allocations());
        facts.recordMesConfirmation(document.document(), command.requestId(), hash, executionId,
                new CombinationInput(2, combinationId, order.getId(), header.getDemandRevision(), command.outputQty(), calculation.allocations()));
        var execution = locks.execution(executionId);
        var path = paths.requireSelected(execution);
        paths.confirm(execution, path.getRevision());
        caches.evict(MesProductionExecutionResource.PREFIX, BusinessCacheEvict.Scope.LIST_AND_DETAIL);
        return result(command.requestId(), document.document().getId(), executionId, command.code());
    }

    private ProductionCombinationModels.Result replay(MesMaterialIssueInputEntity previous, String hash, String code) {
        if (!hash.equals(previous.getRequestHash())) ParameterChecks.reject(ErrorCode.RESOURCE_CONFLICT);
        return transaction.execute(s -> result(previous.getId(), previous.getDocumentId(), previous.getExecutionId(), code));
    }

    private ProductionCombinationModels.Result result(String requestId, Long documentId, Long executionId,
                                                      String code) {
        return new ProductionCombinationModels.Result(requestId, documentId, executionId, code,
                documents.detail(documentId).document().getStatus(), inputQueries.confirmedExecutionOutput(executionId),
                Boolean.TRUE.equals(facts.findByRequestId(requestId).getStockApplied()));
    }

    private static void validate(Confirm c) {
        if (c == null || c.requestId() == null || c.outputQty() == null || c.outputQty().signum() <= 0
                || c.routingId() == null || c.routingNodeIds() == null || c.routingNodeIds().isEmpty()
                || c.routingNodeIds().size() > 100 || c.calculationKey() == null || c.calculationKey().length() != 64)
            ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        try {
            if (!UUID.fromString(c.requestId()).toString().equals(c.requestId()))
                ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        } catch (IllegalArgumentException failure) {
            throw new FoundationException(ErrorCode.BAD_REQUEST, failure);
        }
        ProductionCombinationRules.parse(c.code(), FactoryContext.required().factoryId().value());
    }
}
