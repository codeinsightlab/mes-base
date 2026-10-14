-- 本地作废没有外部冲销，纠正本轮已确认本地成功的影响标记；历史未知或原外部来源不触及。
UPDATE mes_material_issue
SET external_effect='NONE'
WHERE status='VOIDED' AND source_execution_mode='LOCAL_ONLY'
  AND void_execution_mode='LOCAL_ONLY' AND void_result_status='SUCCEEDED'
  AND void_operation_id IS NOT NULL AND external_effect='REVERSED';
