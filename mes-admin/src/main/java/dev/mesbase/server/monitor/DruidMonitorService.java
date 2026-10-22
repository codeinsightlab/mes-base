package dev.mesbase.server.monitor;

import java.util.Optional;

import com.alibaba.druid.DbType;
import com.alibaba.druid.filter.stat.StatFilter;
import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.sql.visitor.ParameterizedOutputVisitorUtils;
import com.alibaba.druid.stat.JdbcSqlStat;
import com.alibaba.druid.wall.WallFilter;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.infrastructure.persistence.BusinessRuntimeBinding;
import dev.mesbase.infrastructure.persistence.DatasourceIdentity;
import dev.mesbase.infrastructure.persistence.RuntimeRegistry;
import dev.mesbase.server.managed.ManagedDatasourceService;
import dev.mesbase.server.managed.ManagedDatasourceService.DatasourceView;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Read-only, node-local observations. No pool creation, credentials or SQL parameters.
 */
@Service
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public final class DruidMonitorService {
    public record Source(String id, String name, String kind, String factoryId, String factoryName,
                         String state, String databaseType, String poolName) {
    }

    public record SourcePage(List<Source> rows, long total, String nodeId) {
    }

    public record Pool(String name, int initialSize, int minIdle, int maxActive, int activeCount, int poolingCount,
                       int activePeak, int poolingPeak, int waitingThreads, int waitingPeak, String maxWaitMillis,
                       String waitCount, String waitMillis, String connectCount, String closeCount, String createCount,
                       String destroyCount, String connectErrorCount, String errorCount, String queryCount,
                       String transactionCount, String commitCount, String rollbackCount,
                       boolean sqlStatisticsEnabled) {
    }

    public record Sql(String id, String sql, String executeCount, String errorCount, String totalMillis,
                      String maxMillis,
                      String updateCount, String fetchRowCount, String runningCount, String concurrentMax,
                      String lastExecuteAt, String lastErrorAt) {
    }

    public record Wall(boolean enabled, String checkCount, String violationCount, String whiteListHitCount,
                       String blackListHitCount) {
    }

    public record Snapshot(Source source, String nodeId, String capturedAt, Pool pool, List<Sql> sql, Wall wall) {
    }

    private record Entry(Source source, DruidDataSource pool) {
    }

    private final DruidDataSource platform;
    private final ManagedDatasourceService managed;
    private final RuntimeRegistry registry;
    private final String nodeId = "node-" + UUID.randomUUID();

    @Autowired
    public DruidMonitorService(@Qualifier("masterDataSource") DataSource platform,
                               ObjectProvider<ManagedDatasourceService> managed,
                               ObjectProvider<RuntimeRegistry> registry) {
        this(platform, managed.getIfAvailable(), registry.getIfAvailable());
    }

    public DruidMonitorService(DataSource platform, ManagedDatasourceService managed, RuntimeRegistry registry) {
        this.platform = druid(platform);
        this.managed = managed;
        this.registry = registry;
    }

    private static DruidDataSource druid(DataSource source) {
        if (!(source instanceof DruidDataSource pool)) throw new IllegalArgumentException("Druid pool required");
        return pool;
    }

    private static String state(DruidDataSource pool) {
        return pool.isClosed() ? "CLOSED" : pool.isEnable() ? "INITIALIZED" : "DISABLED";
    }

    private static Entry poolEntry(String id, String label, String kind, DruidDataSource pool) {
        return new Entry(new Source(id, label, kind, null, null, state(pool), pool.getDbType(), pool.getName()), pool);
    }

    private Entry business(DatasourceView row) {
        DruidDataSource pool = null;
        if (row.initialized() && registry != null && row.factoryId() != null) {
            var binding = new BusinessRuntimeBinding(new FactoryId(row.factoryId()), new DatasourceIdentity("managed-" + row.datasourceId()), row.revision());
            pool = registry.observedDatasource(binding).map(DruidMonitorService::druid).orElse(null);
        }
        String status = pool != null ? state(pool) : !"ENABLED".equals(row.status()) ? "DISABLED" : row.factoryId() == null ? "UNBOUND" : "NOT_INITIALIZED";
        return new Entry(new Source("business:" + row.datasourceId(), row.datasourceName(), "BUSINESS", row.factoryId(), row.factoryName(), status,
                pool == null ? "mysql" : pool.getDbType(), Optional.ofNullable(pool).map(nullableValue -> nullableValue.getName()).orElse(null)), pool);
    }

    public SourcePage sources(int pageNum, int pageSize) {
        if (pageNum < 1 || pageSize < 1 || pageSize > 100) throw new FoundationException(ErrorCode.BAD_REQUEST);
        long offset = ((long) pageNum - 1) * pageSize;
        var fixed = new ArrayList<Source>();
        fixed.add(poolEntry("platform", "中心数据库", "PLATFORM", platform).source());
        long businessTotal = managed == null ? 0 : managed.countSources(), total = fixed.size() + businessTotal;
        var rows = new ArrayList<Source>();
        if (offset < fixed.size())
            rows.addAll(fixed.subList((int) offset, Math.min(fixed.size(), (int) offset + pageSize)));
        long businessOffset = Math.max(0, offset - fixed.size());
        if (managed != null && rows.size() < pageSize && businessOffset < businessTotal) {
            if (businessOffset > Integer.MAX_VALUE) throw new FoundationException(ErrorCode.BAD_REQUEST);
            managed.list((int) businessOffset, pageSize - rows.size()).forEach(row -> rows.add(business(row).source()));
        }
        return new SourcePage(List.copyOf(rows), total, nodeId);
    }

    private Entry selected(String id) {
        if ("platform".equals(id)) return poolEntry("platform", "中心数据库", "PLATFORM", platform);
        if (managed != null && id != null && id.matches("business:[1-9][0-9]{0,18}")) {
            try {
                return business(managed.detail(Long.parseLong(id.substring(9))));
            } catch (NumberFormatException invalid) {
                throw new FoundationException(ErrorCode.NOT_FOUND, invalid);
            }
        }
        throw new FoundationException(ErrorCode.NOT_FOUND);
    }

    public Snapshot snapshot(String id, int limit) {
        if (limit < 1 || limit > 100) throw new FoundationException(ErrorCode.BAD_REQUEST);
        var entry = selected(id);
        if (entry.pool() == null || !"INITIALIZED".equals(entry.source().state()))
            return new Snapshot(entry.source(), nodeId, Instant.now().toString(), null, List.of(), null);
        var source = entry.pool();
        return new Snapshot(entry.source(), nodeId, Instant.now()
                .toString(), pool(source), queries(source, limit), wall(source));
    }

    private static String count(long value) {
        return Long.toString(value);
    }

    private static Pool pool(DruidDataSource s) {
        return new Pool(s.getName(), s.getInitialSize(), s.getMinIdle(), s.getMaxActive(), s.getActiveCount(), s.getPoolingCount(),
                s.getActivePeak(), s.getPoolingPeak(), s.getNotEmptyWaitThreadCount(), s.getNotEmptyWaitThreadPeak(), count(s.getMaxWait()),
                count(s.getNotEmptyWaitCount()), count(s.getNotEmptyWaitMillis()), count(s.getConnectCount()), count(s.getCloseCount()),
                count(s.getCreateCount()), count(s.getDestroyCount()), count(s.getConnectErrorCount()), count(s.getErrorCount()), count(s.getExecuteCount()),
                count(s.getStartTransactionCount()), count(s.getCommitCount()), count(s.getRollbackCount()), s.getProxyFilters()
                .stream().anyMatch(StatFilter.class::isInstance));
    }

    private static String time(Date date) {
        if (date == null) return null;
        return date.toInstant().toString();
    }

    private static List<Sql> queries(DruidDataSource s, int limit) {
        if (s.getProxyFilters().stream().noneMatch(StatFilter.class::isInstance)) return List.of();
        return s.getSqlStatMap().values().stream().filter(stat -> !stat.isRemoved())
                .sorted(Comparator.comparingLong(JdbcSqlStat::getExecuteMillisTotal).reversed())
                .limit(limit)
                .map(stat -> new Sql(count(stat.getId()), template(stat.getSql(), s.getDbType()), count(stat.getExecuteCount()), count(stat.getErrorCount()),
                        count(stat.getExecuteMillisTotal()), count(stat.getExecuteMillisMax()), count(stat.getUpdateCount()), count(stat.getFetchRowCount()),
                        count(stat.getRunningCount()), count(stat.getConcurrentMax()), time(stat.getExecuteLastStartTime()), time(stat.getExecuteErrorLastTime())))
                .toList();
    }

    private static String template(String sql, String dialect) {
        try {
            return ParameterizedOutputVisitorUtils.parameterize(sql, DbType.of(dialect));
        } catch (RuntimeException unsupported) {
            return "无法显示此SQL模板";
        }
    }

    private static Wall wall(DruidDataSource s) {
        for (var filter : s.getProxyFilters())
            if (filter instanceof WallFilter wall) {
                var p = wall.getProvider();
                return new Wall(true, count(p.getCheckCount()), count(p.getViolationCount()), count(p.getWhiteListHitCount()), count(p.getBlackListHitCount()));
            }
        return new Wall(false, null, null, null, null);
    }
}
