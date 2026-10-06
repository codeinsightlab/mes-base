package dev.mesbase.infrastructure.persistence;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.RegexPatternTypeFilter;

/**
 * Mapper ownership is declared once, on the Mapper itself. No central class list.
 */
public final class OwnerMapperCatalog {
    private final Map<PersistenceOwner.Kind, Set<Class<?>>> groups = new EnumMap<>(PersistenceOwner.Kind.class);

    public OwnerMapperCatalog() { this(MapperPackages.ROOTS); }
    public OwnerMapperCatalog(Map<PersistenceOwner.Kind, List<String>> roots) {
        var all = roots.values().stream().flatMap(List::stream).toList();
        for (int i=0;i<all.size();i++) for (int j=i+1;j<all.size();j++)
            if (MapperPackages.contains(all.get(i),all.get(j)) || MapperPackages.contains(all.get(j),all.get(i)))
                throw new IllegalArgumentException("Persistence package roots overlap");
        for (PersistenceOwner.Kind kind : PersistenceOwner.Kind.values()) groups.put(kind, new HashSet<>());
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition definition) {
                return definition.getMetadata().isIndependent() && definition.getMetadata().isInterface() && !definition.getMetadata().getClassName().contains("$");
            }
        };
        scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(".*Mapper")));
        for (String base : packages)
            for (BeanDefinition definition : scanner.findCandidateComponents(base)) {
                try {
                    Class<?> mapper = Class.forName(definition.getBeanClassName());
                    PersistenceOwner owner = mapper.getAnnotation(PersistenceOwner.class);
                    if (owner == null)
                        throw new IllegalArgumentException("Mapper requires PersistenceOwner: " + mapper.getName());
                    groups.get(owner.value()).add(mapper);
                } catch (ClassNotFoundException failure) {
                    throw new IllegalStateException("Mapper class unavailable", failure);
                }
            }
    }

    public Set<Class<?>> types(PersistenceOwner.Kind kind) {
        return Set.copyOf(groups.get(kind));
    }
}
