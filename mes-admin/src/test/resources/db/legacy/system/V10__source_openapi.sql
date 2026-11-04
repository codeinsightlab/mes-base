-- SOURCE_PRODUCT_PORT: Source sql/openapi/20260930-open-api-platform.sql.
-- Generic DDL only. No Source clients, credentials, grants or MES route tables.
CREATE TABLE open_api_client (
  client_id bigint NOT NULL AUTO_INCREMENT COMMENT '内部稳定标识',
  app_key varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '公开身份标识',
  secret_hash varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'BCrypt摘要',
  client_name varchar(100) NOT NULL,
  status tinyint NOT NULL DEFAULT 0 COMMENT '0禁用 1启用',
  credential_version bigint NOT NULL DEFAULT 1 COMMENT '禁用或重置时递增',
  create_by varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by varchar(64) NOT NULL DEFAULT '',
  update_time datetime NULL,
  remark varchar(500) NULL,
  PRIMARY KEY (client_id),
  UNIQUE KEY uk_open_api_client_app_key (app_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OpenAPI外部client';

CREATE TABLE open_api_client_api (
  id bigint NOT NULL AUTO_INCREMENT,
  client_id bigint NOT NULL,
  api_code varchar(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '代码声明的稳定接口标识',
  create_by varchar(64) NOT NULL DEFAULT '',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_open_api_client_api (client_id, api_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='OpenAPI接口授权';

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,menu_type,perms,create_by,create_time)
SELECT 'OpenAPI应用',menu_id,16,'openApiClient','system/openApiClient/index','C','system:openApiClient:list','migration',CURRENT_TIMESTAMP
FROM sys_menu WHERE parent_id=0 AND path='system' AND menu_type='M'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE component='system/openApiClient/index');

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time)
SELECT '应用查询',menu_id,1,'#','F','system:openApiClient:query','migration',CURRENT_TIMESTAMP FROM sys_menu WHERE component='system/openApiClient/index';
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time)
SELECT '新建应用',menu_id,2,'#','F','system:openApiClient:add','migration',CURRENT_TIMESTAMP FROM sys_menu WHERE component='system/openApiClient/index';
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time)
SELECT '应用状态',menu_id,3,'#','F','system:openApiClient:edit','migration',CURRENT_TIMESTAMP FROM sys_menu WHERE component='system/openApiClient/index';
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time)
SELECT '密钥重置',menu_id,4,'#','F','system:openApiClient:reset','migration',CURRENT_TIMESTAMP FROM sys_menu WHERE component='system/openApiClient/index';
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,menu_type,perms,create_by,create_time)
SELECT '接口授权',menu_id,5,'#','F','system:openApiClient:authorize','migration',CURRENT_TIMESTAMP FROM sys_menu WHERE component='system/openApiClient/index';
-- Menu registration does not assign a role or grant any client API.
