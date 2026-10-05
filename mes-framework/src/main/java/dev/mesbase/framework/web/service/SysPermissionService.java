package dev.mesbase.framework.web.service;
@org.springframework.stereotype.Service @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="mes.system.enabled",havingValue="true")
public class SysPermissionService {
 private final dev.mesbase.system.service.SystemPermissionService service;
 public SysPermissionService(dev.mesbase.system.service.SystemPermissionService s){service=s;}
 public java.util.Set<String> getMenuPermission(dev.mesbase.common.core.domain.entity.SysUser u){return service.permissions(u.getUserName(),null);}
}