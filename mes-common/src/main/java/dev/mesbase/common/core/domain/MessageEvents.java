package dev.mesbase.common.core.domain;
/** Server-owned inbox invalidation. Database content remains authoritative and recipient-private. */
public interface MessageEvents { void changed(long recipientId); }
