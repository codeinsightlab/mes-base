-- Clean Platform schema derived from upstream RuoYi table responsibilities. NO seed data.
-- PostgreSQL / H2 acceptance dialect; Factory roles are explicit, never factory_id=0.

CREATE TABLE sys_user (
  id VARCHAR(36) PRIMARY KEY,
  username VARCHAR(64) NOT NULL UNIQUE,
  display_name VARCHAR(100) NOT NULL,
  password_hash VARCHAR(100) NOT NULL,
  status VARCHAR(16) NOT NULL,
  created_at TIMESTAMP NOT NULL
);

CREATE TABLE sys_role (
  id VARCHAR(36) PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  kind VARCHAR(16) NOT NULL,
  factory_id VARCHAR(64),
  status VARCHAR(16) NOT NULL ,
  CHECK ((kind='PLATFORM' AND factory_id IS NULL) OR (kind='FACTORY' AND factory_id IS NOT NULL))
);

CREATE TABLE sys_menu (
  id VARCHAR(36) PRIMARY KEY,
  parent_id VARCHAR(36) REFERENCES sys_menu(id),
  name VARCHAR(100) NOT NULL,
  path VARCHAR(160) NOT NULL,
  permission VARCHAR(100) NOT NULL,
  sort_order INTEGER NOT NULL,
  kind VARCHAR(16) NOT NULL
);

CREATE TABLE sys_dict_type (
  id VARCHAR(36) PRIMARY KEY,
  code VARCHAR(64) NOT NULL UNIQUE,
  name VARCHAR(100) NOT NULL
);

CREATE TABLE sys_dict_data (
  id VARCHAR(36) PRIMARY KEY,
  type_id VARCHAR(36) NOT NULL REFERENCES sys_dict_type(id),
  dict_value VARCHAR(100) NOT NULL,
  label VARCHAR(100) NOT NULL,
  sort_order INTEGER NOT NULL ,
  UNIQUE(type_id,dict_value)
);

CREATE TABLE sys_config (
  id VARCHAR(36) PRIMARY KEY,
  config_key VARCHAR(100) NOT NULL,
  config_value VARCHAR(500) NOT NULL,
  kind VARCHAR(16) NOT NULL,
  factory_id VARCHAR(64) ,
  CHECK ((kind='GLOBAL' AND factory_id IS NULL) OR (kind='FACTORY' AND factory_id IS NOT NULL))
);

CREATE TABLE sys_job (
  id VARCHAR(36) PRIMARY KEY,
  name VARCHAR(100) NOT NULL,
  handler VARCHAR(64) NOT NULL,
  execution_scope VARCHAR(16) NOT NULL,
  factory_id VARCHAR(64),
  cron VARCHAR(100) NOT NULL,
  status VARCHAR(16) NOT NULL,
  running_token VARCHAR(36),
  started_at TIMESTAMP ,
  CHECK ((execution_scope='PLATFORM' AND factory_id IS NULL) OR (execution_scope='FACTORY' AND factory_id IS NOT NULL))
);

CREATE TABLE sys_oper_log (
  id VARCHAR(36) PRIMARY KEY,
  request_id VARCHAR(36) NOT NULL,
  actor_id VARCHAR(64) NOT NULL,
  factory_id VARCHAR(64),
  execution_scope VARCHAR(16) NOT NULL,
  action VARCHAR(100) NOT NULL,
  outcome VARCHAR(32) NOT NULL,
  created_at TIMESTAMP NOT NULL
);

CREATE TABLE sys_logininfor (
  id VARCHAR(36) PRIMARY KEY,
  request_id VARCHAR(36) NOT NULL,
  actor_id VARCHAR(64) NOT NULL,
  factory_id VARCHAR(64),
  execution_scope VARCHAR(16) NOT NULL,
  outcome VARCHAR(32) NOT NULL,
  created_at TIMESTAMP NOT NULL
);

CREATE TABLE sys_job_log (
  id VARCHAR(36) PRIMARY KEY,
  job_id VARCHAR(36) NOT NULL,
  request_id VARCHAR(36) NOT NULL,
  actor_id VARCHAR(64) NOT NULL,
  factory_id VARCHAR(64),
  execution_scope VARCHAR(16) NOT NULL,
  outcome VARCHAR(32) NOT NULL,
  created_at TIMESTAMP NOT NULL
);

CREATE TABLE sys_file (
  id VARCHAR(36) PRIMARY KEY,
  owner_id VARCHAR(36) NOT NULL REFERENCES sys_user(id),
  factory_id VARCHAR(64),
  resource VARCHAR(128) NOT NULL,
  original_name VARCHAR(160) NOT NULL,
  size BIGINT NOT NULL,
  created_at TIMESTAMP NOT NULL
);

CREATE TABLE sys_session (
  id VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(36) NOT NULL REFERENCES sys_user(id),
  expires_at TIMESTAMP NOT NULL
);

CREATE TABLE sys_user_role(user_id VARCHAR(36) NOT NULL REFERENCES sys_user(id),role_id VARCHAR(36) NOT NULL REFERENCES sys_role(id),PRIMARY KEY(user_id,role_id));

CREATE TABLE sys_role_menu(role_id VARCHAR(36) NOT NULL REFERENCES sys_role(id),menu_id VARCHAR(36) NOT NULL REFERENCES sys_menu(id),PRIMARY KEY(role_id,menu_id));

CREATE INDEX session_expiry ON sys_session(expires_at);

CREATE INDEX role_factory ON sys_role(factory_id,status);

ALTER TABLE sys_config ADD scope_key VARCHAR(80) NOT NULL DEFAULT 'PLATFORM';

CREATE UNIQUE INDEX config_scope_key ON sys_config(scope_key,config_key);

-- Internal cache version, no business/customer seed values.
CREATE TABLE platform_catalog_revision(id INTEGER PRIMARY KEY CHECK(id=1), revision BIGINT NOT NULL);
INSERT INTO platform_catalog_revision(id,revision) VALUES(1,0);
