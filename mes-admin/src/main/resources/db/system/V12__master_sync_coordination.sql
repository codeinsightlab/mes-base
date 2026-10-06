-- SOURCE_PRODUCT_PORT: multi-factory branch mes_master_sync. No external executors or source data.
CREATE TABLE mes_master_sync (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 factory_id VARCHAR(64) NOT NULL,
 sync_key VARCHAR(96) NOT NULL,
 status VARCHAR(24) NOT NULL DEFAULT 'IDLE',
 run_token VARCHAR(40) NULL,
 started_at DATETIME(6) NULL,
 updated_at DATETIME(6) NULL,
 finished_at DATETIME(6) NULL,
 last_success_at DATETIME(6) NULL,
 cursor_time DATETIME(6) NULL,
 processed_count BIGINT NOT NULL DEFAULT 0,
 error_message VARCHAR(1000) NULL,
 UNIQUE KEY uk_master_sync_factory_key(factory_id,sync_key),
 KEY idx_master_sync_status_activity(status,updated_at),
 CONSTRAINT fk_master_sync_factory FOREIGN KEY(factory_id) REFERENCES mf_factory(factory_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
