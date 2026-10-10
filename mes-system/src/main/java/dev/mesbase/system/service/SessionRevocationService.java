package dev.mesbase.system.service;

import dev.mesbase.common.core.redis.UserSessionStore;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.system.mapper.SessionAccountMapper;
import dev.mesbase.system.mapper.SessionAccountMapper.Revocation;
import java.util.List;
import java.util.Objects;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * Center commit and Redis are not atomic. The account fence rejects old sessions while delivery retries.
 */
@Service
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public class SessionRevocationService {
    private static final Logger LOG = LoggerFactory.getLogger(SessionRevocationService.class);
    private final SessionAccountMapper accounts;
    private final UserSessionStore sessions;
    private final TransactionTemplate transactions;

    public SessionRevocationService(SessionAccountMapper accounts, UserSessionStore sessions,
                                    @Qualifier("masterTransactionManager") PlatformTransactionManager manager) {
        this.accounts = accounts;
        this.sessions = sessions;
        transactions = new TransactionTemplate(manager);
    }

    public void enqueue(List<Long> ids) {
        if (!TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("Revocation must share the account transaction");
        if (ids == null || ids.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("Revocation IDs must not be null");
        List<Long> unique = new ArrayList<>(new LinkedHashSet<>(ids));
        if (unique.isEmpty()) return;
        List<Revocation> versions = new ArrayList<>();
        // Existing user deletion has no collection limit; bound SQL parameter batches instead.
        for (int start = 0; start < unique.size(); start += 500) {
            List<Long> batch = unique.subList(start, Math.min(start + 500, unique.size()));
            List<Revocation> found = accounts.revisions(batch);
            if (found.size() != batch.size())
                throw new IllegalArgumentException("Revocation account not found");
            versions.addAll(found);
            accounts.enqueue(batch);
        }
        List<Revocation> committed = List.copyOf(versions);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // Do not reuse the just-committed ConnectionHolder for outbox acknowledgement.
                boolean failed = false;
                for (Revocation item : committed) {
                    try {
                        sessions.revoke(item.userId(), item.revision());
                    } catch (RuntimeException failure) {
                        failed = true;
                        LOG.error("Session revocation pending userId={}", item.userId(), failure);
                    }
                }
                if (failed) throw new FoundationException(ErrorCode.SESSION_REVOCATION_PENDING);
            }
        });
    }

    public void enqueueUsername(String username) {
        Long id = accounts.userId(username);
        if (id == null) throw new IllegalArgumentException("Revocation account not found");
        enqueue(List.of(id));
    }

    @Scheduled(fixedDelayString = "${mes.system.session-revocation-retry-ms:5000}")
    public void retryPending() {
        try {
            List<Revocation> pending = transactions.execute(status -> accounts.pending());
            for (Revocation item : pending) {
                try {
                    sessions.revoke(item.userId(), item.revision());
                    transactions.executeWithoutResult(status -> accounts.delivered(item.userId(), item.revision()));
                } catch (RuntimeException failure) {
                    long delaySeconds = Math.min(300, 5L << Math.min(item.attempts(), 6));
                    try {
                        transactions.executeWithoutResult(status -> accounts.failed(item.userId(), item.revision(), delaySeconds));
                    } catch (RuntimeException recordingFailure) {
                        failure.addSuppressed(recordingFailure);
                    }
                    LOG.error("Session revocation retry pending userId={}", item.userId(), failure);
                }
            }
        } catch (RuntimeException failure) {
            LOG.error("Session revocation retry unavailable", failure);
        }
    }
}
