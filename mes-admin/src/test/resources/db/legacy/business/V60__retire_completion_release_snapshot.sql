-- Independent quality release is retired. Preserve every existing snapshot for read-only audit.
-- Current receipt-driven completion records have no quality release quantity.
ALTER TABLE mes_execution_completion
    MODIFY COLUMN released_qty DECIMAL(20,6) NULL COMMENT '退役独立放行数量快照，仅保留既有事实审计；当前收尾为空';
