package dev.mesbase.business.production.service;

import dev.mesbase.business.production.dao.MesExecutionAssignmentMapper;
import dev.mesbase.business.production.domain.MesExecutionAssignmentExample;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

/**
 * Call with trusted operator identity inside the future reporting business transaction.
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
        var criteria = filter.createCriteria().andExecutionIdEqualTo(executionId)
                .andRoutingNodeIdEqualTo(routingNodeId);
        if (assignments.countByExample(filter) == 0) return;
        criteria.andUserIdEqualTo(userId);
        if (assignments.countByExample(filter) == 0) throw new FoundationException(ErrorCode.OPERATOR_NOT_ASSIGNED);
    }
}
