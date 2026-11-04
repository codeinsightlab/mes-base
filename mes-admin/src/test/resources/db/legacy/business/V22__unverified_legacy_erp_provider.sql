-- V20旧结构没有供应商字段，原连接标识不能证明供应商；仅纠正迁移行，保留已由业务明确记录的来源。
UPDATE mes_material_issue_erp_extra
 SET provider=NULL
 WHERE source_execution_mode='CONNECTED'
   AND provider=external_binding_key
   AND last_operation IS NULL AND checked_at IS NULL;
