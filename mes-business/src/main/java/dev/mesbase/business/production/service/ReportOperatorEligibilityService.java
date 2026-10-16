package dev.mesbase.business.production.service;

import dev.mesbase.business.production.dao.MesExecutionAssignmentMapper;
import dev.mesbase.business.production.domain.MesExecutionAssignmentExample;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

/**
 * Shared qualification for trusted operator identities in reporting transactions and read-only candidate hints.
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ReportOperatorEligibilityService {
    private final OperatorQualificationRules rules;
    private final MesExecutionAssignmentMapper assignments;

    public ReportOperatorEligibilityService(OperatorQualificationRules rules,
                                            MesExecutionAssignmentMapper assignments) {
        this.rules = rules;
        this.assignments = assignments;
    }

    @Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, readOnly = true)
    public void requireEligible(Long executionId, Long routingNodeId, Long userId) {
        var node = rules.node(executionId, routingNodeId, false);
        rules.requireCapability(node.getOperationId(), userId);
        var filter = new MesExecutionAssignmentExample();
        var criteria = filter.createCriteria().andExecutionIdEqualTo(executionId).andRoutingNodeIdEqualTo(routingNodeId);
        if (assignments.countByExample(filter) == 0) return;
        criteria.andUserIdEqualTo(userId);
        if (assignments.countByExample(filter) == 0) throw new FoundationException(ErrorCode.OPERATOR_NOT_ASSIGNED);
    }

    /**
     * nodeOperations comes from the server's validated actual-path quantity model; submission also validates node membership.
     */
    public Map<Long, ErrorCode> ineligibilityReasons(Long executionId, Map<Long, Long> nodeOperations, Long userId) {
        var failures = rules.capabilityFailures(List.copyOf(nodeOperations.values()), userId);
        var result = new HashMap<Long, ErrorCode>();
        if (nodeOperations.isEmpty()) return result;
        var filter = new MesExecutionAssignmentExample();
        filter.createCriteria().andExecutionIdEqualTo(executionId)
                .andRoutingNodeIdIn(List.copyOf(nodeOperations.keySet()));
        var assigned = assignments.selectByExample(filter).stream()
                .collect(Collectors.groupingBy(row -> row.getRoutingNodeId()));
        nodeOperations.forEach((nodeId, operationId) -> {
            var failure = failures.get(operationId);
            if (failure != null) result.put(nodeId, failure);
            else if (assigned.containsKey(nodeId) && assigned.get(nodeId).stream()
                    .noneMatch(row -> userId.equals(row.getUserId())))
                result.put(nodeId, ErrorCode.OPERATOR_NOT_ASSIGNED);
        });
        return result;
    }

}
