package dev.mesbase.system.strategy;

import dev.mesbase.common.core.domain.entity.SysAutoCodePart;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="mes.system.enabled",havingValue="true")
public class PartTypeFixCharHandler implements PartTypeTemplate {
    @Override
    public String partHandle(SysAutoCodePart sysAutoCodePart) {
        return sysAutoCodePart.getFixCharacter();
    }
}
