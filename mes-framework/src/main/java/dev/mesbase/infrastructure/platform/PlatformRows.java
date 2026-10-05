package dev.mesbase.infrastructure.platform;
/** Internal persistence contracts. Never return these records from HTTP. */
public final class PlatformRows {
 private PlatformRows() {}
 public enum Status { ENABLED, DISABLED }
 public enum RoleKind { PLATFORM, FACTORY }
 public enum MenuKind { MENU, BUTTON }
 public enum ConfigKind { GLOBAL, FACTORY }
 public enum JobScope { PLATFORM, FACTORY }
 public record User(String id, String username, String displayName, String passwordHash, Status status, java.time.LocalDateTime createdAt) {}
 public record Role(String id, String name, RoleKind kind, String factoryId, Status status) {}
 public record Menu(String id, String parentId, String name, String path, String permission, Integer sortOrder, MenuKind kind) {}
 public record DictType(String id, String code, String name) {}
 public record DictData(String id, String typeId, String value, String label, Integer sortOrder) {}
 public record Config(String id, String configKey, String configValue, ConfigKind kind, String factoryId) {}
 public record Job(String id, String name, String handler, JobScope executionScope, String factoryId, String cron, Status status, String runningToken, java.time.LocalDateTime startedAt) {}
 public record Audit(String id, String requestId, String actorId, String factoryId, JobScope executionScope, String action, String outcome, java.time.LocalDateTime createdAt) {}
 public record LoginLog(String id, String requestId, String actorId, String factoryId, JobScope executionScope, String outcome, java.time.LocalDateTime createdAt) {}
 public record JobLog(String id, String jobId, String requestId, String actorId, String factoryId, JobScope executionScope, String outcome, java.time.LocalDateTime createdAt) {}
 public record File(String id, String ownerId, String factoryId, String resource, String originalName, Long size, java.time.LocalDateTime createdAt) {}
 public record Session(String id, String userId, java.time.LocalDateTime expiresAt) {}
}
