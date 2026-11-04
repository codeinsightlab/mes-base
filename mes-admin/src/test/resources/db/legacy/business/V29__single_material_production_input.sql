-- 单材料生产扫码永久绑定；UTF-8字段经版本化可逆编码后按字节散列，主键保证并发唯一。
-- 不新增非主键唯一约束；散列碰撞时服务端核对完整编码并拒绝，不合并不同身份。
CREATE TABLE mes_production_scan_identity (
 id CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '规范生产扫码完整编码的SHA256主键',
 encoded_code VARCHAR(2048) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '版本化可逆生产二维码，保留品号批次原始字符串语义',
 execution_id BIGINT NOT NULL COMMENT '永久绑定的生产执行单元主键，终态不释放',
 created_at DATETIME(3) NOT NULL COMMENT '首次正式投产准备建立绑定的时间',
 PRIMARY KEY(id),
 KEY idx_scan_execution(execution_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='单材料生产永久扫码身份绑定';
CREATE TABLE mes_material_issue_input (
 id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '正式投产请求UUID主键，保证重放只建立一份领料事实',
 request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '冻结请求内容摘要，拒绝同请求身份承载不同内容',
 issue_id BIGINT NOT NULL COMMENT '本次新建的领料单主键，由原确认及作废链路管理',
 execution_id BIGINT NOT NULL COMMENT '贡献所属生产执行主键',
 input_kind VARCHAR(24) NOT NULL COMMENT 'PRODUCTION_INPUT正式投产或MATERIAL_SUPPLEMENT纯补料',
 output_qty DECIMAL(20,6) NOT NULL COMMENT '本次冻结预计产出，可信确认后贡献，补料为零',
 source_mode VARCHAR(16) NOT NULL COMMENT '明确来源模式，LOCAL_ONLY本地或CONNECTED外部ERP',
 calculation_json LONGTEXT NOT NULL COMMENT '冻结的用料依据与库存来源，不包含凭证',
 stock_id BIGINT NULL COMMENT '独立MES模式实际扣减的本地批次库存记录主键',
 confirmed_at DATETIME(3) NULL COMMENT '首次可信领料确认时间，空值不贡献投产额度',
 stock_applied BOOLEAN NOT NULL DEFAULT FALSE COMMENT '本地库存扣减已随领料确认原子完成',
 created_at DATETIME(3) NOT NULL COMMENT '正式投产请求首次准备时间',
 PRIMARY KEY(id),
 KEY idx_input_execution(execution_id,issue_id),
 KEY idx_input_issue(issue_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='与领料单紧密关联的冻结投产确认事实，不是报工流水';
-- 旧库存不猜测批次；空批次记录不得作为扫码投产的批次库存依据。
ALTER TABLE wm_inventory
 ADD COLUMN batch_no VARCHAR(128) COLLATE utf8mb4_0900_bin NULL COMMENT '实际材料批次，独立MES扫码投产需明确填写',
 ADD COLUMN bin_code VARCHAR(64) COLLATE utf8mb4_0900_bin NULL COMMENT '实际来源库位编码，未管理库位时为空',
 MODIFY quantity DECIMAL(20,6) NOT NULL COMMENT '本地库存数量，正式本地领料原子扣减',
 MODIFY available_quantity DECIMAL(20,6) NULL COMMENT '可用库存数量，不为空时随正式本地领料扣减';
