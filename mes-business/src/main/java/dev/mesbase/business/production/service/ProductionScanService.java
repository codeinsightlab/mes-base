package dev.mesbase.business.production.service;

import dev.mesbase.business.production.dao.MesProductionScanActivityMapper;
import dev.mesbase.business.production.domain.MesProductionScanActivityEntity;
import dev.mesbase.business.production.domain.ProductionIdentity;
import dev.mesbase.business.production.domain.ProductionScanInputMethod;
import dev.mesbase.business.production.domain.ProductionScanOutcome;
import dev.mesbase.business.production.domain.ProductionScanType;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.common.validation.ParameterChecks;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.security.AuthenticatedActor;
import dev.mesbase.core.security.AuthorizationService;
import dev.mesbase.core.security.Permission;
import dev.mesbase.platform.execution.FactoryContext;
import java.time.LocalDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Resolves the same production identity for every scene; activity is never a production fact.
 */
@Service
@ConditionalOnProperty(name = "mes.business.enabled", havingValue = "true")
public class ProductionScanService {
    private static final Logger LOG = LoggerFactory.getLogger(ProductionScanService.class);
    private final ProductionIdentityService identities;
    private final AuthorizationService authorization;
    private final MesProductionScanActivityMapper activities;
    private final TransactionTemplate auditTransaction;

    public ProductionScanService(ProductionIdentityService identities, AuthorizationService authorization,
                                 MesProductionScanActivityMapper activities,
                                 @Qualifier("businessTransactionManager") PlatformTransactionManager manager) {
        this.identities = identities;
        this.authorization = authorization;
        this.activities = activities;
        this.auditTransaction = new TransactionTemplate(manager);
        auditTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional(transactionManager = "businessTransactionManager", propagation = Propagation.NOT_SUPPORTED)
    public ProductionIdentity scan(ProductionScanType type, String code, ProductionScanInputMethod method,
                                   String requestId) {
        if (type == null || method == null) ParameterChecks.reject(ErrorCode.BAD_REQUEST);
        ParameterChecks.text(code, true, 2048, ErrorCode.BAD_REQUEST);
        var scope = FactoryContext.required();
        var user = SecurityUtils.getUserId();
        var occurredAt = LocalDateTime.now();
        long started = System.nanoTime();
        ProductionIdentity identity;
        try {
            if (!authorization.hasPermission(new AuthenticatedActor(SecurityUtils.getAuthentication().getName()),
                    scope, new Permission(type.getPermission()))) throw new FoundationException(ErrorCode.FORBIDDEN);
            identity = identities.resolve(code);
        } catch (RuntimeException | Error failure) {
            ErrorCode error = ErrorCode.INTERNAL_ERROR;
            if (failure instanceof FoundationException rejected) error = rejected.code();
            record(type, method, user, null, ProductionScanOutcome.FAILURE, error, occurredAt, started, requestId);
            throw failure;
        }
        record(type, method, user, identity.executionId(), ProductionScanOutcome.SUCCESS, null,
                occurredAt, started, requestId);
        return identity;
    }

    private void record(ProductionScanType type, ProductionScanInputMethod method, Long user, Long execution,
                        ProductionScanOutcome outcome, ErrorCode error, LocalDateTime occurredAt,
                        long started, String requestId) {
        var event = new MesProductionScanActivityEntity();
        event.setId(UUID.randomUUID().toString());
        event.setScanType(type.getValue());
        event.setInputMethod(method.getValue());
        event.setUserId(user);
        event.setExecutionId(execution);
        event.setOutcome(outcome.getValue());
        if (error != null) event.setErrorCode(error.name());
        event.setDurationMs(Math.max(0L, (System.nanoTime() - started) / 1_000_000));
        event.setOccurredAt(occurredAt);
        if (requestId != null && requestId.matches("[A-Za-z0-9_-][A-Za-z0-9._-]{0,127}")) event.setRequestId(requestId);
        try {
            auditTransaction.executeWithoutResult(status -> {
                if (activities.insert(event) != 1) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            });
        } catch (RuntimeException failure) {
            // Activity is best effort: keep the resolver result or its original exception unchanged.
            LOG.error("event=production.scan.activity.failed activityId={} scanType={} outcome={} requestId={}",
                    event.getId(), type.getValue(), outcome.getValue(), event.getRequestId(), failure);
        }
    }
}
