-- 撤回提前固化的质量字典；V1 保留为已应用历史，不修改其校验和。
-- 执行前须确认两表为空且没有其他正式业务引用；质量领域等待生产执行模型稳定后整体设计。
DROP TABLE IF EXISTS qc_index;
DROP TABLE IF EXISTS qc_defect;
