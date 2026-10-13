package dev.mesbase.business.production.sync;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;

public record ExternalRouting(
        @Size(max = 128) String externalId,
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 1000) String description,
        @Size(max = 128) String sourceVersion,
        @NotNull @Size(min = 1, max = 100) List<@NotBlank @Size(max = 64) String> productCodes,
        @Size(min = 1, max = 100) List<@NotNull @Valid ExternalRoutingStep> steps,
        @Size(min = 1, max = 100) List<@NotNull @Valid ExternalRoutingNode> nodes,
        @Size(max = 400) List<@NotNull @Valid ExternalRoutingEdge> edges
) {
    public ExternalRouting(String externalId, String code, String name, String description,
                           String sourceVersion, List<String> productCodes, List<ExternalRoutingStep> steps) {
        this(externalId, code, name, description, sourceVersion, productCodes, steps, null, null);
    }

    public record Graph(List<ExternalRoutingNode> nodes, List<ExternalRoutingEdge> edges) {
    }

    public Graph graph() {
        if (steps == null) {
            if (nodes == null || edges == null) throw new FoundationException(ErrorCode.ROUTING_GRAPH_INVALID);
            return new Graph(nodes, edges);
        }
        if (nodes != null || edges != null) throw new FoundationException(ErrorCode.ROUTING_GRAPH_INVALID);
        var ordered = steps.stream().sorted(Comparator.comparing(ExternalRoutingStep::sequence)).toList();
        var unique = new HashSet<Integer>();
        var converted = new ArrayList<ExternalRoutingNode>();
        var connections = new ArrayList<ExternalRoutingEdge>();
        for (var step : ordered) {
            if (!unique.add(step.sequence())) throw new FoundationException(ErrorCode.ROUTING_GRAPH_INVALID);
            String code = "N" + step.sequence();
            if (!converted.isEmpty()) connections.add(new ExternalRoutingEdge(converted.getLast().nodeCode(), code));
            converted.add(new ExternalRoutingNode(code, step.operationCode(), step.isKey(),
                    step.description(), step.sequence()));
        }
        return new Graph(converted, connections);
    }
}
