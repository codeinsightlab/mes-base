package dev.mesbase.system.strategy;
import dev.mesbase.common.core.domain.entity.SysAutoCodePart;
import dev.mesbase.common.exception.ServiceException;
/** Source serial formatter PORT. The next value is provided by a DB-locked operation. */
@org.springframework.stereotype.Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="mes.system.enabled",havingValue="true")
public class PartTypeSerialNoHandler implements PartTypeTemplate {
 public String partHandle(SysAutoCodePart part){String number=String.valueOf(part.getSeriaNowNo());if(part.getSeriaNowNo()==null||part.getSeriaNowNo()<0||number.length()>part.getPartLength())throw new ServiceException("流水号已超出组成长度");return "0".repeat(part.getPartLength()-number.length())+number;}
}
