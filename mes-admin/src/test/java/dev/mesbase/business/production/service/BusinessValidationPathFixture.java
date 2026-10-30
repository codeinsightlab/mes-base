package dev.mesbase.business.production.service;

import dev.mesbase.common.validation.ParameterChecks;
import dev.mesbase.core.error.ErrorCode;

/** Executable test fixture in the same package family as production business validators. */
public final class BusinessValidationPathFixture {
    private BusinessValidationPathFixture() { }
    public static void service() { validator(); }
    private static void validator() { ParameterChecks.reject(ErrorCode.BAD_REQUEST); }
    public static IllegalStateException cause() { return new IllegalStateException("TEST_ONLY cause"); }
}
