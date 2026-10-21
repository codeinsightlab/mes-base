-- 统一入库单：生产来源直接关联执行单元，不再创建交接中间事实。
-- 历史交接、仓储接收及库存动作原样保留，新列不猜测或回填旧单据来源。
ALTER TABLE wm_stock_document
 ADD COLUMN source_execution_id BIGINT NULL COMMENT '生产来源执行单元主键，非生产入库为空；创建后固定',
 ADD KEY idx_stock_document_execution(source_execution_id,status,id);
