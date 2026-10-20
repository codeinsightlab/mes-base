package dev.mesbase.business.production.service;

import dev.mesbase.business.production.domain.ProductionExecutionStatus;
import dev.mesbase.business.quality.service.ExecutionQualityReferenceQuery;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;
import dev.mesbase.business.issue.domain.MaterialIssueStatus;
import dev.mesbase.business.integration.warehouse.ExternalEffect;
import dev.mesbase.business.production.domain.ExecutionPathStatus;
import dev.mesbase.business.integration.warehouse.ErpIssueQueryResult.DocumentStatus;
import dev.mesbase.business.production.dao.MesExecutionPathMapper;
import dev.mesbase.business.production.dao.MesExecutionPathNodeMapper;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.business.production.dao.RoutingNodeMapper;
import dev.mesbase.business.directory.domain.DirectoryLabel;
import dev.mesbase.business.directory.service.BusinessDirectoryService;
import dev.mesbase.business.production.dao.RoutingEdgeMapper;
import dev.mesbase.business.production.domain.MesExecutionPathEntity;
import dev.mesbase.business.production.domain.MesExecutionPathNodeEntity;
import dev.mesbase.business.production.domain.MesExecutionPathNodeExample;
import dev.mesbase.business.production.domain.MesProductionExecutionEntity;
import dev.mesbase.business.production.domain.RoutingNodeEntity;
import dev.mesbase.business.production.domain.RoutingNodeExample;
import dev.mesbase.business.production.domain.RoutingEdgeEntity;
import dev.mesbase.business.production.domain.RoutingEdgeExample;
import dev.mesbase.business.issue.dao.MesMaterialIssueMapper;
import dev.mesbase.business.issue.dao.MesMaterialIssueErpExtraMapper;
import dev.mesbase.business.issue.domain.MesMaterialIssueErpExtraExample;
import java.util.stream.Collectors;
import dev.mesbase.business.issue.domain.MesMaterialIssueExample;
import dev.mesbase.business.issue.domain.MesMaterialIssueEntity;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.infrastructure.cache.annotation.BusinessCacheEvict;
import dev.mesbase.business.production.domain.MesProductionExecutionResource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import dev.mesbase.business.production.service.ProductionChainLocks.LockedExecution;

