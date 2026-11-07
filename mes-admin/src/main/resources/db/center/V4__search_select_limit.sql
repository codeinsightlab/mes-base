-- Shared searchable selectors; editable through the existing system-config entry.
-- Preserve an existing administrator-selected value.
INSERT INTO sys_config(config_name,config_key,config_value,config_type,create_by,create_time,remark)
SELECT '检索选择框候选数量','sys.ui.searchSelect.limit','20','Y','migration',CURRENT_TIMESTAMP,
       '检索选择框单次最多返回的候选数量，范围1至100；修改后下一次检索生效，不提供加载更多。'
WHERE NOT EXISTS(SELECT 1 FROM sys_config WHERE config_key='sys.ui.searchSelect.limit');
