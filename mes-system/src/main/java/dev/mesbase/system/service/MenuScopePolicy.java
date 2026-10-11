package dev.mesbase.system.service;

import dev.mesbase.common.core.domain.entity.SysMenu;
import dev.mesbase.common.exception.ServiceException;
import java.util.HashMap;
import java.util.ArrayList;
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
        List<String> parts = new ArrayList<>();
        while (menu != null) {
            if (!visited.add(menu.getMenuId())) throw new ServiceException("Menu hierarchy contains a cycle");
            String path = menu.getPath();
            if (!"F".equals(menu.getMenuType()) && path != null && !path.equals("#") && !path.isBlank()) {
                parts.add(0, path);
                if (path.startsWith("/")) break;
            }
            menu = menus.get(menu.getParentId());
        }
        String path = String.join("/", parts);
        if (path.startsWith("/")) path = path.substring(1);
        if (path.equals("api")) path = "";
        else if (path.startsWith("api/")) path = path.substring(4);
        String resolved = path;
        return List.of("system", "tool", "monitor").stream()
                .anyMatch(prefix -> resolved.equals(prefix) || resolved.startsWith(prefix + "/"));
    }

    public SysMenu require(Long id) {
        SysMenu menu = menus.get(id);
        if (menu == null) throw new ServiceException("Menu does not exist");
        return menu;
    }
}
