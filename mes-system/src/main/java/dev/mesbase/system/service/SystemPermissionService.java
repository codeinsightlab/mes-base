package dev.mesbase.system.service;

import dev.mesbase.system.mapper.SysMenuMapper;
import dev.mesbase.common.core.domain.entity.SysMenu;
import dev.mesbase.system.mapper.SysRoleMapper;
import dev.mesbase.system.factory.ManagedFactoryMapper;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public class SystemPermissionService {
    private final SysMenuMapper menus;
    private final SysRoleMapper roles;
    private final ManagedFactoryMapper factories;

    public SystemPermissionService(SysMenuMapper menus, SysRoleMapper roles, ManagedFactoryMapper factories) {
        this.menus = menus;
        this.roles = roles;
        this.factories = factories;
    }

    public record WorkspaceAccess(boolean platform, List<String> factoryIds) {
    }

    public WorkspaceAccess workspaces(String username) {
        var scopes = roles.selectWorkspaceScopes(username);
        var factoryIds = scopes.stream().filter(scope -> "FACTORY".equals(scope.kind())).map(scope -> scope.factoryId())
                .distinct().sorted().toList();
        // These legacy columns have different collations. Bind IDs rather than joining text columns.
        var enabled = factoryIds.isEmpty() ? Set.<String>of() : Set.copyOf(factories.enabledIds(factoryIds));
        return new WorkspaceAccess(scopes.stream().anyMatch(scope -> "PLATFORM".equals(scope.kind())),
                factoryIds.stream().filter(enabled::contains).toList());
    }

    public Set<String> permissions(String user, String factory) {
        var result = new HashSet<String>();
        var platformCodes = new HashSet<String>();
        if (factory != null) {
            var allMenus = menus.selectMenuList(new SysMenu());
            var policy = new MenuScopePolicy(allMenus);
            for (var menu : allMenus) {
                if (policy.platform(menu) && menu.getPerms() != null) {
                    platformCodes.addAll(List.of(menu.getPerms().split(",")));
                }
            }
        }
        for (String values : menus.selectScopedPermissions(user, factory)) {
            if (values != null) {
                for (String p : values.split(",")) {
                    if (!p.isBlank() && (factory == null ||
                            !MenuScopePolicy.platformPermission(p) && !platformCodes.contains(p))) {
                        result.add(p);
                    }
                }
            }
        }
        return Set.copyOf(result);
    }

    public boolean granted(String user, String factory, String permission) {
        if (factory == null || factory.isBlank()) return false;
        var permissions = permissions(user, factory);
        return permissions.contains(permission);
    }
}
