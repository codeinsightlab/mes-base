package dev.mesbase.system.service;

import dev.mesbase.system.mapper.SysMenuMapper;
import java.util.HashSet;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service @ConditionalOnProperty(name="mes.system.enabled",havingValue="true")
public class SystemPermissionService {
 private final SysMenuMapper menus;public SystemPermissionService(SysMenuMapper menus){this.menus=menus;}
 public Set<String> permissions(String user,String factory){var result=new HashSet<String>();for(String values:menus.selectScopedPermissions(user,factory)){if(values!=null)for(String p:values.split(","))if(!p.isBlank())result.add(p);}return Set.copyOf(result);}
 public boolean granted(String user,String factory,String permission){var permissions=permissions(user,factory);return permissions.contains(permission);}
}
