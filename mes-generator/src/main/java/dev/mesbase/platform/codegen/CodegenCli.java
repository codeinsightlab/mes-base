package dev.mesbase.platform.codegen;

import dev.mesbase.common.utils.StringUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.nio.file.Path;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.Banner;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.AbstractDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stable developer entry. Boot ConfigData is loaded without starting web/security/production beans.
 */
public final class CodegenCli {
    public record Profile(String url, String username, String password, String driverClassName) {
        @Override
        public String toString() {
            return "Profile[REDACTED]";
        }
    }

    public record Settings(String defaultDatasource, Map<String, Profile> profiles) {
        @Override
        public String toString() {
            return "Settings[REDACTED]";
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class ToolConfiguration {
    }

    public static void main(String[] args) {
        System.exit(run(args));
    }

    public static int run(String[] args) {
        try {
            Map<String, String> flags = parse(args);
            if (flags.containsKey("help")) {
                System.out.println("mes-codegen --table=TABLE [--module=MODULE] --business-name=NAME --scope=GLOBAL|FACTORY_SCOPED [--datasource=PROFILE] [--schema=SCHEMA] [--package=dev.mesbase.modules.MODULE] [--backend-module=mes-business] [--author=NAME] [--dry-run] [--overwrite] [--output=BACKEND_ROOT] [--frontend-root=FRONTEND_ROOT] [--config=PATH] [--format-java] [--generate-sync=true|false --sync-match-key=COLUMN]");
                return 0;
            }
            var app = new SpringApplication(ToolConfiguration.class);
            app.setWebApplicationType(WebApplicationType.NONE);
            app.setLogStartupInfo(false);
            app.setBannerMode(Banner.Mode.OFF);
            List<String> config = new ArrayList<>();
            config.add("--logging.level.root=ERROR");
            if (flags.containsKey("config"))
                config.add("--spring.config.additional-location=" + Path.of(flags.get("config")).toAbsolutePath()
                        .toUri());
            try (var context = app.run(config.toArray(String[]::new))) {
                Environment env = context.getEnvironment();
                var settings = Binder.get(env).bind("mes.codegen", Settings.class)
                        .orElseThrow(() -> new CodegenException(CodegenException.Code.UNKNOWN_DATASOURCE));
                Map<String, DataSource> sources = new HashMap<>();
                if (settings.profiles() != null) settings.profiles().forEach((name, p) -> {
                    if (StringUtils.isBlank(p.url())) {
                        sources.put(name, new AbstractDataSource() {
                            public Connection getConnection() throws SQLException {
                                throw new SQLException("Developer profile unavailable");
                            }

                            public Connection getConnection(String u, String password) throws SQLException {
                                return getConnection();
                            }
                        });
                        return;
                    }
                    if (!p.url().startsWith("jdbc:") || p.url().toLowerCase(Locale.ROOT)
                            .matches(".*(password|pwd|secret)=.*"))
                        throw new CodegenException(CodegenException.Code.INVALID_REQUEST);
                    var ds = new DriverManagerDataSource();
                    if (StringUtils.isNotBlank(p.driverClassName()))
                        ds.setDriverClassName(p.driverClassName());
                    ds.setUrl(p.url());
                    ds.setUsername(p.username());
                    ds.setPassword(p.password());
                    sources.put(name, ds);
                });
                String module = flags.get("module");
                Path backend = Path.of(flags.getOrDefault("output", System.getProperty("user.dir"))).toAbsolutePath()
                        .normalize();
                Path frontend = Path.of(flags.getOrDefault("frontend-root", backend.resolveSibling("mes-base-ui")
                        .toString()));
                var request = new CodegenRequest(flags.get("datasource"), flags.get("schema"), required(flags, "table"), module, required(flags, "business-name"), flags.get("package"), flags.getOrDefault("author", "MES Base"), CodegenRequest.ScopeKind.valueOf(flags.getOrDefault("scope", "FACTORY_SCOPED")), flags.containsKey("overwrite") ? CodegenRequest.OverwritePolicy.REPLACE_GENERATED : CodegenRequest.OverwritePolicy.FAIL, flags.containsKey("dry-run"), backend, frontend, flags.getOrDefault("backend-module", "mes-business"), syncFlag(flags), flags.get("sync-match-key"));
                var profiles = new CodegenProfiles(settings.defaultDatasource(), sources);
                MesCodegenService generator = flags.containsKey("format-java")
                        ? new DefaultMesCodegenService(profiles, new IdeaGeneratedJavaFormatter(
                        Path.of(System.getProperty("user.dir"), "scripts", "format-java")))
                        : new DefaultMesCodegenService(profiles);
                var result = generator.generate(request);
                System.out.println(new JsonMapper().writerWithDefaultPrettyPrinter().writeValueAsString(result));
                return 0;
            }
        } catch (CodegenException e) {
            System.err.println("{\"status\":\"FAILED\",\"errorCode\":\"" + e.code() + "\"}");
            return 2;
        } catch (RuntimeException e) {
            System.err.println("{\"status\":\"FAILED\",\"errorCode\":\"INVALID_REQUEST\"}");
            return 2;
        }
    }

    private static boolean syncFlag(Map<String, String> flags) {
        String value = flags.getOrDefault("generate-sync", "false");
        if (!Set.of("true", "false").contains(value)) throw new CodegenException(CodegenException.Code.INVALID_REQUEST);
        return Boolean.parseBoolean(value);
    }

    private static String required(Map<String, String> flags, String key) {
        String value = flags.get(key);
        if (StringUtils.isBlank(value)) throw new CodegenException(CodegenException.Code.INVALID_REQUEST);
        return value;
    }

    private static Map<String, String> parse(String[] args) {
        Set<String> allowed = Set.of("help", "table", "module", "business-name", "scope", "datasource", "schema", "package", "author", "dry-run", "overwrite", "output", "frontend-root", "config", "format-java", "backend-module", "generate-sync", "sync-match-key");
        Set<String> switches = Set.of("help", "dry-run", "overwrite", "format-java");
        Map<String, String> flags = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (!a.startsWith("--")) throw new CodegenException(CodegenException.Code.INVALID_REQUEST);
            String[] split = a.substring(2).split("=", 2);
            String key = split[0];
            if (!allowed.contains(key) || flags.containsKey(key))
                throw new CodegenException(CodegenException.Code.INVALID_REQUEST);
            String value = split.length == 2 ? split[1] : switches.contains(key) ? "true" : ++i < args.length ? args[i] : "";
            if (value.isEmpty() || switches.contains(key) && !value.equals("true"))
                throw new CodegenException(CodegenException.Code.INVALID_REQUEST);
            flags.put(key, value);
        }
        return flags;
    }
}
