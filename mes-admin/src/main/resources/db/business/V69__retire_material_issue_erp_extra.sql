-- Preserve trusted issue origin and the proven LOCAL cancellation outcome.
-- ERP query/reconciliation/provider/message data is retired with the extension.
ALTER TABLE mes_material_issue
    ADD COLUMN source_mode VARCHAR(20) NULL COMMENT '领料来源；未知保持NULL',
    ADD COLUMN void_result_status VARCHAR(20) NULL COMMENT '已发生的领料撤销结果';

UPDATE mes_material_issue issue_row
LEFT JOIN mes_material_issue_erp_extra extra_row ON extra_row.id = issue_row.id
SET issue_row.source_mode = extra_row.source_execution_mode,
    issue_row.void_result_status = CASE
        WHEN extra_row.source_execution_mode = 'LOCAL_ONLY'
         AND extra_row.void_execution_mode = 'LOCAL_ONLY'
        THEN extra_row.void_result_status
        ELSE NULL
    END;

DROP TABLE mes_material_issue_erp_extra;
