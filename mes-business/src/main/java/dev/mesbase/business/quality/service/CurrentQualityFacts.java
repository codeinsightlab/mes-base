package dev.mesbase.business.quality.service;

import dev.mesbase.business.quality.dao.ProductionDefectListExtraMapper;
import dev.mesbase.business.quality.domain.DefectRecordAction;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.CurrentFact;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.Declaration;
import dev.mesbase.business.quality.domain.QualityTreatmentModels.FactBuilder;

import java.util.*;
import java.util.function.Consumer;

/**
 * Bounded raw-fact reader. All interpretation belongs to CurrentFact/FactBuilder.
 */
public final class CurrentQualityFacts {
    private static final int CHUNK = 250;
    private final ProductionDefectListExtraMapper mapper;

    public CurrentQualityFacts(ProductionDefectListExtraMapper mapper) {
        this.mapper = mapper;
    }

    public List<CurrentFact> read(List<String> executions, String declaration, List<Long> ids) {
        var result = new ArrayList<CurrentFact>();
        scan(executions, declaration, ids, result::addAll);
        return List.copyOf(result);
    }

    public void scan(List<String> executions, String declaration, List<Long> ids,
                     Consumer<List<CurrentFact>> consumer) {
        long after = -1;
        while (true) {
            var declarations = mapper.selectDeclarations(executions, declaration, ids, null, after, CHUNK);
            if (declarations.isEmpty()) return;
            var facts = interpret(declarations);
            if (executions != null) facts = facts.stream()
                    .filter(f -> !DefectRecordAction.VOID.getValue().equals(f.recordAction())).toList();
            consumer.accept(facts);
            after = declarations.getLast().id();
            if (declarations.size() < CHUNK) return;
        }
    }

    public List<CurrentFact> interpret(List<Declaration> declarations) {
        if (declarations.isEmpty()) return List.of();
        var builders = builders(declarations);
        var relatedIds = builders.values().stream().map(FactBuilder::relatedDeclarationId)
                .filter(Objects::nonNull).distinct().toList();
        var related = new LinkedHashMap<String, CurrentFact>();
        if (!relatedIds.isEmpty()) {
            long after = -1;
            while (true) {
                var chunk = mapper.selectDeclarations(null, null, null, relatedIds, after, CHUNK);
                if (chunk.isEmpty()) break;
                builders(chunk).forEach((id, builder) -> related.put(id, builder.build(Map.of())));
                after = chunk.getLast().id();
                if (chunk.size() < CHUNK) break;
            }
        }
        return builders.values().stream().map(builder -> builder.build(related)).toList();
    }

    private Map<String, FactBuilder> builders(List<Declaration> declarations) {
        var builders = new LinkedHashMap<String, FactBuilder>();
        for (var declaration : declarations) builders.put(declaration.declarationId(), new FactBuilder(declaration));
        var ids = declarations.stream().map(Declaration::id).toList();
        String after = null;
        while (true) {
            var events = mapper.selectEvents(ids, after, 500);
            for (var event : events) builders.get(event.ownerDeclarationId()).accept(event);
            if (events.size() < 500) break;
            after = events.getLast().id();
        }
        return builders;
    }
}
