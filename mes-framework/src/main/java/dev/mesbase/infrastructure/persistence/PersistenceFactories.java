package dev.mesbase.infrastructure.persistence;

import com.github.pagehelper.PageInterceptor;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import javax.sql.DataSource;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;

public final class PersistenceFactories {
    private PersistenceFactories() {
    }

    public static SqlSessionFactory create(DataSource source, Set<Class<?>> mappers,
                                           Consumer<Configuration> setup, Runnable verify) {
        try {
            Configuration configuration = new Configuration();
            configuration.setCacheEnabled(false);
            configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
            configuration.addInterceptor(new PersistenceStatementGuard(verify));
            setup.accept(configuration);
            mappers.forEach(configuration::addMapper);
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(source);
            factory.setConfiguration(configuration);
            return factory.getObject();
        } catch (Exception failure) {
            throw new IllegalStateException("Persistence factory initialization failed", failure);
        }
    }

    public static void pagination(Configuration configuration) {
        PageInterceptor page = new PageInterceptor();
        Properties properties = new Properties();
        properties.setProperty("autoRuntimeDialect", "true");
        properties.setProperty("reasonable", "false");
        page.setProperties(properties);
        configuration.addInterceptor(page);
    }
}
