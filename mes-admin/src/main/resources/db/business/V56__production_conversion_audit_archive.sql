-- 保存本次内部生产模型转换前的原行，不参与当前业务查询、数量计算或状态判断。
-- 转换数据须另行核实目标、备份并执行受控事务；本版本只创建审计结构。
CREATE TABLE mes_production_conversion_archive (
    source_table VARCHAR(64) NOT NULL COMMENT '转换前原记录所属表名',
    source_id VARCHAR(64) NOT NULL COMMENT '转换前原记录主键原文',
    original_row JSON NOT NULL COMMENT '转换前包含全部原始字段的完整记录',
    conversion_key VARCHAR(64) NOT NULL COMMENT '本次受控转换的审计标识，不是运行期状态',
    conversion_reason VARCHAR(500) NOT NULL COMMENT '从退役模型转换到当前内部生产模型的原因',
    archived_at DATETIME(6) NOT NULL COMMENT '转换事务保存原始证据的时间',
    PRIMARY KEY (source_table, source_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='内部生产模型转换原始证据归档，仅供只读审计，不提供业务处理能力';
