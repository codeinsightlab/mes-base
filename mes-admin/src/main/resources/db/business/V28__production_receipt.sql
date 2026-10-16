-- 请求 UUID 直接作为事实主键；不新增非主键唯一约束或重复的请求键缓存。
CREATE TABLE mes_production_receipt (
 id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '交接事实主键，等于小写请求UUID，保证请求原子幂等',
 receipt_no VARCHAR(64) NOT NULL COMMENT '交接记录编号，由类型及事实UUID生成',
 execution_id BIGINT NOT NULL COMMENT '生产执行单元主键，引用当前业务库执行',
 product_lot_code VARCHAR(100) NULL COMMENT '实际产出产品批次，可空且不从原材料批次推导',
 warehouse_id BIGINT NOT NULL COMMENT '目标交接仓库主键',
 record_type VARCHAR(20) NOT NULL COMMENT '事实类型，RECEIPT正向交接或REVERSAL冲销',
 quantity_delta DECIMAL(20,6) NOT NULL COMMENT '交接数量变化，正向为正，冲销为负',
 reference_receipt_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL COMMENT '冲销引用的原始正向交接事实主键',
 operator_user_id BIGINT NOT NULL COMMENT '实际交接操作人的系统用户主键，冲销继承原始人员',
 recorded_by BIGINT NOT NULL COMMENT '本次录入人的可信登录用户主键',
 occurred_at DATETIME(3) NOT NULL COMMENT '实际发生时间，冲销继承原始时间',
 create_time DATETIME(3) NOT NULL COMMENT '本次事实创建时间，由服务端生成',
 reason VARCHAR(500) NULL COMMENT '冲销原因，正向交接可空',
 PRIMARY KEY(id),
 KEY idx_receipt_execution_created(execution_id,create_time,id),
 KEY idx_receipt_reference(reference_receipt_id),
 KEY idx_receipt_warehouse_time(warehouse_id,occurred_at),
 CONSTRAINT ck_receipt_kind CHECK (
  (record_type='RECEIPT' AND quantity_delta>0 AND reference_receipt_id IS NULL)
  OR (record_type='REVERSAL' AND quantity_delta<0 AND reference_receipt_id IS NOT NULL AND reason IS NOT NULL AND CHAR_LENGTH(TRIM(reason))>0)),
 CONSTRAINT ck_receipt_request CHECK (id REGEXP '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='不可变生产交接事实，不代表库存入账';
CREATE TRIGGER mes_receipt_no_update BEFORE UPDATE ON mes_production_receipt
 FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Production receipt ledger is immutable';
CREATE TRIGGER mes_receipt_no_delete BEFORE DELETE ON mes_production_receipt
 FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Production receipt ledger is immutable';
-- 即使上层删除入口变化，交接历史的执行引用仍不可删除。
DELIMITER $$
CREATE TRIGGER mes_receipt_execution_guard BEFORE DELETE ON mes_production_execution
 FOR EACH ROW BEGIN
 IF EXISTS (SELECT 1 FROM mes_production_receipt WHERE execution_id=OLD.id) THEN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Execution has immutable production receipt history';
 END IF;
 END$$
DELIMITER ;
