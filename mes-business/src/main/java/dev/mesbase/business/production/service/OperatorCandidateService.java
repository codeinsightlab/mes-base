package dev.mesbase.business.production.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import dev.mesbase.business.production.dao.OperatorQualificationExtraMapper;
import java.util.ArrayList;
import java.util.Comparator;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.RoutingNodeMapper;
import dev.mesbase.business.production.domain.RoutingNodeExample;
import dev.mesbase.business.production.domain.RoutingNodeEntity;
import dev.mesbase.platform.execution.FactoryContext;
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
    private final OperatorQualificationExtraMapper capabilities;
    private final OperatorQualificationRules rules;
    private final MesProductionExecutionMapper executions;
    private final RoutingNodeMapper nodes;

    public OperatorCandidateService(OperationUserDirectory directory, OperatorQualificationExtraMapper capabilities,
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
    public ExecutionNodes nodes(String executionCode) {
        FactoryContext.required();
        var execution = executions.selectByCode(executionCode);
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

    public record CandidatePage(List<UserOption> items, int nextOffset, boolean hasMore, long total) {
    }

    /**
     * Exact cross-database pagination: scan the deduplicated business identities in batches of 100,
     * validate each batch against the center, then apply the public offset to valid users only.
     * Full scanning is necessary for an exact valid-user total; memory stays bounded to one batch + page.
     */
    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public CandidatePage candidates(Long operationId, int offset) {
        if (offset < 0) throw new FoundationException(ErrorCode.BAD_REQUEST);
        rules.operation(operationId);
        var items = new ArrayList<UserOption>();
        long total = 0;
        Long before = null;
        while (true) {
            var ids = capabilities.candidateUserIds(operationId, before);
            if (ids.isEmpty()) break;
            var valid = directory.validUsers(ids).stream()
                    .sorted(Comparator.comparing(UserOption::id).reversed()).toList();
            for (var user : valid) {
                if (total >= offset && items.size() < 100) items.add(user);
                total++;
            }
            before = ids.getLast();
            if (ids.size() < 100) break;
        }
        int next = offset + items.size();
        return new CandidatePage(List.copyOf(items), next, next < total, total);
    }
}
