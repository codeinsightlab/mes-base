package dev.mesbase.web.controller.system;

import dev.mesbase.platform.execution.FactoryIntegrationContext;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


import dev.mesbase.common.core.domain.AjaxResult;
import dev.mesbase.common.utils.PasswordPolicy;
import dev.mesbase.common.utils.SecurityUtils;
import dev.mesbase.framework.web.service.SysLoginService;
import dev.mesbase.framework.web.service.TokenService;
import dev.mesbase.platform.execution.FactoryContext;
import dev.mesbase.platform.web.EndpointAccess;
import dev.mesbase.system.domain.vo.RouterVo;
import dev.mesbase.system.service.ISysMenuService;
import dev.mesbase.system.service.SystemPermissionService;
import dev.mesbase.web.view.user.SysUserAssembler;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@RestController
@ConditionalOnProperty(name = "mes.system.enabled", havingValue = "true")
public class SystemSessionController {
    public record Login(@NotBlank @Size(max = 64) String username, @NotBlank @Size(max = 128) String password,
                        String code, String uuid) {
    }

    public record Session(String accessToken, Instant expiresAt) {
    }

    @Autowired
    private PasswordPolicy passwords;
    private final TokenService tokens;
    private final SystemPermissionService permissions;
    private final ISysMenuService menus;
    private final SysLoginService login;

    public SystemSessionController(TokenService tokens, SystemPermissionService permissions, ISysMenuService menus,
                                   SysLoginService login) {
        this.tokens = tokens;
        this.permissions = permissions;
        this.menus = menus;
        this.login = login;
    }

    @PostMapping("/api/session")
    @EndpointAccess(EndpointAccess.Kind.PUBLIC)
    public SysLoginService.Session login(@Valid @RequestBody Login input, HttpServletRequest request) {
        return login.login(input.username(), input.password(), input.code(), input.uuid(), request);
    }

    @DeleteMapping("/api/session")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHENTICATED, permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    public void logout() {
        tokens.delLoginUser(SecurityUtils.getLoginUser().getToken());
    }

    @GetMapping("/api/session/info")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHENTICATED, permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    public AjaxResult info() {
        var a = AjaxResult.success();
        a.put("user", SysUserAssembler.toResult(SecurityUtils.getLoginUser().getUser()));
        a.put("passwordPolicy", passwords.view());
        a.put("permissions", permissions.permissions(SecurityUtils.getUsername(), null));
        a.put("workspaces", permissions.workspaces(SecurityUtils.getUsername()));
        return a;
    }

    @GetMapping("/api/factory/navigation")
    @EndpointAccess(EndpointAccess.Kind.AUTHENTICATED)
    public Object factoryNavigation() {
        return new FactoryNavigation(permissions.permissions(SecurityUtils.getUsername(), FactoryContext.required()
                .factoryId()
                .value()), menus.buildMenus(menus.selectFactoryMenuTree(SecurityUtils.getUsername(), FactoryContext.required()
                .factoryId().value())), FactoryIntegrationContext.current().map(snapshot -> snapshot.mode().name())
                .orElse(null));
    }

    public record FactoryNavigation(Set<String> permissions, List<RouterVo> menus, String runtimeMode) {
    }

    @GetMapping("/api/session/menus")
    @EndpointAccess(value = EndpointAccess.Kind.AUTHENTICATED, permissionScope = EndpointAccess.PermissionScope.PLATFORM)
    public AjaxResult routers() {
        return AjaxResult.success(menus.buildMenus(menus.selectMenuTreeByUserId(SecurityUtils.getUserId())));
    }
}
