package dev.mesbase.system.strategy;
import dev.mesbase.common.core.domain.entity.SysAutoCodePart;
import dev.mesbase.common.exception.ServiceException;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
/** Source serial formatter PORT. The next value is provided by a DB-locked operation. */
@Component
@ConditionalOnProperty(name="mes.system.enabled",havingValue="true")
public class PartTypeSerialNoHandler implements PartTypeTemplate {
 public String partHandle(SysAutoCodePart part){if(part.getSeriaNowNo()==null||part.getSeriaNowNo()<0||part.getPartLength()==null||part.getPartLength()<1)throw new ServiceException("流水号或组成长度非法");String number=String.valueOf(part.getSeriaNowNo());return "0".repeat(Math.max(0,part.getPartLength()-number.length()))+number;}
}
