package dev.mesbase.server;

import dev.mesbase.infrastructure.persistence.MapperPackages;
import dev.mesbase.infrastructure.persistence.OwnerMapperCatalog;
import dev.mesbase.infrastructure.persistence.PersistenceOwner;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.mybatis.spring.mapper.ClassPathMapperScanner;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.env.Environment;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
import org.springframework.util.ClassUtils;

/** One standard MyBatis-Spring scan per owner; validation consumes the same discovery result. */
final class OwnerMapperRegistration implements BeanDefinitionRegistryPostProcessor {
    private final Environment environment;
    private final Map<PersistenceOwner.Kind, List<String>> roots;

    OwnerMapperRegistration(Environment environment) {
        this(environment, MapperPackages.ROOTS);
    }

    OwnerMapperRegistration(Environment environment, Map<PersistenceOwner.Kind, List<String>> roots) {
        this.environment = environment;
        this.roots = roots;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
        OwnerMapperCatalog catalog = new OwnerMapperCatalog(roots);
        registry.registerBeanDefinition("ownerMapperCatalog", new RootBeanDefinition(OwnerMapperCatalog.class, () -> catalog));
        for (PersistenceOwner.Kind kind : PersistenceOwner.Kind.values()) {
            if (!roots.containsKey(kind)) continue;
            var scanner = new ClassPathMapperScanner(registry, environment) {
                @Override
                protected boolean isCandidateComponent(AnnotatedBeanDefinition definition) {
                    if (!super.isCandidateComponent(definition) || definition.getMetadata().getClassName().contains("$")) {
                        return false;
                    }
                    if (kind == PersistenceOwner.Kind.ERP && !environment.getProperty("mes.erp.enabled", Boolean.class, false)) {
                        throw new IllegalArgumentException("ERP Mappers require enabled ERP persistence");
                    }
                    catalog.register(kind, ClassUtils.resolveClassName(definition.getMetadata().getClassName(),
                        ClassUtils.getDefaultClassLoader()));
                    return true;
                }
            };
            scanner.setSqlSessionFactoryBeanName(switch (kind) {
                case PLATFORM -> "platformSqlSessionFactory";
                case BUSINESS -> "businessSqlSessionFactory";
                case ERP -> "erpSqlSessionFactory";
            });
            scanner.setExcludeFilters(List.of(new RegexPatternTypeFilter(Pattern.compile("^(?!.*Mapper$).*$"))));
            scanner.registerFilters();
            scanner.doScan(roots.get(kind).toArray(String[]::new));
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory factory) {
    }
}
