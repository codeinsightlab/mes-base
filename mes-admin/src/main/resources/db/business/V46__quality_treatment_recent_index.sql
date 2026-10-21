-- 首页最近处置流水按服务端登记时间排序；UUID 主键不表达时间顺序。
-- 采用升序联合索引，由 MySQL 倒序扫描取得最近记录，兼顾同一时间下的稳定排序。
CREATE INDEX idx_quality_treatment_recorded ON mes_quality_treatment(recorded_at, id);
