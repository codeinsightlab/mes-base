# Automatic document numbering

Automatic numbering is a platform service backed by the center database. `SysAutoCodeGenerationServiceImpl.getDocNo(type)` is the common entry point; `type` is the exact, immutable `ruleCode` configured in the Auto Code Rules page. Each call allocates a new number. It is not an idempotency key: a retried business request must first return its previously stored business result.

## Rule configuration

Rules and their ordered components are managed from **System → Auto Code Rules**. To make a rule usable through `getDocNo(type)`, configure exactly three components in this order:

1. `FIXCHAR` for the configured prefix, such as `SO` or `RK`.
2. `NOWDATE` with `yyyyMMdd`.
3. `SERIALNO` with start `1`, step `1`, cycle enabled with `DAY`, and component width at least `3`.

Do not enable whole-number padding for these rules. A configuration that does not meet this contract is rejected by `getDocNo`; the service does not substitute a default rule, prefix, or identifier. Existing non-target configuration remains stored as-is.

The page preview and formal generation both enforce this contract and use the same configuration parser and formatter. Preview does not write the counter; formal generation does. Existing non-target rules and components remain stored as configured, but the `getDocNo` preview/generation actions reject them with a configuration error. A resulting number is the configured prefix followed by the server date and the rule's daily sequence, for example `SO20261027001`. The minimum sequence width is three digits: after `999`, values continue as `1000`, `1001`, and so on. The final result must still fit the rule's configured maximum length and the database's 64-character result field.

## Counter and cache behavior

Configuration is cached as a complete, immutable platform snapshot indexed by rule code. The snapshot includes all rules and their components; it is not built from a paged HTTP result. Component rows are batch-loaded. Rule and component mutations attempt to invalidate the shared snapshot only after the center transaction commits. A Redis version token prevents an older in-flight load from filling the cache after successful invalidation. If Redis is unavailable, the service loads current configuration from the center database and does not treat an unavailable cache as proof that old configuration is current.

Counters are persisted independently by rule identity and cycle key (`rule_id` plus `DAY:yyyyMMdd` for daily rules). The center database rule row lock serializes allocation across application instances, including the first counter row creation. Once inside that transaction, the rule and its components are re-read with database locking reads; a stale cache entry or failed Redis invalidation therefore cannot authorize a disabled or outdated configuration. If invalidation fails, an old snapshot can delay visibility of a newly added rule until expiry, while existing-rule validation still uses the locked database rows. The cached rules do not contain mutable current sequence state, and a counter update does not invalidate configuration. The service uses one server-side `Asia/Shanghai` time value for the date component, daily cycle, and generation timestamp.

The center number allocation and a later Factory business save are separate transactions. A number can be unused if the business save fails; this service does not promise a cross-database transaction or business-request idempotency.
