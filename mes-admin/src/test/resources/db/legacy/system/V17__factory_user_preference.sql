-- SOURCE_PRODUCT_PORT: mf_user_preference from Source master-init; no user/Factory seed.
-- Historical preference may outlive a Factory. Missing/disabled targets are revalidated on use.
CREATE TABLE mf_user_preference (
    user_id BIGINT NOT NULL,
    last_factory_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
