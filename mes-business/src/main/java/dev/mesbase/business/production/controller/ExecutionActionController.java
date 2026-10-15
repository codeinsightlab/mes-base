package dev.mesbase.business.production.controller;

import dev.mesbase.business.production.domain.ExecutionPathStatus;
import dev.mesbase.business.production.service.ExecutionPathService;
import dev.mesbase.business.production.service.ExecutionPathService.PathModel;
import dev.mesbase.business.production.controller.vo.MesProductionExecutionVo;
import dev.mesbase.platform.web.EndpointAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.validation.annotation.Validated;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@RestController
@Validated
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ExecutionActionController {
    private final ExecutionPathService service;

    public ExecutionActionController(ExecutionPathService service) {
        this.service = service;
    }

    public record SelectRequest(@NotNull Integer revision,
                                @NotEmpty @Size(max = 100) List<@NotNull Long> routingNodeIds) {
    }

    public record NodeView(String id, String nodeCode, String operationName, boolean required) {
    }

    public record EdgeView(String fromNodeId, String toNodeId) {
    }

    public record PathView(String executionId, String executionStatus, String routingId, String pathStatus,
                           int revision, LocalDateTime selectedAt, LocalDateTime lockedAt, List<String> selectedNodeIds,
                           List<NodeView> nodes, List<EdgeView> edges) {
    }

    @GetMapping("/api/production/executions/{id}/path")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:mes:production:execution:query")
    public PathView view(@PathVariable @NotNull Long id) {
        return toView(service.view(id));
    }

    @PostMapping("/api/production/executions/{id}/actions/select-path")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:mes:production:execution:update")
    public PathView select(@PathVariable @NotNull Long id, @RequestBody @Valid SelectRequest input) {
        return toView(service.select(id, input.revision(), input.routingNodeIds()));
    }

    @PostMapping("/api/production/executions/{id}/actions/start")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "business:mes:production:execution:update")
    public MesProductionExecutionVo start(@PathVariable @NotNull Long id) {
        return MesProductionExecutionController.toView(service.start(id));
    }

    private static PathView toView(PathModel model) {
        var p = model.path();
        return new PathView(model.executionId().toString(), model.executionStatus(), p == null ? null : p.getRoutingId()
                .toString(), p == null ? ExecutionPathStatus.UNSELECTED.getValue() : p.getPathStatus(), p == null ? 0 : p.getRevision(), p == null ? null : p.getSelectedAt(), p == null ? null : p.getLockedAt(), model.selectedNodeIds()
                .stream().map(Object::toString).toList(), model.nodes().stream().map(n -> new NodeView(n.getId()
                        .toString(), n.getNodeCode(), n.getOperationName(), Boolean.TRUE.equals(n.getIsKey())))
                .toList(), model.edges().stream()
                .map(e -> new EdgeView(e.getFromNodeId().toString(), e.getToNodeId().toString())).toList());
    }
}