@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
@Transactional(transactionManager = "businessTransactionManager", isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
public class ExecutionPathService {
    private final ExecutionQualityReferenceQuery quality;
    private final ProductionChainLocks locks;
    private final ProductionExecutionRules rules;
    private final MesExecutionPathMapper paths;
    private final MesExecutionPathNodeMapper selected;
    private final MesProductionExecutionMapper executions;
    private final RoutingNodeMapper nodes;
    private final BusinessDirectoryService references;
    private final RoutingEdgeMapper edges;
    private final MesMaterialIssueMapper issues;
    private final MesMaterialIssueErpExtraMapper extras;

    public ExecutionPathService(ProductionChainLocks locks, ProductionExecutionRules rules,
                                MesExecutionPathMapper paths,
                                MesExecutionPathNodeMapper selected, MesProductionExecutionMapper executions,
                                RoutingNodeMapper nodes, RoutingEdgeMapper edges, MesMaterialIssueMapper issues,
                                MesMaterialIssueErpExtraMapper extras, BusinessDirectoryService references,
                                ExecutionQualityReferenceQuery quality) {
        this.quality = quality;
        this.locks = locks;
        this.rules = rules;
        this.paths = paths;
        this.selected = selected;
        this.executions = executions;
        this.nodes = nodes;
        this.references = references;
        this.edges = edges;
        this.issues = issues;
        this.extras = extras;
    }

    public record PathModel(Long executionId, String executionStatus, MesExecutionPathEntity path,
                            List<Long> selectedNodeIds, List<RoutingNodeEntity> nodes, List<RoutingEdgeEntity> edges,
                            String executionNo, DirectoryLabel routing, Map<Long, DirectoryLabel> operationLabels) {
    }

    @Transactional(transactionManager = "businessTransactionManager", readOnly = true)
    public PathModel view(Long id) {
        var e = executions.selectByPrimaryKey(id);
        if (e == null) throw new FoundationException(ErrorCode.EXECUTION_NOT_FOUND);
        return view(e);
    }

    private PathModel view(MesProductionExecutionEntity e) {
        return view(e, null);
    }

    private PathModel view(MesProductionExecutionEntity e, List<MesExecutionPathNodeEntity> ordered) {
        Long id = e.getId();
        var routing = references.routingLabel(e.getRoutingId());
        var actualNodes = nodes(e.getRoutingId());
        var operationLabels = references.operationLabels(actualNodes.stream().map(RoutingNodeEntity::getOperationId)
                .toList());
        if (routing == null) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        return new PathModel(id, e.getStatus(), paths.selectByPrimaryKey(id),
                ordered == null ? nodeIds(id) : ordered.stream().map(MesExecutionPathNodeEntity::getRoutingNodeId)
                        .toList(),
                actualNodes, edges(e.getRoutingId()), e.getExecutionNo(), routing, operationLabels);
    }

    /**
     * Only for the report validation chain while its WorkOrder/Execution locks remain held.
     * Published graph versions are immutable; a new version has a different routing primary key.
     */
    public static final class LockedPath {
        private final LockedExecution owner;
        private final PathModel model;
        private final List<MesExecutionPathNodeEntity> ordered;

        private LockedPath(LockedExecution owner, PathModel model, List<MesExecutionPathNodeEntity> ordered) {
            this.owner = owner;
            this.model = model;
            this.ordered = List.copyOf(ordered);
        }

        private void requireOwner(LockedExecution expected) {
            owner.verify();
            if (owner != expected) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }

        public PathModel model() {
            owner.verify();
            return model;
        }

        public List<MesExecutionPathNodeEntity> orderedRows() {
            owner.verify();
            return ordered;
        }
    }

    public LockedPath lockedPath(LockedExecution locked) {
        var e = locked.execution();
        var ordered = selectedRows(e.getId());
        var model = view(e, ordered);
        if (model.path() == null || !Objects.equals(model.path().getRoutingId(), e.getRoutingId()))
            throw new FoundationException(ErrorCode.REPORT_PATH_REQUIRED);
        ExecutionPathValidator.validate(model.selectedNodeIds(), model.nodes(), model.edges());
        for (int i = 0; i < ordered.size(); i++)
            if (ordered.get(i).getSequenceNo() != i + 1)
                throw new FoundationException(ErrorCode.REPORT_PATH_REQUIRED);
        if (!ExecutionPathStatus.LOCKED.getValue().equals(model.path().getPathStatus()))
            throw new FoundationException(ErrorCode.REPORT_PATH_REQUIRED);
        return new LockedPath(locked, model, ordered);
    }

    public PathModel select(Long id, Integer revision, List<Long> nodeIds) {
        var e = locks.execution(id);
        locks.requireActiveExecution(id);
        if (!ProductionExecutionStatus.READY.getValue().equals(e.getStatus()) || e.getStartTime() != null) fail();
        rules.checkReadyReferences(e);
        var old = paths.selectByPrimaryKey(id);
        if (revision == null || !Objects.equals(revision, old == null ? 0 : old.getRevision())) fail();
        if (old != null && ExecutionPathStatus.LOCKED.getValue().equals(old.getPathStatus()))
            fail();
        if (quality.hasAnyHistory(id))
            throw new FoundationException(ErrorCode.EXECUTION_QUALITY_REFERENCED);
        if (hasBlockingIssue(id)) fail();
        ExecutionPathValidator.validate(nodeIds, nodes(e.getRoutingId()), edges(e.getRoutingId()));
        var path = new MesExecutionPathEntity();
        path.setId(id);
        path.setRoutingId(e.getRoutingId());
        path.setPathStatus(ExecutionPathStatus.SELECTED.getValue());
        path.setRevision(revision + 1);
        path.setSelectedAt(LocalDateTime.now());
        changed(old == null ? paths.insert(path) : paths.updateByPrimaryKey(path));
        var f = new MesExecutionPathNodeExample();
        f.createCriteria().andExecutionIdEqualTo(id);
        selected.deleteByExample(f);
        // At most 100 nodes, with execution locked; generated single-table writes each verify row count.
        for (int i = 0; i < nodeIds.size(); i++) {
            var n = new MesExecutionPathNodeEntity();
            n.setExecutionId(id);
            n.setSequenceNo(i + 1);
            n.setRoutingNodeId(nodeIds.get(i));
            changed(selected.insert(n));
        }
        return view(id);
    }

    public MesExecutionPathEntity requireSelected(MesProductionExecutionEntity e) {
        var p = paths.selectByPrimaryKey(e.getId());
        if (p == null || !Objects.equals(p.getRoutingId(), e.getRoutingId()) || !Set.of(ExecutionPathStatus.SELECTED.getValue(), ExecutionPathStatus.LOCKED.getValue())
                .contains(p.getPathStatus())) fail();
        ExecutionPathValidator.validate(nodeIds(e.getId()), nodes(e.getRoutingId()), edges(e.getRoutingId()));
        return p;
    }

    public void confirm(MesProductionExecutionEntity e, Integer revision) {
        var p = requireSelected(e);
        if (!Objects.equals(p.getRevision(), revision)) fail();
        if (ExecutionPathStatus.LOCKED.getValue().equals(p.getPathStatus())) return;
        p.setPathStatus(ExecutionPathStatus.LOCKED.getValue());
        p.setLockedAt(LocalDateTime.now());
        changed(paths.updateByPrimaryKey(p));
    }

    @BusinessCacheEvict(business = MesProductionExecutionResource.PREFIX, scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public MesProductionExecutionEntity start(Long id) {
        return start(id, null, null);
    }

    @BusinessCacheEvict(business = MesProductionExecutionResource.PREFIX, scope = BusinessCacheEvict.Scope.LIST_AND_DETAIL)
    public MesProductionExecutionEntity start(LockedExecution locked, LockedPath path) {
        try {
            path.requireOwner(locked);
            return start(locked.execution().getId(), locked, path);
        } finally {
            locked.invalidate();
        }
    }

    private MesProductionExecutionEntity start(Long id, LockedExecution locked, LockedPath path) {
        var e = locked == null ? locks.execution(id) : locked.execution();
        if (locked == null) locks.requireActiveExecution(id);
        else locks.requireActiveExecution(id, locked);
        if (!Set.of(ProductionExecutionStatus.READY.getValue(), ProductionExecutionStatus.RUNNING.getValue())
                .contains(e.getStatus())) fail();
        if (ProductionExecutionStatus.READY.getValue().equals(e.getStatus())) rules.checkReadyReferences(e);
        if (path != null) path.requireOwner(locked);
        var selectedPath = path == null ? requireSelected(e) : path.model().path();
        if (!ExecutionPathStatus.LOCKED.getValue().equals(selectedPath.getPathStatus())) fail();
        if (ProductionExecutionStatus.RUNNING.getValue().equals(e.getStatus())) return e;
        var rows = issueRows(id);
        if (rows.stream()
                .noneMatch(i -> MaterialIssueStatus.CONFIRMED.getValue().equals(i.getStatus())) || rows.stream()
                .anyMatch(i -> Set.of(MaterialIssueStatus.VOID_PENDING.getValue(), MaterialIssueStatus.VOID_UNKNOWN.getValue())
                        .contains(i.getStatus()))) fail();
        var confirmed = rows.stream().filter(i -> MaterialIssueStatus.CONFIRMED.getValue().equals(i.getStatus()))
                .toList();
        var filter = new MesMaterialIssueErpExtraExample();
        filter.createCriteria().andIdIn(confirmed.stream().map(MesMaterialIssueEntity::getId).toList());
        var facts = extras.selectByExample(filter).stream().collect(Collectors.toMap(x -> x.getId(), x -> x));
        for (var issue : confirmed) {
            var fact = facts.get(issue.getId());
            if (issue.getSubmissionId() == null || fact == null || !(Mode.LOCAL_ONLY.getValue()
                    .equals(fact.getSourceExecutionMode()) && ExternalEffect.NONE.getValue()
                    .equals(fact.getExternalEffect()) || Mode.CONNECTED.getValue()
                    .equals(fact.getSourceExecutionMode()) && ExternalEffect.CONFIRMED.getValue()
                    .equals(fact.getExternalEffect()) && DocumentStatus.EFFECTIVE.getValue()
                    .equals(fact.getDocumentStatus())))
                fail();
        }
        if (ProductionExecutionStatus.RUNNING.getValue().equals(e.getStatus())) return e;
        e.setStatus(ProductionExecutionStatus.RUNNING.getValue());
        e.setStartTime(LocalDateTime.now());
        e.setUpdateTime(LocalDateTime.now());
        e.setUpdateBy(String.valueOf(SecurityUtils.getUserId()));
        changed(executions.updateByPrimaryKey(e));
        // Return the persisted DATETIME precision, so repeated start has the same canonical response.
        return executions.selectByPrimaryKey(id);
    }

    public boolean hasBlockingIssue(Long id) {
        return issueRows(id).stream()
                .anyMatch(i -> i.getSubmissionId() != null || Set.of(MaterialIssueStatus.VOID_PENDING.getValue(), MaterialIssueStatus.VOID_UNKNOWN.getValue())
                        .contains(i.getStatus()));
    }

    private List<MesMaterialIssueEntity> issueRows(Long id) {
        var f = new MesMaterialIssueExample();
        f.createCriteria().andExecutionIdEqualTo(id);
        if (issues.countByExample(f) > 1000) fail();
        return issues.selectByExample(f);
    }

    private List<Long> nodeIds(Long id) {
        return selectedRows(id).stream().map(MesExecutionPathNodeEntity::getRoutingNodeId).toList();
    }

    private List<MesExecutionPathNodeEntity> selectedRows(Long id) {
        var f = new MesExecutionPathNodeExample();
        f.createCriteria().andExecutionIdEqualTo(id);
        f.setOrderByClause("sequence_no,id");
        return selected.selectByExample(f);
    }

    private List<RoutingNodeEntity> nodes(Long id) {
        var f = new RoutingNodeExample();
        f.createCriteria().andRoutingIdEqualTo(id);
        f.setOrderByClause("display_order,id");
        return nodes.selectByExample(f);
    }

    private List<RoutingEdgeEntity> edges(Long id) {
        var f = new RoutingEdgeExample();
        f.createCriteria().andRoutingIdEqualTo(id);
        return edges.selectByExample(f);
    }

    private static void changed(int n) {
        if (n != 1) fail();
    }

    private static void fail() {
        throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
    }
}
