package dev.mesbase.server.integration.e10;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import dev.mesbase.framework.sync.SyncCoordinator;

/**
 * SDK 按工厂中心配置惰性装配；启动、保存配置均不访问 ERP。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "mes.runtime.enabled", havingValue = "true")
public class DigiwinE10Configuration {
    @Bean(destroyMethod = "close")
    DigiwinE10MoQueries digiwinE10MoQueries(FactoryOapiService configurations) {
        return new DigiwinE10MoQueries(configurations);
    }

    @Bean
    DigiwinE10SyncPreview digiwinE10SyncPreview(DigiwinE10MoQueries queries, SyncCoordinator coordinator) {
        return new DigiwinE10SyncPreview(queries, coordinator);
    }
}
