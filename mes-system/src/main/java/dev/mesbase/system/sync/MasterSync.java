package dev.mesbase.system.sync;
import java.time.LocalDateTime;
/** SOURCE_PRODUCT_PORT: mes_master_sync; generic factory sync state, no external type enum. */
public record MasterSync(long id,String factoryId,String syncKey,String status,String runToken,
 LocalDateTime startedAt,LocalDateTime updatedAt,LocalDateTime finishedAt,LocalDateTime lastSuccessAt,
 LocalDateTime cursorTime,long processedCount,String errorMessage) {}
