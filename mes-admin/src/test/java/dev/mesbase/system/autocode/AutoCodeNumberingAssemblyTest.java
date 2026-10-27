package dev.mesbase.system.autocode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import dev.mesbase.core.factory.FactoryId;
import dev.mesbase.core.factory.FactoryScope;
import dev.mesbase.platform.execution.AutoCodeFactoryContextAdapter;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.system.mapper.AutoCodeExtraMapper;
import dev.mesbase.system.mapper.SysAutoCodePartMapper;
import dev.mesbase.system.mapper.SysAutoCodeRuleMapper;
import dev.mesbase.system.mapper.SysAutoCodeResultMapper;
import dev.mesbase.system.service.AutoCodeFactoryContext;
import dev.mesbase.system.service.AutoCodeNumbering;
import dev.mesbase.system.service.impl.AutoCodeRuleSnapshotCache;
import dev.mesbase.system.service.impl.SysAutoCodeGenerationServiceImpl;
import dev.mesbase.system.strategy.PartTypeFixCharHandler;
import dev.mesbase.system.strategy.PartTypeHandler;
import dev.mesbase.system.strategy.PartTypeInputCharHandler;
import dev.mesbase.system.strategy.PartTypeNowDateHandler;
import dev.mesbase.system.strategy.PartTypeSerialNoHandler;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

class AutoCodeNumberingAssemblyTest {
    @Test
    void generationServiceIsExposedThroughThePublicNumberingContract() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test",
                    Map.of("mes.system.enabled", "true")));
            context.registerBean(AutoCodeRuleSnapshotCache.class, () -> mock(AutoCodeRuleSnapshotCache.class));
            context.registerBean(SysAutoCodeRuleMapper.class, () -> mock(SysAutoCodeRuleMapper.class));
            context.registerBean(SysAutoCodePartMapper.class, () -> mock(SysAutoCodePartMapper.class));
            context.registerBean(SysAutoCodeResultMapper.class, () -> mock(SysAutoCodeResultMapper.class));
            context.registerBean(AutoCodeExtraMapper.class, () -> mock(AutoCodeExtraMapper.class));
            context.registerBean(PartTypeHandler.class, () -> new PartTypeHandler(new PartTypeInputCharHandler(),
                    new PartTypeNowDateHandler(), new PartTypeFixCharHandler(), new PartTypeSerialNoHandler()));
            context.registerBean(AutoCodeFactoryContext.class, () -> () -> "FACTORY_ASSEMBLY");
            context.registerBean("masterTransactionManager", PlatformTransactionManager.class,
                    () -> mock(PlatformTransactionManager.class));
            context.register(SysAutoCodeGenerationServiceImpl.class);
            context.refresh();

            assertSame(context.getBean(SysAutoCodeGenerationServiceImpl.class),
                    context.getBean(AutoCodeNumbering.class));
        }
    }

    @Test
    void frameworkAdapterReadsFactoryFromTheBoundThreadContext() {
        try (var context = new AnnotationConfigApplicationContext(AutoCodeFactoryContextAdapter.class)) {
            var factoryContext = context.getBean(AutoCodeFactoryContext.class);
            assertEquals("FACTORY_ASSEMBLY", FactoryContext.withScope(
                    new FactoryScope(new FactoryId("FACTORY_ASSEMBLY")), factoryContext::requiredFactoryId));
        }
    }
}
