package dev.mesbase.system.strategy;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.util.Map;
import dev.mesbase.common.core.domain.entity.SysAutoCodePart;
import dev.mesbase.common.exception.ServiceException;
/** Source strategy dispatch PORT, explicit allowlist instead of bean index/order. */
@Component
@ConditionalOnProperty(name="mes.system.enabled",havingValue="true")
public class PartTypeHandler {
 private final Map<String,PartTypeTemplate> handlers;
 public PartTypeHandler(PartTypeInputCharHandler input,PartTypeNowDateHandler date,PartTypeFixCharHandler fixed,PartTypeSerialNoHandler serial){handlers=Map.of("INPUTCHAR",input,"NOWDATE",date,"FIXCHAR",fixed,"SERIALNO",serial);}
 public String choiceExecute(SysAutoCodePart part){var handler=handlers.get(part.getPartType());if(handler==null)throw new ServiceException("不支持的规则组成类型");return handler.partHandle(part);}
}
