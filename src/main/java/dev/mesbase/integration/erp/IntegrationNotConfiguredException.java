package dev.mesbase.integration.erp;
import dev.mesbase.core.error.*;
public final class IntegrationNotConfiguredException extends FoundationException {
    public IntegrationNotConfiguredException() { super(ErrorCode.INTEGRATION_NOT_CONFIGURED); }
}
