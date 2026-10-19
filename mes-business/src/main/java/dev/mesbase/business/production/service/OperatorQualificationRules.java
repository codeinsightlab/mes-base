package dev.mesbase.business.production.service;

import dev.mesbase.business.production.dao.OperationMapper;
import dev.mesbase.business.production.dao.RoutingNodeMapper;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.MesOperationUserMapper;
import dev.mesbase.business.production.domain.MesOperationUserExample;
import dev.mesbase.business.production.domain.MesProductionExecutionEntity;
import dev.mesbase.business.production.domain.RoutingNodeEntity;
import dev.mesbase.business.production.domain.ProductionExecutionStatus;
import dev.mesbase.business.shared.enums.EnableStatus;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.platform.execution.FactoryContext;
import java.util.Objects;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import dev.mesbase.business.production.domain.OperationExample;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import dev.mesbase.business.production.service.ProductionChainLocks.LockedExecution;

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
    private final ProductionChainLocks locks;

    public OperatorQualificationRules(OperationMapper operations, RoutingNodeMapper nodes,
                                      MesProductionExecutionMapper executions, MesOperationUserMapper capabilities,
                                      OperationUserDirectory users, ProductionChainLocks locks) {
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

    public void cancelAssignment(Long executionId) {
        FactoryContext.required();
        locks.execution(executionId);
    }

    public void operation(Long operationId) {
        FactoryContext.required();
        var operation = operationId == null ? null : operations.selectByPrimaryKey(operationId);
        if (operation == null || !EnableStatus.ENABLED.getValue().equals(operation.getStatus()))
            throw new FoundationException(ErrorCode.ROUTING_REFERENCE_INVALID);
    }

    public RoutingNodeEntity node(Long executionId, Long nodeId, boolean editing) {
        FactoryContext.required();
        var execution = executionId == null ? null : editing ? locks.execution(executionId) : executions.selectByPrimaryKey(executionId);
        if (execution == null) throw new FoundationException(ErrorCode.EXECUTION_NOT_FOUND);
        if (editing) {
            locks.requireActiveExecution(executionId);
            requireMutable(execution);
        }
        return node(execution, nodeId);
    }

    public RoutingNodeEntity node(LockedExecution locked, Long nodeId) {
        return node(locked.execution(), nodeId);
    }

    private RoutingNodeEntity node(MesProductionExecutionEntity execution, Long nodeId) {
        var node = nodeId == null ? null : nodes.selectByPrimaryKey(nodeId);
        if (node == null || !Objects.equals(execution.getRoutingId(), node.getRoutingId()))
            throw new FoundationException(ErrorCode.ASSIGNMENT_NODE_INVALID);
        operation(node.getOperationId());
        return node;
    }

    public void requireCapability(Long operationId, Long userId) {
        capability(operationId, userId);
        var filter = new MesOperationUserExample();
        filter.createCriteria().andOperationIdEqualTo(operationId).andUserIdEqualTo(userId);
        if (capabilities.countByExample(filter) == 0)
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
        var capFilter = new MesOperationUserExample();
        capFilter.createCriteria().andOperationIdIn(ids).andUserIdEqualTo(userId);
        var capable = capabilities.selectByExample(capFilter).stream().map(row -> row.getOperationId())
                .collect(Collectors.toSet());
        for (Long id : ids) {
            if (!enabled.contains(id)) failures.put(id, ErrorCode.ROUTING_REFERENCE_INVALID);
            else if (!capable.contains(id)) failures.put(id, ErrorCode.OPERATOR_CAPABILITY_REQUIRED);
        }
        return failures;
    }

    public static void requireMutable(MesProductionExecutionEntity execution) {
        var status = ProductionExecutionStatus.from(execution.getStatus());
        if (status != ProductionExecutionStatus.READY && status != ProductionExecutionStatus.RUNNING)
            throw new FoundationException(ErrorCode.ASSIGNMENT_EXECUTION_CLOSED);
    }
}
