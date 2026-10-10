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

    public record WorkspaceAccess(boolean platform, boolean platformBusiness, List<String> factoryIds) {
    }

    public WorkspaceAccess workspaces(String username) {
        var scopes = roles.selectWorkspaceScopes(username);
        var factoryIds = scopes.stream().filter(scope -> "FACTORY".equals(scope.kind())).map(scope -> scope.factoryId())
                .distinct().sorted().toList();
        // These legacy columns have different collations. Bind IDs rather than joining text columns.
        var enabled = factoryIds.isEmpty() ? Set.<String>of() : Set.copyOf(factories.enabledIds(factoryIds));
        boolean platform = scopes.stream().anyMatch(scope -> "PLATFORM".equals(scope.kind()));
        boolean platformBusiness = platform && !scopedPermissions(username, null, true).isEmpty();
        return new WorkspaceAccess(platform, platformBusiness,
                factoryIds.stream().filter(enabled::contains).toList());
    }

    public Set<String> permissions(String user, String factory) {
        return scopedPermissions(user, factory, factory != null);
    }

    private Set<String> scopedPermissions(String user, String factory, boolean businessOnly) {
        var result = new HashSet<String>();
        var platformCodes = new HashSet<String>();
        if (businessOnly) {
            var allMenus = menus.selectMenuList(new SysMenu());
            var policy = new MenuScopePolicy(allMenus);
            for (var menu : allMenus) {
                if (policy.platform(menu) && menu.getPerms() != null) {
                    for (String code : menu.getPerms().split(",")) {
                        platformCodes.add(code.trim());
                    }
                }
            }
        }
        for (String values : menus.selectScopedPermissions(user, factory)) {
            if (values != null) {
                for (String p : values.split(",")) {
                    p = p.trim();
                    if (!p.isBlank() && (!businessOnly ||
                            !platformCodes.contains(p))) {
                        result.add(p);
                    }
                }
            }
        }
        return Set.copyOf(result);
    }

    public boolean hasPlatformRole(String user) {
        return roles.selectWorkspaceScopes(user).stream().anyMatch(scope -> "PLATFORM".equals(scope.kind()));
    }

    public boolean grantedPlatform(String user, String permission) {
        return permissions(user, null).contains(permission);
    }

    public boolean grantedFactory(String user, String factory, String permission) {
        if (factory == null || factory.isBlank()) return false;
        var permissions = permissions(user, factory);
        return permissions.contains(permission);
    }
}
