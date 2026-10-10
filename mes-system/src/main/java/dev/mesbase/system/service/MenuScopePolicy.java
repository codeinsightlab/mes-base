package dev.mesbase.system.service;

import dev.mesbase.common.core.domain.entity.SysMenu;
import dev.mesbase.common.exception.ServiceException;
import java.util.HashMap;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared menu classification for role assignment and factory navigation.
 */
public final class MenuScopePolicy {
    private final Map<Long, SysMenu> menus = new HashMap<>();

    public MenuScopePolicy(List<SysMenu> rows) {
        rows.forEach(menu -> menus.put(menu.getMenuId(), menu));
    }

    public boolean platform(SysMenu menu) {
        Set<Long> visited = new HashSet<>();
        while (menu != null) {
            if (!visited.add(menu.getMenuId())) throw new ServiceException("Menu hierarchy contains a cycle");
            if (menu.getPerms() != null && Arrays.stream(menu.getPerms().split(","))
                    .map(String::trim).anyMatch(MenuScopePolicy::platformPermission)) return true;
            String path = menu.getPath();
            if (path != null && !path.equals("#") && !path.isBlank()) {
                if (platformPrefix(path.startsWith("/") ? path.substring(1) : path, "/")) return true;
                if (path.startsWith("/")) return false;
            }
            menu = menus.get(menu.getParentId());
        }
        return false;
    }

    public static boolean platformPermission(String code) {
        return platformPrefix(code, ":");
    }

    private static boolean platformPrefix(String value, String separator) {
        return List.of("system", "tool", "monitor").stream()
                .anyMatch(prefix -> value.equals(prefix) || value.startsWith(prefix + separator));
    }

    public SysMenu require(Long id) {
        SysMenu menu = menus.get(id);
        if (menu == null) throw new ServiceException("Menu does not exist");
        return menu;
    }
}
