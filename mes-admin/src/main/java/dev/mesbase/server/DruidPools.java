package dev.mesbase.server;

import dev.mesbase.common.utils.StringUtils;
import com.alibaba.druid.pool.DruidDataSource;
import java.sql.SQLException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;

/**
 * YAML-owned pool tuning. Connections are supplied by explicit Platform/Factory bindings.
 */
public final class DruidPools {
    private DruidPools() {
    }

    public record Settings(int initialSize, int minIdle, int maxActive, long maxWait,
                           int connectTimeout, int socketTimeout, long timeBetweenEvictionRunsMillis,
                           long minEvictableIdleTimeMillis, long maxEvictableIdleTimeMillis,
                           String validationQuery, boolean testWhileIdle, boolean testOnBorrow, boolean testOnReturn,
                           String filters) {
        public Settings {
            if (initialSize < 0 || minIdle < 0 || maxActive < 1 || maxActive > 100 || initialSize > maxActive
                    || minIdle > maxActive || maxWait < 250 || connectTimeout < 1 || socketTimeout < 1
                    || timeBetweenEvictionRunsMillis < 1000 || minEvictableIdleTimeMillis < 0
                    || maxEvictableIdleTimeMillis < minEvictableIdleTimeMillis
                    || StringUtils.isBlank(validationQuery))
                throw new IllegalArgumentException("Invalid Druid pool settings");
        }
    }

    public static Settings settings(Environment environment) {
        return Binder.get(environment).bind("spring.datasource.druid", Bindable.of(Settings.class))
                .orElseThrow(() -> new IllegalArgumentException("Druid YAML settings required"));
    }

    public static DruidDataSource create(Settings s, String name, String url, String user, String password) {
        return initialize(new DruidDataSource(), s, name, url, user, password);
    }

    public static RequestDrivenDataSource createBusiness(Settings s, String name, String url, String user, String password, DatasourceRecovery recovery) {
        return initialize(new RequestDrivenDataSource(recovery), s, name, url, user, password);
    }

    private static <T extends DruidDataSource> T initialize(T pool, Settings s, String name, String url, String user, String password) {
        pool.setName(name);
        pool.setUrl(url);
        pool.setUsername(user);
        pool.setPassword(password);
        pool.setInitialSize(s.initialSize());
        pool.setMinIdle(s.minIdle());
        pool.setMaxActive(s.maxActive());
        pool.setMaxWait(s.maxWait());
        pool.setConnectTimeout(s.connectTimeout());
        pool.setSocketTimeout(s.socketTimeout());
        pool.setTimeBetweenEvictionRunsMillis(s.timeBetweenEvictionRunsMillis());
        pool.setMinEvictableIdleTimeMillis(s.minEvictableIdleTimeMillis());
        pool.setMaxEvictableIdleTimeMillis(s.maxEvictableIdleTimeMillis());
        pool.setValidationQuery(s.validationQuery());
        pool.setTestWhileIdle(s.testWhileIdle());
        pool.setTestOnBorrow(s.testOnBorrow());
        pool.setTestOnReturn(s.testOnReturn());
        if (pool instanceof RequestDrivenDataSource) {
            pool.setInitialSize(0);
            pool.setMinIdle(0);
            pool.setKeepAlive(false);
            pool.setAsyncInit(false);
            pool.setTestWhileIdle(false);
            pool.setTestOnBorrow(true);
            pool.setTestOnReturn(false);
            pool.setBreakAfterAcquireFailure(true);
            pool.setConnectionErrorRetryAttempts(0);
            pool.setFailFast(true);
            pool.setValidationQueryTimeout(Math.max(1, (s.connectTimeout() + 999) / 1000));
        }
        try {
            if (s.filters() == null || !s.filters().matches("(stat|wall)(,(stat|wall))*"))
                throw new IllegalArgumentException("Only registered Druid filters allowed");
            pool.setFilters(s.filters());
            pool.init();
            return pool;
        } catch (SQLException failure) {
            pool.close();
            throw new IllegalStateException("Druid pool initialization failed", failure);
        }
    }
}
