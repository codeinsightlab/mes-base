package dev.mesbase.platform.execution;

import dev.mesbase.system.service.AutoCodeFactoryContext;
import org.springframework.stereotype.Component;

@Component
public class AutoCodeFactoryContextAdapter implements AutoCodeFactoryContext {
    @Override
    public String requiredFactoryId() {
        return FactoryContext.required().factoryId().value();
    }
}
