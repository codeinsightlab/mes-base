package dev.mesbase.system.strategy;
import dev.mesbase.common.core.domain.entity.SysAutoCodePart;
import dev.mesbase.common.exception.ServiceException;
/** Source strategy dispatch PORT, explicit allowlist instead of bean index/order. */
@org.springframework.stereotype.Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="mes.system.enabled",havingValue="true")
public class PartTypeHandler {
 private final java.util.Map<String,PartTypeTemplate> handlers;
 public PartTypeHandler(PartTypeInputCharHandler input,PartTypeNowDateHandler date,PartTypeFixCharHandler fixed,PartTypeSerialNoHandler serial){handlers=java.util.Map.of("INPUTCHAR",input,"NOWDATE",date,"FIXCHAR",fixed,"SERIALNO",serial);}
 public String choiceExecute(SysAutoCodePart part){var handler=handlers.get(part.getPartType());if(handler==null)throw new ServiceException("不支持的规则组成类型");return handler.partHandle(part);}
}
