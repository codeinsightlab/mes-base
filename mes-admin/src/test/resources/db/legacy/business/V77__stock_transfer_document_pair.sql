-- 本厂LOCAL同步调拨复用两张正式出入库单，由业务事务维护双向关联。
-- 关联仅允许空值到对方单据的一次写入；复用既有创建请求唯一约束，不新增业务唯一或外键约束。
ALTER TABLE wm_stock_document
 ADD COLUMN paired_document_id BIGINT NULL COMMENT '同步LOCAL调拨的另一方向正式单据ID',
 ADD KEY idx_stock_document_pair(paired_document_id);
