package dev.mesbase.server;

import dev.mesbase.common.core.domain.entity.SysMenu;
import dev.mesbase.system.service.MenuScopePolicy;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuScopePolicyTest {
    @Test
    void resolvedUrlAndButtonParentDetermineScopeRatherThanPermissionNames() {
        var business = menu(1L, 0L, "pro", "");
        var child = menu(2L, 1L, "system", "system:custom:read");
        var platform = menu(3L, 0L, "system", "");
        var button = menu(4L, 3L, "#", "custom:action");
        var absolute = menu(5L, 3L, "/pro/other", "monitor:custom:read");
        var policy = new MenuScopePolicy(List.of(business, child, platform, button, absolute));
        assertFalse(policy.platform(child));
        assertTrue(policy.platform(button));
        assertFalse(policy.platform(absolute));
    }

    private static SysMenu menu(Long id, Long parent, String path, String permission) {
        var menu = new SysMenu();
        menu.setMenuId(id);
        menu.setParentId(parent);
        menu.setPath(path);
        menu.setPerms(permission);
        return menu;
    }
}
