package dev.mesbase.platform.management;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;
import dev.mesbase.infrastructure.platform.PlatformRows.*;
/** Transport contracts are separate from PlatformRows; no password hash is exposed. */
public final class PlatformContracts {
 private PlatformContracts() {}
 public record PageView<T>(List<T> items,long total,int offset,int limit) {}
 public record LinksView(List<String> ids) {}
 public record UserInput(@NotBlank @Pattern(regexp="[A-Za-z0-9_.-]{3,64}") String username,@NotBlank @Size(max=100) String displayName,@NotNull Status status) {}
 public record PasswordInput(@NotBlank @Size(min=12,max=128) String password) { @Override public String toString(){return "PasswordInput[REDACTED]";} }
 public record NewUser(@NotNull @jakarta.validation.Valid UserInput user,@NotNull @jakarta.validation.Valid PasswordInput credential) { @Override public String toString(){return "NewUser[REDACTED]";} }
 public record Login(@NotBlank @Size(max=64) String username,@NotBlank @Size(max=128) String password) { @Override public String toString(){return "Login[REDACTED]";} }
 public record SessionView(String accessToken,LocalDateTime expiresAt) { @Override public String toString(){return "SessionView[REDACTED]";} }
 public record UserView(String id,String username,String displayName,Status status,LocalDateTime createdAt) {}
 public record RoleInput(@NotBlank @Size(max=100) String name,@NotNull RoleKind kind,@Pattern(regexp="[A-Za-z0-9_-]{1,64}") String factoryId,@NotNull Status status) {}
 public record RoleView(String id,String name,RoleKind kind,String factoryId,Status status) {}
 public record Links(@NotNull @Size(max=200) List<@NotBlank @Size(max=36) String> ids) {}
 public record MenuInput(String parentId,@NotBlank @Size(max=100) String name,@NotBlank @Pattern(regexp="/[A-Za-z0-9/_-]{0,159}") String path,@NotBlank @Pattern(regexp="[a-z][a-z0-9.:_-]{0,99}") String permission,@NotNull @Min(0) @Max(10000) Integer sortOrder,@NotNull MenuKind kind) {}
 public record MenuView(String id,String parentId,String name,String path,String permission,Integer sortOrder,MenuKind kind,List<MenuView> children) {}
 public record Navigation(List<MenuView> menus,List<String> permissions) {}
 public record DictTypeInput(@NotBlank @Pattern(regexp="[a-z][a-z0-9_-]{0,63}") String code,@NotBlank @Size(max=100) String name) {}
 public record DictTypeView(String id,String code,String name) {}
 public record DictDataInput(@NotBlank String typeId,@NotBlank @Size(max=100) String value,@NotBlank @Size(max=100) String label,@NotNull @Min(0) @Max(10000) Integer sortOrder) {}
 public record DictDataView(String id,String typeId,String value,String label,Integer sortOrder) {}
 public record ConfigInput(@NotBlank @Pattern(regexp="[a-z][a-z0-9_.-]{0,99}") String configKey,@NotNull @Size(max=500) String configValue,@NotNull ConfigKind kind,@Pattern(regexp="[A-Za-z0-9_-]{1,64}") String factoryId) {}
 public record ConfigView(String id,String configKey,String configValue,ConfigKind kind,@Pattern(regexp="[A-Za-z0-9_-]{1,64}") String factoryId) {}
 public record JobInput(@NotBlank @Size(max=100) String name,@NotBlank String handler,@NotNull JobScope executionScope,@Pattern(regexp="[A-Za-z0-9_-]{1,64}") String factoryId,@NotBlank String cron,@NotNull Status status) {}
 public record JobView(String id,String name,String handler,JobScope executionScope,@Pattern(regexp="[A-Za-z0-9_-]{1,64}") String factoryId,String cron,Status status,boolean running) {}
 public record AuditView(String id,String requestId,String actorId,String factoryId,JobScope executionScope,String action,String outcome,LocalDateTime createdAt) {}
 public record LoginLogView(String id,String requestId,String actorId,String factoryId,JobScope executionScope,String outcome,LocalDateTime createdAt) {}
 public record JobLogView(String id,String jobId,String requestId,String actorId,String factoryId,JobScope executionScope,String outcome,LocalDateTime createdAt) {}
 public record FileView(String id,String ownerId,String factoryId,String resource,String originalName,Long size,LocalDateTime createdAt) {}
}
