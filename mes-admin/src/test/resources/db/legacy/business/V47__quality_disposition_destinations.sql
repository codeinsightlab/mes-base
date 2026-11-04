-- 不良处理仅新增“返回原申报节点”和“转指定仓库”事实；旧事件及其数量口径保留。
ALTER TABLE mes_quality_treatment
 MODIFY COLUMN action_type VARCHAR(32) NOT NULL COMMENT '不可变质量事件，含旧处理事件及RETURN_NODE返回申报节点、TRANSFER_WAREHOUSE正式转仓',
 ADD COLUMN target_warehouse_id BIGINT NULL COMMENT '转仓时确认的目标仓库，非转仓事件为空',
 ADD COLUMN stock_document_id BIGINT NULL COMMENT '转仓产生的正式入库单主键，非转仓或未产生入库单为空',
 ADD COLUMN transfer_qty DECIMAL(20,6) NOT NULL DEFAULT 0 COMMENT '本次正式转仓数量，不等于报废数量',
 ADD KEY idx_quality_treatment_stock_document(stock_document_id);
ALTER TABLE mes_production_report
 ADD COLUMN source_quality_event_id VARCHAR(36) NULL COMMENT '返工回申报节点引起的报工回退所关联的不可变质量事件',
 ADD KEY idx_report_quality_event(source_quality_event_id,id);
