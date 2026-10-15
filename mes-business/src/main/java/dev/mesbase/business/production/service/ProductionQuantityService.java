package dev.mesbase.business.production.service;

import dev.mesbase.business.production.domain.ProductionReportModels.ExecutionSummary;

import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.MesExecutionPathNodeMapper;
import dev.mesbase.business.production.dao.ProductionQuantityMapper;
import dev.mesbase.business.production.domain.MesExecutionPathNodeExample;
import dev.mesbase.business.production.domain.ExecutionPathStatus;
import dev.mesbase.business.production.domain.ProductionReportModels.ExecutionQuantity;
import dev.mesbase.business.production.domain.ProductionReportModels.ExecutionPage;
import dev.mesbase.business.production.domain.ProductionReportModels.NodeQuantity;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import com.github.pagehelper.PageHelper;
import java.math.BigDecimal;
import java.util.List;
import dev.mesbase.business.directory.service.BusinessDirectoryService;
import dev.mesbase.common.cache.ReferenceLabelCache.Label;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
@Transactional(transactionManager = "businessTransactionManager", readOnly = true)
public class ProductionQuantityService {
    private final MesProductionExecutionMapper executions;
    private final ExecutionPathService paths;
    private final MesExecutionPathNodeMapper pathNodes;
    private final ProductionQuantityMapper quantities;
    private final BusinessDirectoryService directory;

    public ProductionQuantityService(MesProductionExecutionMapper executions, ExecutionPathService paths,
                                     MesExecutionPathNodeMapper pathNodes, ProductionQuantityMapper quantities,
                                     BusinessDirectoryService directory) {
        this.executions = executions;
        this.paths = paths;
        this.pathNodes = pathNodes;
        this.quantities = quantities;
        this.directory = directory;
    }

    public ExecutionQuantity getExecutionQuantities(Long executionId) {
        var execution = executions.selectByPrimaryKey(executionId);
        if (execution == null) throw new FoundationException(ErrorCode.EXECUTION_NOT_FOUND);
        var model = paths.view(executionId);
        if (model.path() == null)
            return new ExecutionQuantity(executionId, execution.getWorkorderId(), execution.getExecutionNo(),
                    execution.getRoutingId(), execution.getTargetQty(), ExecutionPathStatus.UNSELECTED.getValue(), false, "执行尚未选择实际路径", List.of(), label(model.routing()));
        if (!Objects.equals(model.path().getRoutingId(), execution.getRoutingId())) unavailable();
        ExecutionPathValidator.validate(model.selectedNodeIds(), model.nodes(), model.edges());
        var f = new MesExecutionPathNodeExample();
        f.createCriteria().andExecutionIdEqualTo(executionId);
        f.setOrderByClause("sequence_no,id");
        var ordered = pathNodes.selectByExample(f);
        if (ordered.size() != model.selectedNodeIds().size()) unavailable();
        for (int i = 0; i < ordered.size(); i++) if (ordered.get(i).getSequenceNo() != i + 1) unavailable();
        var nodeMap = model.nodes().stream().collect(Collectors.toMap(n -> n.getId(), n -> n));
        var totals = quantities.nodeTotals(executionId).stream()
                .collect(Collectors.toMap(t -> t.routingNodeId(), t -> t.reportedQty()));
        var result = new ArrayList<NodeQuantity>();
        BigDecimal input = execution.getTargetQty();
        for (int i = 0; i < ordered.size(); i++) {
            var node = nodeMap.get(ordered.get(i).getRoutingNodeId());
            var reported = totals.getOrDefault(node.getId(), BigDecimal.ZERO);
            requireWithin(input, reported);
            result.add(new NodeQuantity(executionId, node.getId(), node.getOperationId(), node.getNodeCode(), node.getOperationName() != null ? node.getOperationName() : model.operationLabels()
                    .containsKey(node.getOperationId()) ? model.operationLabels().get(node.getOperationId())
                    .name() + "（当前名称）" : null, i + 1,
                    input, reported, BigDecimal.ZERO, reported, input.subtract(reported), false));
            input = reported;
        }
        boolean locked = ExecutionPathStatus.LOCKED.getValue().equals(model.path().getPathStatus());
        return new ExecutionQuantity(executionId, execution.getWorkorderId(), execution.getExecutionNo(), execution.getRoutingId(),
                execution.getTargetQty(), model.path()
                .getPathStatus(), locked, locked ? null : "实际路径尚未锁定，禁止写入报工数量", List.copyOf(result), label(model.routing()));
    }

    private static String label(Label label) {
        return label == null ? null : label.code() + " / V" + label.version() + " · " + label.name();
    }

    public List<NodeQuantity> getNodeQuantities(Long executionId) {
        return getExecutionQuantities(executionId).nodes();
    }

    public NodeQuantity getNodeQuantity(Long executionId, Long nodeId) {
        return getNodeQuantities(executionId).stream().filter(n -> n.routingNodeId().equals(nodeId)).findFirst()
                .orElseThrow(() -> new FoundationException(ErrorCode.REPORT_PATH_REQUIRED));
    }

    public ExecutionPage getWorkorderQuantities(Long workorderId, int offset, int limit) {
        paging(offset, limit);
        List<ExecutionSummary> rows;
        long total;
        try (var page = PageHelper.offsetPage(offset, limit)) {
            rows = List.copyOf(quantities.workorderQuantities(workorderId));
            total = page.getTotal();
        }
        var labels = directory.routingLabels(rows.stream().map(ExecutionSummary::routingId).toList()).entrySet()
                .stream().collect(Collectors.toMap(entry -> entry.getKey(), entry -> label(entry.getValue())));
        return new ExecutionPage(rows, total, offset, limit, labels);
    }

    public static void validateChange(ExecutionQuantity view, Long nodeId, BigDecimal delta) {
        if (!view.writable()) unavailable();
        boolean found = false;
        var input = view.targetQty();
        for (var node : view.nodes()) {
            var next = node.reportedQty();
            if (node.routingNodeId().equals(nodeId)) {
                next = next.add(delta);
                found = true;
            }
            requireWithin(input, next);
            input = next;
        }
        if (!found) unavailable();
    }

    public static void paging(int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > 100) throw new FoundationException(ErrorCode.BAD_REQUEST);
    }

    private static void requireWithin(BigDecimal input, BigDecimal reported) {
        if (reported.signum() < 0 || reported.compareTo(input) > 0)
            throw new FoundationException(ErrorCode.REPORT_QUANTITY_CONFLICT);
    }

    private static void unavailable() {
        throw new FoundationException(ErrorCode.REPORT_PATH_REQUIRED);
    }
}
