-- SOURCE_PRODUCT_PORT: feat/multi-factory-isolation scripts/mf-upgrade/sql/001-master-init.sql.
-- Core FactoryId remains a string. enterprise_id/revision retain Base identity and concurrency contracts.
-- External plant identifiers and source records are excluded.
CREATE TABLE mf_datasource (
 datasource_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 datasource_name VARCHAR(64) NOT NULL,
 db_host VARCHAR(255) NOT NULL,
 db_port INT NOT NULL DEFAULT 3306,
 db_name VARCHAR(64) NOT NULL,
 db_username VARCHAR(128) NOT NULL,
 password_cipher VARCHAR(2048) NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'DISABLED',
 revision BIGINT NOT NULL DEFAULT 1,
 create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 UNIQUE KEY uk_datasource_database(db_host,db_port,db_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE mf_factory (
 factory_id VARCHAR(64) NOT NULL PRIMARY KEY,
 enterprise_id VARCHAR(64) NOT NULL,
 factory_code VARCHAR(32) NOT NULL,
 factory_name VARCHAR(64) NOT NULL,
 datasource_id BIGINT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
 revision BIGINT NOT NULL DEFAULT 1,
 UNIQUE KEY uk_factory_code(factory_code),
 UNIQUE KEY uk_factory_datasource(datasource_id),
 CONSTRAINT fk_factory_datasource FOREIGN KEY(datasource_id) REFERENCES mf_datasource(datasource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
