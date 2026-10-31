package dev.mesbase.business.production.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.production.dao.MesOperationUserMapper;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.RoutingNodeMapper;
import dev.mesbase.business.production.domain.RoutingNodeExample;
import dev.mesbase.business.production.domain.RoutingNodeEntity;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.business.production.domain.MesOperationUserEntity;
import dev.mesbase.business.production.domain.MesOperationUserExample;
import dev.mesbase.system.production.OperationUserDirectoryMapper.UserOption;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class OperatorCandidateService {
    private final OperationUserDirectory directory;
    private final MesOperationUserMapper capabilities;
    private final OperatorQualificationRules rules;
    private final MesProductionExecutionMapper executions;
    private final RoutingNodeMapper nodes;

    public OperatorCandidateService(OperationUserDirectory directory, MesOperationUserMapper capabilities,
                                    OperatorQualificationRules rules, MesProductionExecutionMapper executions,
                                    RoutingNodeMapper nodes) {
        this.executions = executions;
        this.nodes = nodes;
        this.directory = directory;
        this.capabilities = capabilities;
        this.rules = rules;
    }

    public record Node(Long id, Long operationId, String code, String name) {
    }

    public record ExecutionNodes(String status, List<Node> nodes) {
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public ExecutionNodes nodes(Long executionId) {
        FactoryContext.required();
        var execution = executions.selectByPrimaryKey(executionId);
        if (execution == null) throw new FoundationException(ErrorCode.EXECUTION_NOT_FOUND);
        if (execution.getRoutingId() == null) return new ExecutionNodes(execution.getStatus(), List.of());
        var filter = new RoutingNodeExample();
        filter.createCriteria().andRoutingIdEqualTo(execution.getRoutingId());
        filter.setOrderByClause("display_order,id");
        try (Page<RoutingNodeEntity> page = PageHelper.offsetPage(0, 100)) {
            var rows = nodes.selectByExample(filter);
            if (page.getTotal() > 100) throw new FoundationException(ErrorCode.BAD_REQUEST);
            return new ExecutionNodes(execution.getStatus(), rows.stream()
                    .map(n -> new Node(n.getId(), n.getOperationId(), n.getNodeCode(), n.getOperationName())).toList());
        }
    }

    /**
     * Cursor advances over capability rows; inactive/revoked identities are excluded from returned candidates.
     */
    public record CandidatePage(List<UserOption> items, int nextOffset, boolean hasMore) {
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public CandidatePage candidates(Long operationId, int offset) {
        if (offset < 0) throw new FoundationException(ErrorCode.BAD_REQUEST);
        rules.operation(operationId);
        var filter = new MesOperationUserExample();
        filter.createCriteria().andOperationIdEqualTo(operationId);
        filter.setOrderByClause("id desc");
        List<MesOperationUserEntity> rows;
        long total;
        try (Page<MesOperationUserEntity> page = PageHelper.offsetPage(offset, 100)) {
            rows = capabilities.selectByExample(filter);
            total = page.getTotal();
        }
        return new CandidatePage(directory.validUsers(rows.stream().map(MesOperationUserEntity::getUserId).toList()),
                offset + rows.size(), offset + rows.size() < total);
    }
}
