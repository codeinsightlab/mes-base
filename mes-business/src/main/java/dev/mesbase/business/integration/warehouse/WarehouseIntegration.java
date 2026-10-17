package dev.mesbase.business.integration.warehouse;

import dev.mesbase.business.integration.warehouse.ExternalEffect;
import dev.mesbase.business.issue.domain.MesMaterialIssueEntity;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.core.factory.FactoryRuntimeMode;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.platform.execution.FactoryIntegrationContext;
import dev.mesbase.platform.execution.FactoryIntegrationSnapshot;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;

@Component
public class WarehouseIntegration {
    private final LocalWarehouseErpService local = new LocalWarehouseErpService();
    private final List<WarehouseErpAdapter> adapters;

    public WarehouseIntegration(List<WarehouseErpAdapter> adapters) {
        this.adapters = List.copyOf(adapters);
    }

    private FactoryIntegrationSnapshot snapshot(FactoryScope scope) {
        if (FactoryContext.current().isPresent() && !FactoryContext.required().equals(scope))
            throw new FoundationException(ErrorCode.CONFLICT);
        var snapshot = FactoryIntegrationContext.required();
        if (!snapshot.factoryId().equals(scope.factoryId()))
            throw new FoundationException(ErrorCode.CONFLICT);
        return snapshot;
    }

    public Mode configuredMode(FactoryScope scope) {
        return snapshot(scope).mode() == FactoryRuntimeMode.LOCAL ? Mode.LOCAL_ONLY : Mode.CONNECTED;
    }

    public Mode cancellationMode(FactoryScope scope, MesMaterialIssueEntity row) {
        // Cancellation follows frozen source evidence, independent of current factory authority.
        if (ExternalEffect.UNVERIFIED.getValue()
                .equals(row.getExternalEffect()) || row.getSourceExecutionMode() == null)
            throw new FoundationException(ErrorCode.ISSUE_EXTERNAL_UNVERIFIED);
        if (Mode.LOCAL_ONLY.getValue()
                .equals(row.getSourceExecutionMode()) && ExternalEffect.NONE.getValue()
                .equals(row.getExternalEffect()) && row.getExternalBindingKey() == null && row.getExternalReference() == null)
            return Mode.LOCAL_ONLY;
        if (!Mode.CONNECTED.getValue()
                .equals(row.getSourceExecutionMode()) || row.getExternalBindingKey() == null || row.getExternalReference() == null)
            throw new FoundationException(ErrorCode.ISSUE_EXTERNAL_UNVERIFIED);
        var original = row.getSourceContext();
        if (original != null && (!Objects.equals(original.provider(), row.getSourceProvider())
                || !Objects.equals(original.bindingKey(), row.getExternalBindingKey())))
            throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
        if (row.getSourceProvider() == null || !adapter(scope, row.getExternalBindingKey()).supportsProvider(row.getSourceProvider()))
            throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
        return Mode.CONNECTED;
    }

    public record Selection(Mode mode, String provider, String bindingKey) {
    }

    public Selection submissionSelection(FactoryScope scope) {
        var mode = configuredMode(scope);
        if (mode == Mode.LOCAL_ONLY) return new Selection(mode, "local", null);
        var snapshot = snapshot(scope);
        String provider = snapshot.erpType();
        String binding = snapshot.configurationRef();
        if (provider == null || provider.isBlank() || binding == null || binding.isBlank())
            throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
        if (!adapter(scope, binding).supportsProvider(provider))
            throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
        return new Selection(mode, provider, binding);
    }

    public ErpActionResult createMaterialIssue(FactoryScope scope, Selection selection,
                                               MaterialIssueSubmitCommand command) {
        // The submission has already frozen this selection; configuration edits cannot reinterpret it.
        if (selection == null || selection.mode() == null)
            throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
        if (selection.mode() == Mode.LOCAL_ONLY) {
            if (!"local".equals(selection.provider()) || selection.bindingKey() != null)
                throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
            return local.createMaterialIssue(scope, command);
        }
        if (selection.bindingKey() == null || selection.provider() == null)
            throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
        var adapter = adapter(scope, selection.bindingKey());
        if (!adapter.supportsProvider(selection.provider()))
            throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
        if (command.lines().stream()
                .anyMatch(line -> line.externalWarehouseId() == null || line.externalWarehouseId().isBlank()))
            throw new FoundationException(ErrorCode.BUSINESS_SOURCE_CONFLICT);
        return adapter.createMaterialIssue(scope, command);
    }

    public ErpIssueQueryResult query(FactoryScope scope, String sourceMode, String provider,
                                     QueryMaterialIssueCommand command) {
        if (Mode.LOCAL_ONLY.getValue().equals(sourceMode) && "local".equals(provider)) {
            if (command.externalId() != null || command.externalCode() != null || command.bindingKey() != null)
                throw new FoundationException(ErrorCode.ISSUE_EXTERNAL_UNVERIFIED);
            return local.queryMaterialIssue(scope, command);
        }
        if (!Mode.CONNECTED.getValue().equals(sourceMode) || provider == null || command.bindingKey() == null)
            throw new FoundationException(ErrorCode.ISSUE_EXTERNAL_UNVERIFIED);
        var adapter = adapter(scope, command.bindingKey());
        if (!adapter.supportsProvider(provider)) throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
        return adapter.queryMaterialIssue(scope, command);
    }

    private WarehouseErpAdapter adapter(FactoryScope scope, String bindingKey) {
        var matching = adapters.stream().filter(a -> a.supports(scope, bindingKey)).toList();
        if (matching.size() != 1) throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
        return matching.getFirst();
    }

    public ErpActionResult execute(FactoryScope scope, MesMaterialIssueEntity row, boolean query) {
        if (Mode.LOCAL_ONLY.getValue().equals(row.getVoidExecutionMode())) {
            if (cancellationMode(scope, row) != Mode.LOCAL_ONLY)
                throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
            return local.cancelMaterialIssue(scope, new MaterialIssueCancelCommand(row.getId(), row.getVoidOperationId(), "local", null));
        }
        if (cancellationMode(scope, row) != Mode.CONNECTED)
            throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
        var command = new MaterialIssueCancelCommand(row.getId(), row.getVoidOperationId(), row.getExternalBindingKey(), row.getExternalReference(), row.getSourceContext());
        var adapter = adapter(scope, row.getExternalBindingKey());
        return query ? adapter.queryMaterialIssueCancellation(scope, command) : adapter.cancelMaterialIssue(scope, command);
    }
}
