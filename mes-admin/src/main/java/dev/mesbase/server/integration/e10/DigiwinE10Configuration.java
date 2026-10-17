package dev.mesbase.server.integration.e10;

import com.lixin.capability.digiwin.e10.E10Host;
import com.lixin.capability.digiwin.e10.E10OapiClient;
import com.lixin.capability.digiwin.e10.E10Service;
import dev.mesbase.server.integration.e10.DigiwinE10MoQueries.Binding;
import dev.mesbase.server.managed.FactoryIntegrationRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * MES 自有配置与生命周期装配，SDK 没有配置前缀或自动装配能力。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "mes.integration.digiwin-e10.enabled", havingValue = "true")
public class DigiwinE10Configuration {
    private static final String PREFIX = "mes.integration.digiwin-e10.connections";

    public record Host(String ver, String prod, String timezone, String ip, String id, String lang, String acct) {
        @Override
        public String toString() {
            return "E10HostSettings[REDACTED]";
        }
    }

    public record Service(String prod, String ip, String id) {
        @Override
        public String toString() {
            return "E10ServiceSettings[REDACTED]";
        }
    }

    public record Settings(String endpoint, Host host, Service service) {
        @Override
        public String toString() {
            return "E10Settings[REDACTED]";
        }
    }

    @Bean(destroyMethod = "close")
    DigiwinE10MoQueries digiwinE10MoQueries(Environment environment, FactoryIntegrationRegistry registry) {
        Map<String, Settings> settings = Binder.get(environment).bind(PREFIX,
                Bindable.mapOf(String.class, Settings.class)).orElse(Map.of());
        if (settings.isEmpty()) throw missing("connections");
        Map<String, Binding> bindings = new LinkedHashMap<>();
        try {
            for (var entry : settings.entrySet()) {
                var connection = registry.requireConnection(entry.getKey());
                if (!DigiwinE10MoQueries.PROVIDER.equals(connection.provider()))
                    throw new IllegalStateException("E10 connection must use the DIGIWIN_E10 provider");
                var value = entry.getValue();
                String endpoint = required("endpoint", value.endpoint());
                if (!Objects.equals(connection.serviceUrl(), endpoint))
                    throw new IllegalStateException("E10 endpoint must match its registered connection service-url");
                Host host = value.host();
                Service service = value.service();
                if (host == null) throw missing("host");
                if (service == null) throw missing("service");
                if (host.id() == null || !host.id().isEmpty() && host.id().isBlank()) throw missing("host.id");
                var client = E10OapiClient.builder().endpoint(endpoint)
                        .host(new E10Host(required("host.ver", host.ver()), required("host.prod", host.prod()),
                                required("host.timezone", host.timezone()), required("host.ip", host.ip()),
                                host.id(), required("host.lang", host.lang()), required("host.acct", host.acct())))
                        .service(new E10Service(required("service.prod", service.prod()),
                                required("service.ip", service.ip()), required("service.id", service.id())))
                        .build();
                bindings.put(entry.getKey(), new Binding(connection.factoryId(), connection.revision(), client));
            }
            return new DigiwinE10MoQueries(bindings);
        } catch (RuntimeException failure) {
            bindings.values().forEach(binding -> binding.client().close());
            throw failure;
        }
    }

    private static String required(String name, String value) {
        if (value == null || value.isBlank()) throw missing(name);
        return value;
    }

    private static IllegalStateException missing(String name) {
        return new IllegalStateException("Missing required MES E10 setting: " + name);
    }
}
