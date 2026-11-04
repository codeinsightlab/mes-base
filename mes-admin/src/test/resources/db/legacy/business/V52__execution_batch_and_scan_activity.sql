-- 执行批次是生产事实字段；扫码行为独立记录，不参与生产定位或库存判定。
ALTER TABLE mes_production_execution
  ADD COLUMN batch_no VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL COMMENT '产出批次原文，未确定批次的计划执行可为空' AFTER product_id,
  ADD INDEX idx_execution_order_product_batch (workorder_id, product_id, batch_no);

-- 只搬运已有正式绑定中的无歧义批次；不改状态、数量、历史业务事实或旧绑定证据。
UPDATE mes_production_execution e
JOIN (
  SELECT execution_id, MIN(material_lot_code) AS batch_no
  FROM mes_production_scan_identity
  WHERE material_lot_code IS NOT NULL AND CHAR_LENGTH(material_lot_code)>0
  GROUP BY execution_id
  HAVING COUNT(*)=1
) b ON b.execution_id=e.id
SET e.batch_no=b.batch_no
WHERE e.batch_no IS NULL;

CREATE TABLE mes_production_scan_activity (
  id CHAR(36) NOT NULL COMMENT '每次执行定位尝试的服务端事件编号',
  scan_type VARCHAR(32) NOT NULL COMMENT '扫码业务场景，报工、不良申报或执行查询',
  input_method VARCHAR(16) NOT NULL COMMENT '浏览器申报的输入方式，文本或图片，不作为可信业务事实',
  user_id BIGINT NOT NULL COMMENT '服务端认证的操作用户编号',
  execution_id BIGINT NULL COMMENT '成功定位的执行编号，失败时为空，仅供行为分析',
  outcome VARCHAR(16) NOT NULL COMMENT '服务端定位结果，成功或失败',
  error_code VARCHAR(80) NULL COMMENT '失败的稳定安全错误码，不保存原始异常或码值',
  duration_ms BIGINT NOT NULL COMMENT '服务端定位耗时毫秒，不包含客户端识别及网络耗时',
  occurred_at DATETIME(6) NOT NULL COMMENT '服务端记录的扫码定位时间',
  request_id VARCHAR(128) NULL COMMENT '服务端请求追踪标识，不是生产提交幂等键',
  PRIMARY KEY (id),
  KEY idx_scan_activity_user_time (user_id, occurred_at),
  KEY idx_scan_activity_type_time (scan_type, occurred_at),
  KEY idx_scan_activity_execution_time (execution_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户扫码定位行为，仅供分析审计，不是生产身份或业务准入依据';
