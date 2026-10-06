package dev.mesbase.server;

import dev.mesbase.infrastructure.persistence.OwnerMapperCatalog;
import dev.mesbase.infrastructure.persistence.MapperPackages;
import dev.mesbase.infrastructure.persistence.PersistenceOwner;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.env.Environment;

/** Standard MyBatis-Spring package scanners, one per SessionFactory. */
final class OwnerMapperRegistration implements BeanDefinitionRegistryPostProcessor {
    private final Environment environment;
    private final Map<PersistenceOwner.Kind, List<String>> roots;
    OwnerMapperRegistration(Environment environment) { this(environment, MapperPackages.ROOTS); }
    OwnerMapperRegistration(Environment environment, Map<PersistenceOwner.Kind,List<String>> roots) { this.environment = environment; this.roots = roots; }
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        OwnerMapperCatalog catalog = new OwnerMapperCatalog(roots);
        registry.registerBeanDefinition("ownerMapperCatalog", new RootBeanDefinition(OwnerMapperCatalog.class, () -> catalog));
        for (PersistenceOwner.Kind kind : PersistenceOwner.Kind.values()) {
            String factory = switch (kind) {
                case PLATFORM -> "platformSqlSessionFactory";
                case BUSINESS -> "businessSqlSessionFactory";
                case ERP -> "erpSqlSessionFactory";
            };
            if (kind == PersistenceOwner.Kind.ERP && !environment.getProperty("mes.erp.enabled", Boolean.class, false)) {
                if (!catalog.types(kind).isEmpty()) throw new IllegalArgumentException("ERP Mappers require enabled ERP persistence");
                continue;
            }
            if (!roots.containsKey(kind)) continue;
            RootBeanDefinition scanner = new RootBeanDefinition(MapperScannerConfigurer.class);
            scanner.getPropertyValues().add("basePackage", String.join(",", roots.get(kind)));
            scanner.getPropertyValues().add("sqlSessionFactoryBeanName", factory);
            scanner.getPropertyValues().add("excludeFilters", List.of(new RegexPatternTypeFilter(Pattern.compile("^(?!.*Mapper$).*$"))));
            registry.registerBeanDefinition(kind.name().toLowerCase() + "MapperScanner", scanner);
        }
    }
    public void postProcessBeanFactory(ConfigurableListableBeanFactory factory) {}
}
