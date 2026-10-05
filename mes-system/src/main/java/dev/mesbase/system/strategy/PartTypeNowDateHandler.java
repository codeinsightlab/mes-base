package dev.mesbase.system.strategy;

import dev.mesbase.common.core.domain.entity.SysAutoCodePart;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
@Order(1)
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="mes.system.enabled",havingValue="true")
public class PartTypeNowDateHandler implements PartTypeTemplate {

    @Override
    public String partHandle(SysAutoCodePart sysAutoCodePart) {
        String formatDate = sysAutoCodePart.getDateFormat();
        return DateTimeFormatter.ofPattern(formatDate).format(LocalDateTime.now());
    }
}
