package dev.mesbase.business.legacy.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.mesbase.business.legacy.dao.LegacyMigrationMapper;
import dev.mesbase.business.legacy.domain.LegacyHistory.Mapping;
import dev.mesbase.business.legacy.domain.LegacyHistory.Node;
import dev.mesbase.business.production.dao.MesProductionExecutionMapper;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
@Transactional(transactionManager = "businessTransactionManager", readOnly = true)
public class LegacyHistoryQueryService {
    private final LegacyMigrationMapper metadata;
    private final MesProductionExecutionMapper executions;

    public LegacyHistoryQueryService(LegacyMigrationMapper metadata, MesProductionExecutionMapper executions) {
        this.metadata = metadata;
        this.executions = executions;
    }

    public record Detail(Mapping mapping, BigDecimal historicalInputQty, List<Node> nodes) {
    }

    public record Card(String sourceKey, String executionId, String batchId, boolean migrated,
                       int sourceReports, long importedReports, int sourceRoutingNodes, long importedNodes,
                       boolean sourcePathPartial) {
    }

    public record Order(Long workorderId, String sourceMappingId, List<Card> cards,
                        long unmigratedCards, long sourceIncompleteCards, boolean productionComplete) {
    }

    public Order order(Long id) {
        var mapping = metadata.order(id);
        if (mapping == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        var children = metadata.orderExecutions(mapping.id()).stream()
                .collect(Collectors.toMap(Mapping::sourcePk, v -> v));
        var counts = metadata.cardCounts(mapping.id()).stream().collect(Collectors.toMap(v -> v.sourceKey(), v -> v));
        var json = new ObjectMapper();
        var cards = new ArrayList<Card>();
        try {
            for (var source : json.readTree(mapping.sourcePayload()).path("executions")) {
                String key = json.writeValueAsString(source.path("key"));
                var child = children.get(key);
                var count = counts.get(key);
                int fullNodes = source.path("routing").path("current_node_ids").size();
                int reportedNodes = source.path("reported_by_node").size();
                cards.add(new Card(key, Optional.ofNullable(child).map(nullableValue -> nullableValue.entityId()
                        .toString()).orElse(null), Optional.ofNullable(child)
                        .map(nullableValue -> nullableValue.batchId()).orElse(null),
                        child != null, source.path("reports")
                        .size(), count == null ? 0 : count.reportCount(), fullNodes,
                        count == null ? 0 : count.nodeCount(), reportedNodes < fullNodes));
            }
        } catch (JsonProcessingException failure) {
            throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        }
        return new Order(id, mapping.id(), List.copyOf(cards), cards.stream().filter(c -> !c.migrated()).count(),
                cards.stream().filter(Card::sourcePathPartial).count(), false);
    }

    public Detail execution(Long id) {
        var row = executions.selectByPrimaryKey(id);
        if (row == null) throw new FoundationException(ErrorCode.EXECUTION_NOT_FOUND);
        var mapping = metadata.execution(id);
        if (mapping == null) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
        return new Detail(mapping, metadata.input(id), List.copyOf(metadata.nodes(id)));
    }
}
