-- 审核请求及生产领料草稿沿用同一原始UUID；跨工单并发不能重复占用请求身份。
-- 唯一索引允许无请求身份的普通草稿保留NULL，不回填或改写历史单据。
-- 执行前须核实非空post_request_id无重复，发现冲突停止，不删除或合并事实。
ALTER TABLE wm_stock_document
    ADD UNIQUE INDEX uk_stock_document_request_identity (post_request_id);
