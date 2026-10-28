package dev.mesbase.system.service.impl;

import dev.mesbase.common.core.domain.entity.SysAutoCodePart;
import dev.mesbase.common.core.redis.RedisCache;
import dev.mesbase.common.exception.ServiceException;
import dev.mesbase.system.mapper.SysAutoCodePartMapper;
import dev.mesbase.system.mapper.SysAutoCodeRuleMapper;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Shared platform-wide immutable generation configuration snapshot. */
@Component
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public class AutoCodeRuleSnapshotCache {
    private static final Logger LOG = LoggerFactory.getLogger(AutoCodeRuleSnapshotCache.class);
    private static final String KEY = "autocode:{rules:v1}:snapshot";
    private static final String VERSION_KEY = "autocode:{rules:v1}:version";
    private static final int TTL_SECONDS = 3600;
    private final RedisCache redis;
    private final SysAutoCodeRuleMapper rules;
    private final SysAutoCodePartMapper parts;

    public AutoCodeRuleSnapshotCache(RedisCache redis, SysAutoCodeRuleMapper rules, SysAutoCodePartMapper parts) {
        this.redis = redis;
        this.rules = rules;
        this.parts = parts;
    }

    public RuleConfig get(String ruleCode) {
        if (TransactionSynchronizationManager.isActualTransactionActive())
            return loadAll().byCode().get(ruleCode);
        Snapshot snapshot = null;
        String version = null;
        boolean redisAvailable = true;
        try {
            version = redis.getCacheVersion(VERSION_KEY);
            snapshot = redis.getCacheObject(KEY);
        } catch (RuntimeException failure) {
            redisAvailable = false;
            LOG.warn("Autocode shared cache unavailable; loading current rules from database type={}",
                    failure.getClass().getSimpleName());
        }
        if (snapshot == null) {
            if (redisAvailable) {
                for (int attempt = 0; attempt < 3; attempt++) {
                    snapshot = loadAll();
                    try {
                        if (redis.setCacheObjectIfVersion(VERSION_KEY, version, KEY, snapshot, TTL_SECONDS))
                            return snapshot.byCode().get(ruleCode);
                        version = redis.getCacheVersion(VERSION_KEY);
                    } catch (RuntimeException failure) {
                        redisAvailable = false;
                        LOG.warn("Autocode shared cache fill unavailable type={}; using current database rules",
                                failure.getClass().getSimpleName());
                        break;
                    }
                }
                if (redisAvailable)
                    throw new ServiceException("编码规则正在变更，请稍后重试");
            }
            snapshot = loadAll();
        }
        RuleConfig cached = snapshot.byCode().get(ruleCode);
        if (cached != null || !redisAvailable) return cached;
        for (int attempt = 0; attempt < 3; attempt++) {
            Snapshot refreshed = loadAll();
            try {
                if (redis.setCacheObjectIfVersion(VERSION_KEY, version, KEY, refreshed, TTL_SECONDS))
                    return refreshed.byCode().get(ruleCode);
                version = redis.getCacheVersion(VERSION_KEY);
            } catch (RuntimeException failure) {
                LOG.warn("Autocode cache refresh unavailable after rule miss type={}; using current database rules",
                        failure.getClass().getSimpleName());
                return refreshed.byCode().get(ruleCode);
            }
        }
        throw new ServiceException("编码规则正在变更，请稍后重试");
    }

    public void invalidateAfterCommit() {
        Runnable invalidate = () -> {
            try {
                redis.invalidateVersionedObject(VERSION_KEY, KEY, UUID.randomUUID().toString());
            } catch (RuntimeException failure) {
                LOG.error("Autocode cache invalidation failed after commit; generation will validate current rules under lock type={}",
                        failure.getClass().getSimpleName());
            }
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    invalidate.run();
                }
            });
        } else {
            invalidate.run();
        }
    }

    private Snapshot loadAll() {
        var ruleRows = rules.selectAllForGeneration();
        var partRows = parts.selectAllForGeneration();
        Map<Long, List<PartConfig>> partsByRule = new LinkedHashMap<>();
        for (var part : partRows) {
            partsByRule.computeIfAbsent(part.getRuleId(), ignored -> new ArrayList<>()).add(copy(part));
        }
        Map<String, RuleConfig> byCode = new LinkedHashMap<>();
        for (var rule : ruleRows) {
            var config = new RuleConfig(rule.getRuleId(), rule.getRuleCode(), rule.getMaxLength(), rule.getIsPadded(),
                    rule.getPaddedChar(), rule.getPaddedMethod(), rule.getEnableFlag(),
                    List.copyOf(partsByRule.getOrDefault(rule.getRuleId(), List.of())));
            if (byCode.put(rule.getRuleCode(), config) != null)
                throw new ServiceException("规则编码重复，无法加载编码配置");
        }
        return new Snapshot(Map.copyOf(byCode));
    }

    private static PartConfig copy(SysAutoCodePart row) {
        return new PartConfig(row.getPartId(), row.getRuleId(), row.getPartIndex(), row.getPartType(),
                row.getPartLength(), row.getDateFormat(), row.getFixCharacter(), row.getSeriaStartNo(),
                row.getSeriaStep(), row.getCycleFlag(), row.getCycleMethod());
    }

    public record Snapshot(Map<String, RuleConfig> byCode) implements Serializable { }

    public record RuleConfig(long ruleId, String ruleCode, Integer maxLength, String isPadded,
                             String paddedChar, String paddedMethod, String enableFlag,
                             List<PartConfig> parts) implements Serializable { }

    public record PartConfig(long partId, long ruleId, Integer partIndex, String partType,
                             Integer partLength, String dateFormat, String fixCharacter,
                             Integer seriaStartNo, Integer seriaStep, String cycleFlag,
                             String cycleMethod) implements Serializable {
    }
}
