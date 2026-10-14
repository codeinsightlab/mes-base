package dev.mesbase.business.integration.warehouse;

import dev.mesbase.business.issue.domain.MesMaterialIssueEntity;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import dev.mesbase.business.integration.warehouse.ErpActionResult.Mode;

@Component
public class WarehouseIntegration {
    private final LocalWarehouseErpService local = new LocalWarehouseErpService();
    private final Environment environment;
    private final List<WarehouseErpAdapter> adapters;

    public WarehouseIntegration(Environment environment, List<WarehouseErpAdapter> adapters) {
        this.environment = environment;
        this.adapters = List.copyOf(adapters);
    }

    public Mode configuredMode(FactoryScope scope) {
        String value = environment.getProperty("mes.integration.warehouse.factories." + scope.factoryId()
                .value() + ".mode");
        if (value == null) return null;
        try {
            return Mode.valueOf(value);
        } catch (IllegalArgumentException failure) {
            throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED, failure);
        }
    }

    public Mode cancellationMode(FactoryScope scope, MesMaterialIssueEntity row) {
        Mode current = configuredMode(scope);
        if (current == null) throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
        if ("UNVERIFIED".equals(row.getExternalEffect()) || row.getSourceExecutionMode() == null)
            throw new FoundationException(ErrorCode.ISSUE_EXTERNAL_UNVERIFIED);
        if (current == Mode.LOCAL_ONLY && "LOCAL_ONLY".equals(row.getSourceExecutionMode()) && "NONE".equals(row.getExternalEffect()))
            return Mode.LOCAL_ONLY;
        if (!"CONNECTED".equals(row.getSourceExecutionMode()) || row.getExternalBindingKey() == null || row.getExternalReference() == null)
            throw new FoundationException(ErrorCode.ISSUE_EXTERNAL_UNVERIFIED);
        adapter(scope, row.getExternalBindingKey());
        return Mode.CONNECTED;
    }

    public ErpActionResult localCreate(FactoryScope scope, MaterialIssueCreateCommand command) {
        if (configuredMode(scope) != Mode.LOCAL_ONLY)
            throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
        return local.createMaterialIssue(scope, command);
    }

    public ErpIssueQueryResult query(FactoryScope scope, String sourceMode, String provider,
                                     QueryMaterialIssueCommand command) {
        Mode mode = configuredMode(scope);
        if (mode == null) throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
        if ("LOCAL_ONLY".equals(sourceMode) && "local".equals(provider)) {
            if (command.externalId() != null || command.externalCode() != null || command.bindingKey() != null)
                throw new FoundationException(ErrorCode.ISSUE_EXTERNAL_UNVERIFIED);
            if (mode != Mode.LOCAL_ONLY) throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
            return local.queryMaterialIssue(scope, command);
        }
        if (!"CONNECTED".equals(sourceMode) || provider == null || command.bindingKey() == null)
            throw new FoundationException(ErrorCode.ISSUE_EXTERNAL_UNVERIFIED);
        if (mode != Mode.CONNECTED) throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
        // V1 requires the historical provider to match the current delivery configuration.
        String configured = environment.getProperty("mes.integration.warehouse.factories." + scope.factoryId()
                .value() + ".provider");
        if (!provider.equals(configured)) throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
        return adapter(scope, command.bindingKey()).queryMaterialIssue(scope, command);
    }

    private WarehouseErpAdapter adapter(FactoryScope scope, String bindingKey) {
        var matching = adapters.stream().filter(a -> a.supports(scope, bindingKey)).toList();
        if (matching.size() != 1) throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
        return matching.getFirst();
    }

    public ErpActionResult execute(FactoryScope scope, MesMaterialIssueEntity row, boolean query) {
        if ("LOCAL_ONLY".equals(row.getVoidExecutionMode())) {
            if (cancellationMode(scope, row) != Mode.LOCAL_ONLY)
                throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
            return local.cancelMaterialIssue(scope, new MaterialIssueCancelCommand(row.getId(), row.getVoidOperationId(), "local", null));
        }
        var command = new MaterialIssueCancelCommand(row.getId(), row.getVoidOperationId(), row.getExternalBindingKey(), row.getExternalReference());
        var adapter = adapter(scope, row.getExternalBindingKey());
        return query ? adapter.queryMaterialIssueCancellation(scope, command) : adapter.cancelMaterialIssue(scope, command);
    }
}
