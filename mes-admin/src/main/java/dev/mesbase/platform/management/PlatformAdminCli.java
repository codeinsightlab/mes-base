package dev.mesbase.platform.management;

import dev.mesbase.common.core.domain.entity.SysRole;
import dev.mesbase.common.core.domain.entity.SysMenu;
import dev.mesbase.common.core.domain.entity.SysUser;
import dev.mesbase.common.utils.PasswordPolicy;
import dev.mesbase.infrastructure.persistence.PlatformPersistence;
import dev.mesbase.server.DruidPools;
import dev.mesbase.system.domain.SysRoleMenu;
import dev.mesbase.system.domain.SysUserRole;
import dev.mesbase.system.mapper.SysMenuMapper;
import dev.mesbase.system.mapper.SysRoleMapper;
import dev.mesbase.system.mapper.SysRoleMenuMapper;
import dev.mesbase.system.mapper.SysUserMapper;
import dev.mesbase.system.mapper.SysUserRoleMapper;
import dev.mesbase.system.mapper.SystemBootstrapMapper;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.annotation.Configuration;
import dev.mesbase.server.schema.FactoryFlyway;

/**
 * First Source-system administrator only. Environment password; atomic empty-database guard; no userId magic.
 */
public final class PlatformAdminCli {
    @Configuration(proxyBeanMethods = false)
    public static class BootstrapConfiguration {
    }

    private static final class AlreadyInitialized extends IllegalStateException {
    }

    public static void main(String[] args) {
        try {
            String username = null;
            String config = null;
            boolean migrate = false;
            for (String arg : args) {
                if (arg.startsWith("--username=")) username = arg.substring(11);
                else if (arg.startsWith("--config=")) config = arg.substring(9);
                else if (arg.equals("--migrate")) migrate = true;
                else throw new IllegalArgumentException("Unknown argument");
            }
            if (username == null || !username.matches("[A-Za-z][A-Za-z0-9_.-]{1,29}"))
                throw new IllegalArgumentException("Username invalid");
            var app = new SpringApplication(BootstrapConfiguration.class);
            app.setWebApplicationType(WebApplicationType.NONE);
            app.setBannerMode(Banner.Mode.OFF);
            app.setLogStartupInfo(false);
            var settings = new ArrayList<String>();
            settings.add("--logging.level.root=ERROR");
            if (config != null)
                settings.add("--spring.config.additional-location=" + Path.of(config).toAbsolutePath().toUri());
            try (var context = app.run(settings.toArray(String[]::new))) {
                var env = context.getEnvironment();
                String password = env.getRequiredProperty("MES_BOOTSTRAP_PASSWORD");
                String encodedPassword = new PasswordPolicy(env).encode(password);
                try (var pool = DruidPools.create(DruidPools.settings(env), "system-bootstrap", env.getRequiredProperty("mes.platform.url"), env.getRequiredProperty("mes.platform.username"), env.getRequiredProperty("mes.platform.password"))) {
                    if (migrate) FactoryFlyway.configure(pool, env).load().migrate();
                    var persistence = new PlatformPersistence(pool, Set.of(SystemBootstrapMapper.class, SysUserMapper.class, SysRoleMapper.class, SysMenuMapper.class, SysRoleMenuMapper.class, SysUserRoleMapper.class), c -> {
                        c.getTypeAliasRegistry().registerAliases("dev.mesbase.common.core.domain.entity");
                        c.getTypeAliasRegistry().registerAliases("dev.mesbase.system.domain");
                    });
                    final String account = username;
                    String id = persistence.transaction(session -> {
                        var guard = session.mapper(SystemBootstrapMapper.class);
                        guard.lock();
                        if (guard.users() != 0) throw new AlreadyInitialized();
                        var role = new SysRole();
                        role.setRoleName("平台管理员");
                        role.setRoleKey("platform-admin");
                        role.setRoleSort("0");
                        role.setKind("PLATFORM");
                        role.setFactoryId(null);
                        role.setDataScope("1");
                        role.setStatus("0");
                        role.setCreateBy("BOOTSTRAP");
                        session.mapper(SysRoleMapper.class).insertRole(role);
                        var menus = session.mapper(SysMenuMapper.class).selectMenuList(new SysMenu());
                        var links = new ArrayList<SysRoleMenu>();
                        for (var menu : menus) {
                            String permission = menu.getPerms();
                            if ("0".equals(menu.getStatus()) && ("M".equals(menu.getMenuType()) || permission != null && (permission.startsWith("system:") || permission.startsWith("monitor:") || permission.startsWith("tool:")))) {
                                var link = new SysRoleMenu();
                                link.setRoleId(role.getRoleId());
                                link.setMenuId(menu.getMenuId());
                                links.add(link);
                            }
                        }
                        if (!links.isEmpty()) session.mapper(SysRoleMenuMapper.class).batchRoleMenu(links);
                        var user = new SysUser();
                        user.setUserName(account);
                        user.setNickName("平台管理员");
                        user.setUserType("00");
                        user.setSex("2");
                        user.setStatus("0");
                        user.setCreateBy("BOOTSTRAP");
                        user.setPassword(encodedPassword);
                        session.mapper(SysUserMapper.class).insertUser(user);
                        var membership = new SysUserRole();
                        membership.setUserId(user.getUserId());
                        membership.setRoleId(role.getRoleId());
                        session.mapper(SysUserRoleMapper.class).batchUserRole(List.of(membership));
                        return user.getUserId().toString();
                    });
                    System.out.println("{\"status\":\"SUCCEEDED\",\"userId\":\"" + id + "\"}");
                }
            }
        } catch (RuntimeException failure) {
            String code = failure instanceof AlreadyInitialized ? "ALREADY_INITIALIZED" : "PROVISIONING_FAILED";
            System.err.println("{\"status\":\"FAILED\",\"errorCode\":\"" + code + "\"}");
            System.exit(2);
        }
    }
}
