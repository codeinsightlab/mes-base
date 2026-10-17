package dev.mesbase.quartz.util;

import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryCatalog;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.core.factory.FactoryStatus;
import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import dev.mesbase.platform.execution.ExecutionScope;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.platform.execution.FactoryIntegrationExecutor;
import dev.mesbase.quartz.domain.SysJob;
import dev.mesbase.quartz.mapper.JobClaimMapper;
import dev.mesbase.quartz.mapper.SysJobMapper;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

public final class ControlledJobRunner {
    private final FactoryIntegrationExecutor integration;
    private final PlatformPersistence unit;
    private final FactoryCatalog factories;
    private final JobClaimMapper claims;
    private final SysJobMapper jobs;

    public ControlledJobRunner(PlatformPersistence unit, FactoryCatalog factories, JobClaimMapper claims,
                               SysJobMapper jobs) {
        this(unit, factories, claims, jobs, (scope, task) -> {
            throw new FoundationException(ErrorCode.INTEGRATION_NOT_CONFIGURED);
        });
    }

    public ControlledJobRunner(PlatformPersistence unit, FactoryCatalog factories, JobClaimMapper claims,
                               SysJobMapper jobs, FactoryIntegrationExecutor integration) {
        this.integration = integration;
        this.unit = unit;
        this.factories = factories;
        this.claims = claims;
        this.jobs = jobs;
    }

    public void validate(SysJob job) {
        boolean platform = "platform-heartbeat".equals(job.getInvokeTarget()), factory = "factory-heartbeat".equals(job.getInvokeTarget());
        if (!platform && !factory) throw new FoundationException(ErrorCode.UNPROCESSABLE);
        if (job.getJobName() == null || job.getJobName().isBlank() || job.getJobName()
                .length() > 64 || job.getJobGroup() == null || !job.getJobGroup()
                .matches("[A-Za-z0-9_-]{1,64}") || !CronUtils.isValid(job.getCronExpression()) || !Set.of("0", "1", "2", "3")
                .contains(job.getMisfirePolicy()) || !Set.of("0", "1").contains(job.getConcurrent()))
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        if (platform) {
            if (!"PLATFORM".equals(job.getExecutionScope()) || job.getFactoryId() != null && !job.getFactoryId()
                    .isBlank()) throw new FoundationException(ErrorCode.BAD_REQUEST);
            job.setFactoryId(null);
        } else {
            if (!"FACTORY".equals(job.getExecutionScope()) || job.getFactoryId() == null)
                throw new FoundationException(ErrorCode.FACTORY_SCOPE_REQUIRED);
            var f = factories.find(new FactoryId(job.getFactoryId()))
                    .orElseThrow(() -> new FoundationException(ErrorCode.FACTORY_NOT_FOUND));
            if (f.status() != FactoryStatus.ENABLED) throw new FoundationException(ErrorCode.FACTORY_DISABLED);
        }
    }

    public void run(Long id) {
        run(id, "SCHEDULER", UUID.randomUUID().toString(), false);
    }

    public void run(Long id, String actor, String requestId) {
        run(id, actor, requestId, true);
    }

    private void run(Long id, String actor, String requestId, boolean manual) {
        String token = UUID.randomUUID().toString();
        var job = unit.run(() -> {
            if (claims.claim(id, token, manual) != 1) {
                if (jobs.selectJobById(id) == null) throw new FoundationException(ErrorCode.NOT_FOUND);
                throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            }
            var current = jobs.selectJobById(id);
            validate(current);
            return current;
        });
        Date started = new Date();
        long clock = System.nanoTime();
        RuntimeException failure = null;
        String outcome = "SUCCEEDED";
        try {
            var scope = "PLATFORM".equals(job.getExecutionScope()) ? ExecutionScope.platform() : ExecutionScope.factory(new FactoryScope(new FactoryId(job.getFactoryId())));
            scope.run(() -> {
                if ("FACTORY".equals(job.getExecutionScope())) integration.run(FactoryContext.required(), () -> {
                });
                else if (FactoryContext.current().isPresent()) throw new FoundationException(ErrorCode.CONFLICT);
            });
        } catch (RuntimeException e) {
            failure = e;
            outcome = "UNKNOWN";
        }
        final String result = outcome;
        try {
            unit.run(() -> {
                if (claims.release(id, token) != 1) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
                claims.log(job.getJobName(), job.getJobGroup(), job.getInvokeTarget(), result, "SUCCEEDED".equals(result) ? "0" : "1", requestId, actor, job.getFactoryId(), job.getExecutionScope(), token, started, new Date(), Math.max(0, (System.nanoTime() - clock) / 1_000_000L));
                return null;
            });
        } catch (RuntimeException log) {
            if (failure != null) log.addSuppressed(failure);
            throw log;
        }
        if (failure != null) throw failure;
    }

    public void recover(Long id, String token, boolean confirmedDead, String actor, String requestId) {
        if (!confirmedDead || token == null || !token.matches("[a-f0-9-]{36}"))
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        unit.run(() -> {
            var job = jobs.selectJobById(id);
            if (job == null) throw new FoundationException(ErrorCode.NOT_FOUND);
            if (claims.recover(id, token) != 1) throw new FoundationException(ErrorCode.RESOURCE_CONFLICT);
            claims.log(job.getJobName(), job.getJobGroup(), job.getInvokeTarget(), "RECOVERED_UNKNOWN", "1", requestId, actor, job.getFactoryId(), job.getExecutionScope(), token, null, new Date(), 0);
            return null;
        });
    }

    public JobClaimMapper.RunState state(Long id) {
        var state = claims.state(id);
        if (state == null) throw new FoundationException(ErrorCode.NOT_FOUND);
        return state;
    }
}
