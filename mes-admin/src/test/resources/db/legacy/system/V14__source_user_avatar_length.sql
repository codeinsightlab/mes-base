-- SOURCE_PRODUCT_PORT: verified mes_master.sys_user metadata uses VARCHAR(150).
-- Widen only; preserves existing Base records and avoids truncating imported avatar references.
ALTER TABLE sys_user MODIFY avatar VARCHAR(150) DEFAULT '' COMMENT '头像地址';
