package dev.mesbase.business.production.domain;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;

public final class ProductionClosureBlockedException extends FoundationException {
    private final String explanation;

    public ProductionClosureBlockedException(String explanation) {
        super(ErrorCode.PRODUCTION_CLOSURE_BLOCKED);
        this.explanation = explanation;
    }

    public String safeExplanation() {
        return explanation;
    }
}
