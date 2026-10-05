package dev.mesbase.web.controller.monitor;
import org.springframework.web.bind.annotation.*;import dev.mesbase.platform.web.*;import dev.mesbase.common.core.domain.AjaxResult;
@RestController @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="mes.system.enabled",havingValue="true")
public class DruidController {
 private final com.alibaba.druid.pool.DruidDataSource source;public DruidController(javax.sql.DataSource source){this.source=(com.alibaba.druid.pool.DruidDataSource)source;}
 public record Pool(String name,int activeCount,int poolingCount,long connectCount,long closeCount,long createCount,long destroyCount,long errorCount,long waitCount,long queryCount){}
 @GetMapping("/api/monitor/druid") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="monitor:druid:list",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public AjaxResult stats(){return AjaxResult.success(new Pool(source.getName(),source.getActiveCount(),source.getPoolingCount(),source.getConnectCount(),source.getCloseCount(),source.getCreateCount(),source.getDestroyCount(),source.getErrorCount(),source.getNotEmptyWaitCount(),source.getExecuteCount()));}
 public record Sql(String id,String sql,String executeCount,String errorCount,String totalMillis,String maxMillis,String updateCount,String fetchRowCount){}
 @GetMapping("/api/monitor/druid/sql") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="monitor:druid:list",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public AjaxResult sql(@RequestParam(defaultValue="100")int limit){if(limit<1||limit>100)throw new dev.mesbase.core.error.FoundationException(dev.mesbase.core.error.ErrorCode.BAD_REQUEST);return AjaxResult.success(source.getSqlStatMap().values().stream().sorted(java.util.Comparator.comparingLong(com.alibaba.druid.stat.JdbcSqlStat::getExecuteCount).reversed()).limit(limit).map(s->new Sql(String.valueOf(s.getId()),template(s.getSql()),String.valueOf(s.getExecuteCount()),String.valueOf(s.getErrorCount()),String.valueOf(s.getExecuteMillisTotal()),String.valueOf(s.getExecuteMillisMax()),String.valueOf(s.getUpdateCount()),String.valueOf(s.getFetchRowCount()))).toList());}
 private String template(String sql){try{return com.alibaba.druid.sql.visitor.ParameterizedOutputVisitorUtils.parameterize(sql,com.alibaba.druid.DbType.mysql);}catch(RuntimeException unsupported){return "无法显示此SQL模板";}}
 public record Wall(boolean enabled,String checkCount,String violationCount,String whiteListHitCount,String blackListHitCount){}
 @GetMapping("/api/monitor/druid/wall") @EndpointAccess(value=EndpointAccess.Kind.AUTHORIZED,permission="monitor:druid:list",permissionScope=EndpointAccess.PermissionScope.PLATFORM)
 public AjaxResult wall(){for(var filter:source.getProxyFilters())if(filter instanceof com.alibaba.druid.wall.WallFilter wall){var provider=wall.getProvider();return AjaxResult.success(new Wall(true,String.valueOf(provider.getCheckCount()),String.valueOf(provider.getViolationCount()),String.valueOf(provider.getWhiteListHitCount()),String.valueOf(provider.getBlackListHitCount())));}return AjaxResult.success(new Wall(false,null,null,null,null));}
}
