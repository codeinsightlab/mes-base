package dev.mesbase.business.production.domain;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;

/**
 * Only server-generated node labels and gate explanations are exposed.
 */
public final class ProductionQualityPendingException extends FoundationException {
    private final String explanation;

    public ProductionQualityPendingException(String explanation) {
        super(ErrorCode.QUALITY_ATTRIBUTION_REQUIRED);
        this.explanation = explanation;
    }

    public String safeExplanation() {
        return explanation;
    }
}
