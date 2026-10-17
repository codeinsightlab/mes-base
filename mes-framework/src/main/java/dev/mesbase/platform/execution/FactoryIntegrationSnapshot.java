package dev.mesbase.platform.execution;

import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryRuntimeMode;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;

/** Immutable, credential-free configuration admitted for one request or explicit task. */
public record FactoryIntegrationSnapshot(FactoryId factoryId, FactoryRuntimeMode mode, String erpType,
                                         String configurationRef, long revision, Long configurationRevision) {
    public FactoryIntegrationSnapshot {
        if (factoryId == null || mode == null || revision < 1
                || mode == FactoryRuntimeMode.LOCAL && (erpType != null || configurationRef != null || configurationRevision != null)
                || mode == FactoryRuntimeMode.ERP && (erpType == null || erpType.isBlank()
                || configurationRef == null || configurationRef.isBlank() || configurationRevision == null || configurationRevision < 1))
            throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
    }
}
