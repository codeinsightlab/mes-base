package dev.mesbase.business.production.service;

import dev.mesbase.business.production.dao.MesOperationUserMapper;
import dev.mesbase.business.production.dao.MesOperatorQualificationMapper;
import java.util.Set;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.OperationMapper;
import dev.mesbase.business.production.dao.RoutingNodeMapper;
import dev.mesbase.business.production.domain.MesOperationUserExample;
import dev.mesbase.business.production.domain.MesProductionExecutionEntity;
import dev.mesbase.business.production.domain.OperationExample;
import dev.mesbase.business.production.domain.ProductionExecutionStatus;
import dev.mesbase.business.production.domain.RoutingNodeEntity;
import dev.mesbase.business.production.service.ProductionChainLocks.LockedExecution;
import dev.mesbase.business.shared.enums.EnableStatus;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.platform.execution.FactoryContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Shared production qualification rules; authorization remains at the HTTP boundary.
 */
@Component
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class OperatorQualificationRules {
    private final OperationMapper operations;
    private final RoutingNodeMapper nodes;
    private final MesProductionExecutionMapper executions;
    private final MesOperationUserMapper capabilities;
    private final OperationUserDirectory users;
    private final MesOperatorQualificationMapper qualifications;
    private final ProductionChainLocks locks;

    public OperatorQualificationRules(OperationMapper operations, RoutingNodeMapper nodes,
                                      MesProductionExecutionMapper executions, MesOperationUserMapper capabilities,
                                      OperationUserDirectory users, ProductionChainLocks locks,
                                      MesOperatorQualificationMapper qualifications) {
        this.qualifications = qualifications;
        this.operations = operations;
        this.nodes = nodes;
        this.executions = executions;
        this.capabilities = capabilities;
        this.users = users;
        this.locks = locks;
    }

    public void capability(Long operationId, Long userId) {
        operation(operationId);
        users.requireValid(userId);
    }

    public void capabilities(Long operationId, List<Long> userIds) {
        operation(operationId);
        if (users.validUsers(userIds).size() != userIds.size())
            throw new FoundationException(ErrorCode.OPERATOR_NOT_AUTHORIZED);
    }

    public void cancelAssignment(String executionCode) {
        FactoryContext.required();
        locks.execution(executionCode);
    }

    public void operation(Long operationId) {
        FactoryContext.required();
        var operation = Optional.ofNullable(operationId)
                .map(nullableValue -> operations.selectByPrimaryKey(nullableValue)).orElse(null);
        if (operation == null || !EnableStatus.ENABLED.getValue().equals(operation.getStatus()))
            throw new FoundationException(ErrorCode.ROUTING_REFERENCE_INVALID);
    }

    public RoutingNodeEntity node(String executionCode, Long nodeId, boolean editing) {
        FactoryContext.required();
        var execution = Optional.ofNullable(executionCode)
                .map(nullableValue -> editing ? locks.execution(nullableValue) : executions.selectByCode(nullableValue))
                .orElse(null);
        if (execution == null) throw new FoundationException(ErrorCode.EXECUTION_NOT_FOUND);
        if (editing) {
            locks.requireActiveExecution(executionCode);
            requireMutable(execution);
        }
        return node(execution, nodeId);
    }

    public RoutingNodeEntity node(LockedExecution locked, Long nodeId) {
        return node(locked.execution(), nodeId);
    }

    private RoutingNodeEntity node(MesProductionExecutionEntity execution, Long nodeId) {
        var node = Optional.ofNullable(nodeId).map(nullableValue -> nodes.selectByPrimaryKey(nullableValue))
                .orElse(null);
        if (node == null || !Objects.equals(execution.getRoutingId(), node.getRoutingId()))
            throw new FoundationException(ErrorCode.ASSIGNMENT_NODE_INVALID);
        operation(node.getOperationId());
        return node;
    }

    public void requireCapability(Long operationId, Long userId) {
        capability(operationId, userId);
        if (!capableOperationIds(List.of(operationId), userId).contains(operationId))
            throw new FoundationException(ErrorCode.OPERATOR_CAPABILITY_REQUIRED);
    }

    /**
     * Batched display/submit qualification; authorization is queried once, never cached across requests.
     */
    public Map<Long, ErrorCode> capabilityFailures(List<Long> operationIds, Long userId) {
        FactoryContext.required();
        var ids = operationIds.stream().distinct().toList();
        if (ids.size() > 100) throw new FoundationException(ErrorCode.BAD_REQUEST);
        var failures = new HashMap<Long, ErrorCode>();
        if (ids.isEmpty()) return failures;
        if (userId == null || users.validUsers(List.of(userId)).isEmpty()) {
            ids.forEach(id -> failures.put(id, ErrorCode.OPERATOR_NOT_AUTHORIZED));
            return failures;
        }
        var opFilter = new OperationExample();
        opFilter.createCriteria().andIdIn(ids).andStatusEqualTo(EnableStatus.ENABLED.getValue());
        var enabled = operations.selectByExample(opFilter).stream().map(row -> row.getId()).collect(Collectors.toSet());
        var capable = capableOperationIds(ids, userId);
        for (Long id : ids) {
            if (!enabled.contains(id)) failures.put(id, ErrorCode.ROUTING_REFERENCE_INVALID);
            else if (!capable.contains(id)) failures.put(id, ErrorCode.OPERATOR_CAPABILITY_REQUIRED);
        }
        return failures;
    }

    /**
     * Identity and enabled-operation checks stay with the caller; both read paths share this grant rule.
     */
    private Set<Long> capableOperationIds(List<Long> operationIds, Long userId) {
        var setting = qualifications.selectByPrimaryKey(userId);
        if (setting != null && Boolean.TRUE.equals(setting.getAllOperations())) return Set.copyOf(operationIds);
        var filter = new MesOperationUserExample();
        filter.createCriteria().andOperationIdIn(operationIds).andUserIdEqualTo(userId);
        return capabilities.selectByExample(filter).stream().map(row -> row.getOperationId())
                .collect(Collectors.toSet());
    }

    public static void requireMutable(MesProductionExecutionEntity execution) {
        var status = ProductionExecutionStatus.from(execution.getStatus());
        if (status != ProductionExecutionStatus.READY && status != ProductionExecutionStatus.RUNNING)
            throw new FoundationException(ErrorCode.ASSIGNMENT_EXECUTION_CLOSED);
    }
}
