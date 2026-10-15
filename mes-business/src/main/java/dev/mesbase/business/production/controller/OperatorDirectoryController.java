package dev.mesbase.business.production.controller;

import dev.mesbase.business.production.service.OperationUserDirectory;
import dev.mesbase.business.production.service.OperatorCandidateService;
import dev.mesbase.platform.web.EndpointAccess;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@RestController
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class OperatorDirectoryController {
    private final OperationUserDirectory users;
    private final OperatorCandidateService candidates;

    public OperatorDirectoryController(OperationUserDirectory users, OperatorCandidateService candidates) {
        this.users = users;
        this.candidates = candidates;
    }

    public record Option(String id, String name) {
    }

    public record UserPage(List<Option> items, long total, int offset, int limit) {
    }

    public record CandidatePage(List<Option> items, int nextOffset, boolean hasMore) {
    }

    public record Node(String id, String operationId, String code, String name) {
    }

    public record ExecutionNodes(String status, List<Node> nodes) {
    }

    @GetMapping("/api/production/executions/{id}/assignment-nodes")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = MesExecutionAssignmentPermissions.LIST)
    public ExecutionNodes nodes(@PathVariable Long id) {
        var result = candidates.nodes(id);
        return new ExecutionNodes(result.status(), result.nodes().stream()
                .map(n -> new Node(n.id().toString(), n.operationId().toString(), n.code(), n.name())).toList());
    }

    @GetMapping("/api/production/operator-users")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = MesOperationUserPermissions.LIST)
    public UserPage users(@RequestParam(required = false) String keyword, @RequestParam(defaultValue = "0") int offset,
                          @RequestParam(defaultValue = "20") int limit) {
        var page = users.page(keyword, offset, limit);
        return new UserPage(page.items().stream().map(u -> new Option(u.id().toString(), u.name()))
                .toList(), page.total(), offset, limit);
    }

    @GetMapping("/api/production/operator-candidates")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = MesExecutionAssignmentPermissions.LIST)
    public CandidatePage candidates(@RequestParam Long operationId, @RequestParam(defaultValue = "0") int offset) {
        var page = candidates.candidates(operationId, offset);
        return new CandidatePage(page.items().stream().map(u -> new Option(u.id().toString(), u.name()))
                .toList(), page.nextOffset(), page.hasMore());
    }
}
