package dev.mesbase.server;

import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactorySelectionService;
import dev.mesbase.core.security.AuthenticatedActor;
import dev.mesbase.platform.execution.FactoryContext;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public final class FoundationService {
    public record FactoryView(String factoryId,String code,String name,String status) {}
    public record ScopeView(String factoryId) {}
    public record AccessView(String factoryId,String permission) {}
    private final FactorySelectionService selection;
    public FoundationService(FactorySelectionService selection) { this.selection=selection; }
    public List<FactoryView> options(int offset,int limit) { return selection.options(offset,limit).stream().map(f -> new FactoryView(f.id().value(),f.code(),f.name(),f.status().name())).toList(); }
    public ScopeView context() { return new ScopeView(FactoryContext.current().map(scope -> scope.factoryId().value()).orElse(null)); }
    public ScopeView remember(AuthenticatedActor actor,String factoryId) { return new ScopeView(selection.remember(actor,new FactoryId(factoryId)).factoryId().value()); }
    public AccessView access() { return new AccessView(FactoryContext.required().factoryId().value(),"foundation:access:read"); }
}
