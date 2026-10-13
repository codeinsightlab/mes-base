package dev.mesbase.system.service;

import dev.mesbase.system.mapper.SysMenuMapper;
import dev.mesbase.common.exception.ServiceException;
import dev.mesbase.common.core.domain.entity.SysMenu;
import dev.mesbase.system.mapper.SysRoleMapper;
import dev.mesbase.system.factory.ManagedFactoryMapper;

import dev.mesbase.common.core.domain.model.LoginUser;
import dev.mesbase.system.domain.WorkspaceRoleScope;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public class SystemPermissionService {
    private static final Logger LOG = LoggerFactory.getLogger(SystemPermissionService.class);
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

    private record CacheKey(String kind, String user, String factory) {
    }

    public WorkspaceAccess workspaces(String username) {
        var scopes = roleScopes(username);
        var factoryIds = scopes.stream().filter(scope -> "FACTORY".equals(scope.kind())).map(scope -> scope.factoryId())
                .distinct().sorted().toList();
        // These legacy columns have different collations. Bind IDs rather than joining text columns.
        var enabled = factoryIds.isEmpty() ? Set.<String>of() : Set.copyOf(factories.enabledIds(factoryIds));
        boolean platform = scopes.stream().anyMatch(scope -> "PLATFORM".equals(scope.kind()));
        boolean platformBusiness = platform && !cached(new CacheKey("platform-business", username, null), () -> platformBusinessPermissions(username)).isEmpty();
        return new WorkspaceAccess(platform, platformBusiness,
                factoryIds.stream().filter(enabled::contains).toList());
    }

    public Set<String> permissions(String user, String factory) {
        return cached(new CacheKey("permissions", user, factory), () -> scopedPermissions(user, factory));
    }

    private Set<String> scopedPermissions(String user, String factory) {
        var result = new HashSet<String>();
        for (String values : cached(new CacheKey("grants", user, factory), () -> menus.selectScopedPermissions(user, factory)
                .stream().filter(Objects::nonNull).filter(value -> !value.isBlank()).toList())) {
            if (values != null) {
                for (String p : values.split(",")) {
                    p = p.trim();
                    if (!p.isBlank()) {
                        result.add(p);
                    }
                }
            }
        }
        return Set.copyOf(result);
    }

    private Set<String> platformBusinessPermissions(String username) {
        var allowed = new HashSet<String>();
        var policy = menuPolicy();
        for (var menu : menuCatalog())
            if (!policy.platform(menu) && menu.getPerms() != null)
                for (String code : menu.getPerms().split(",")) if (!code.isBlank()) allowed.add(code.trim());
        var grants = new HashSet<>(permissions(username, null));
        grants.retainAll(allowed);
        return Set.copyOf(grants);
    }

    private volatile Map<String, Boolean> endpointScopes = Map.of();

    public void installEndpointScopes(Map<String, Boolean> scopes) {
        endpointScopes = Map.copyOf(scopes);
        validateMenuAuthorization();
    }

    /**
     * Management/startup audit only, never called from business request authorization.
     */
    public void validateMenuAuthorization() {
        List<SysMenu> rows = menus.selectMenuList(new SysMenu());
        var policy = new MenuScopePolicy(rows);
        var codes = new HashMap<>(endpointScopes);
        for (var menu : rows) {
            boolean platform = policy.platform(menu);
            if (menu.getPerms() != null) for (String code : menu.getPerms().split(",")) {
                code = code.trim();
                if (code.isEmpty()) continue;
                Boolean previous = codes.putIfAbsent(code, platform);
                if (previous != null && previous != platform)
                    throw new ServiceException("权限码跨平台/业务范围复用: " + code);
            }
        }
        int missing = 0;
        for (Long id : menus.selectFactoryAssignedMenuIds()) {
            var menu = policy.find(id);
            if (menu.isEmpty()) {
                missing++;
                continue;
            }
            if (policy.platform(menu.get()))
                throw new ServiceException("工厂角色存在平台菜单授权，需修正菜单编号: " + id);
        }
        if (missing > 0) LOG.warn("菜单授权审查跳过 {} 个已删除菜单的工厂角色历史关联，失效关联不授予权限", missing);
    }

    public boolean hasPlatformRole(String user) {
        return roleScopes(user).stream().anyMatch(scope -> "PLATFORM".equals(scope.kind()));
    }

    public boolean grantedPlatform(String user, String permission) {
        return permissions(user, null).contains(permission);
    }

    public boolean grantedFactory(String user, String factory, String permission) {
        if (factory == null || factory.isBlank()) return false;
        var permissions = permissions(user, factory);
        return permissions.contains(permission);
    }

    public List<SysMenu> menuCatalog() {
        return cached("menu-catalog", () -> List.copyOf(menus.selectMenuList(new SysMenu())));
    }

    public MenuScopePolicy menuPolicy() {
        return cached("menu-policy", () -> new MenuScopePolicy(menuCatalog()));
    }

    private List<WorkspaceRoleScope> roleScopes(String username) {
        return cached(new CacheKey("roles", username, null), () -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof LoginUser login
                    && username.equals(login.getUsername()) && login.getUser().getRoles() != null) {
                return login.getUser().getRoles().stream()
                        .filter(role -> "0".equals(role.getStatus()) && "0".equals(role.getDelFlag()))
                        .map(role -> new WorkspaceRoleScope(role.getKind(), role.getFactoryId())).toList();
            }
            return List.copyOf(roles.selectWorkspaceScopes(username));
        });
    }

    @SuppressWarnings("unchecked")
    private <T> T cached(Object key, Supplier<T> load) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes))
            return load.get();
        var request = attributes.getRequest();
        String attribute = SystemPermissionService.class.getName();
        synchronized (request) {
            Map<Object, Object> values = (Map<Object, Object>) request.getAttribute(attribute);
            if (values == null) {
                values = new HashMap<>();
                request.setAttribute(attribute, values);
            }
            if (values.containsKey(key)) return (T) values.get(key);
            // Nested grant entries share this request map; menu catalog is loaded only by navigation/management.
            T result = load.get();
            values.put(key, result);
            return result;
        }
    }

}
