package dev.mesbase.web.controller.monitor;

import com.alibaba.druid.DbType;
import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.stat.JdbcSqlStat;
import com.alibaba.druid.sql.visitor.ParameterizedOutputVisitorUtils;
import com.alibaba.druid.wall.WallFilter;
import dev.mesbase.common.core.domain.AjaxResult;
import dev.mesbase.core.error.ErrorCode;
import dev.mesbase.core.error.FoundationException;
import dev.mesbase.platform.web.EndpointAccess;
import dev.mesbase.server.monitor.DruidMonitorService;
import dev.mesbase.server.monitor.DruidMonitorService.SourcePage;
import dev.mesbase.server.monitor.DruidMonitorService.Snapshot;
import java.util.Comparator;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public class DruidController {
    private final DruidDataSource source;
    private final DruidMonitorService monitor;

    public DruidController(@Qualifier("masterDataSource") DataSource source, DruidMonitorService monitor) {
        this.source = (DruidDataSource) source;
        this.monitor = monitor;
    }

    @GetMapping("/api/monitor/druid/sources")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "monitor:druid:list", permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    public SourcePage sources(@RequestParam(defaultValue = "1") int pageNum,
                              @RequestParam(defaultValue = "20") int pageSize) {
        return monitor.sources(pageNum, pageSize);
    }

    @GetMapping("/api/monitor/druid/snapshot")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "monitor:druid:list", permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    public Snapshot snapshot(@RequestParam String sourceId, @RequestParam(defaultValue = "100") int limit) {
        return monitor.snapshot(sourceId, limit);
    }

    public record Pool(String name, int activeCount, int poolingCount, long connectCount, long closeCount,
                       long createCount, long destroyCount, long errorCount, long waitCount, long queryCount) {
    }

    @GetMapping("/api/monitor/druid")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "monitor:druid:list", permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    public AjaxResult stats() {
        return AjaxResult.success(new Pool(source.getName(), source.getActiveCount(), source.getPoolingCount(), source.getConnectCount(), source.getCloseCount(), source.getCreateCount(), source.getDestroyCount(), source.getErrorCount(), source.getNotEmptyWaitCount(), source.getExecuteCount()));
    }

    public record Sql(String id, String sql, String executeCount, String errorCount, String totalMillis,
                      String maxMillis, String updateCount, String fetchRowCount) {
    }

    @GetMapping("/api/monitor/druid/sql")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "monitor:druid:list", permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    public AjaxResult sql(@RequestParam(defaultValue = "100") int limit) {
        if (limit < 1 || limit > 100)
            throw new FoundationException(ErrorCode.BAD_REQUEST);
        return AjaxResult.success(source.getSqlStatMap().values().stream()
                .sorted(Comparator.comparingLong(JdbcSqlStat::getExecuteCount).reversed()).limit(limit)
                .map(s -> new Sql(String.valueOf(s.getId()), template(s.getSql()), String.valueOf(s.getExecuteCount()), String.valueOf(s.getErrorCount()), String.valueOf(s.getExecuteMillisTotal()), String.valueOf(s.getExecuteMillisMax()), String.valueOf(s.getUpdateCount()), String.valueOf(s.getFetchRowCount())))
                .toList());
    }

    private String template(String sql) {
        try {
            return ParameterizedOutputVisitorUtils.parameterize(sql, DbType.of(source.getDbType()));
        } catch (RuntimeException unsupported) {
            return "无法显示此SQL模板";
        }
    }

    public record Wall(boolean enabled, String checkCount, String violationCount, String whiteListHitCount,
                       String blackListHitCount) {
    }

    @GetMapping("/api/monitor/druid/wall")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHORIZED, permission = "monitor:druid:list", permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    public AjaxResult wall() {
        for (var filter : source.getProxyFilters())
            if (filter instanceof WallFilter wall) {
                var provider = wall.getProvider();
                return AjaxResult.success(new Wall(true, String.valueOf(provider.getCheckCount()), String.valueOf(provider.getViolationCount()), String.valueOf(provider.getWhiteListHitCount()), String.valueOf(provider.getBlackListHitCount())));
            }
        return AjaxResult.success(new Wall(false, null, null, null, null));
    }
}
