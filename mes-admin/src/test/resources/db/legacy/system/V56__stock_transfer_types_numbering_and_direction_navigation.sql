-- 仅给缺失的调拨类型补充同业务key编码规则；不覆盖既有规则、组成、计数和历史单号。
-- 候选前缀在新增前检查占用，冲突时停止升级并明确处理配置。
DELIMITER $$
DROP PROCEDURE IF EXISTS mes_v56_seed_stock_transfer_rules$$
CREATE PROCEDURE mes_v56_seed_stock_transfer_rules()
BEGIN
    DECLARE bad_rows BIGINT DEFAULT 0;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    SELECT COUNT(*) INTO bad_rows FROM sys_menu
    WHERE menu_type='C' AND component IN ('wm/stockDocuments/index','wm/outboundDocuments/index');
    IF bad_rows<>1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Stock document navigation is missing or ambiguous';
    END IF;
    SELECT COUNT(*) INTO bad_rows FROM (
        SELECT component FROM sys_menu WHERE menu_type='C'
        AND component IN ('wm/inboundDocuments/index','wm/stockTransfers/index')
        GROUP BY component HAVING COUNT(*)>1
    ) ambiguous_navigation;
    IF bad_rows<>0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Inbound or transfer navigation is ambiguous';
    END IF;

    SELECT COUNT(*) INTO bad_rows
    FROM (
        SELECT 'TRANSFER_RECEIPT' rule_code,'STOCK_IN' rule_type,'TRI' prefix UNION ALL
        SELECT 'TRANSFER_ISSUE','STOCK_OUT','TRO'
    ) seed
    JOIN sys_auto_code_rule r ON r.rule_code=seed.rule_code
    WHERE r.rule_type<>seed.rule_type OR r.rule_type IS NULL;
    IF bad_rows<>0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A stock document type key already exists as a numbering rule with a different direction category';
    END IF;

    SELECT COUNT(*) INTO bad_rows
    FROM (
        SELECT 'TRANSFER_RECEIPT' rule_code,'STOCK_IN' rule_type,'TRI' prefix UNION ALL
        SELECT 'TRANSFER_ISSUE','STOCK_OUT','TRO'
    ) seed
    JOIN sys_auto_code_part p ON p.part_index=1 AND p.part_type='FIXCHAR'
                             AND p.fix_character=seed.prefix
    JOIN sys_auto_code_rule r ON r.rule_id=p.rule_id AND r.enable_flag='Y'
    WHERE r.rule_code<>seed.rule_code
      AND NOT EXISTS (SELECT 1 FROM sys_auto_code_rule existing_rule WHERE existing_rule.rule_code=seed.rule_code);
    IF bad_rows<>0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A proposed stock document numbering prefix is already used by an active rule; inspect current rule configuration and choose a conflict-free prefix';
    END IF;

    SELECT COUNT(*) INTO bad_rows
    FROM (
        SELECT 'TRANSFER_RECEIPT' rule_code UNION ALL SELECT 'TRANSFER_ISSUE'
    ) seed
    JOIN sys_auto_code_rule r ON r.rule_name=seed.rule_code AND r.rule_code<>seed.rule_code;
    IF bad_rows<>0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A stock document type rule display name already exists; resolve the existing name before seeding';
    END IF;

    START TRANSACTION;

    INSERT INTO sys_auto_code_rule
        (rule_code,rule_type,factory_id,rule_name,rule_desc,max_length,is_padded,padded_char,padded_method,
         enable_flag,remark,attr1,create_by,create_time)
    SELECT seed.rule_code,seed.rule_type,NULL,seed.rule_code,
           CONCAT(seed.rule_code,' document number'),64,'N',NULL,'L','Y','',
           'V56_STOCK_TRANSFER_RULE_SEED','migration',CURRENT_TIMESTAMP
    FROM (
        SELECT 'TRANSFER_RECEIPT' rule_code,'STOCK_IN' rule_type,'TRI' prefix UNION ALL
        SELECT 'TRANSFER_ISSUE','STOCK_OUT','TRO'
    ) seed
    WHERE NOT EXISTS (SELECT 1 FROM sys_auto_code_rule r WHERE r.rule_code=seed.rule_code);

    INSERT INTO sys_auto_code_part
        (rule_id,part_index,part_type,part_code,part_name,part_length,fix_character,create_by,create_time)
    SELECT r.rule_id,1,'FIXCHAR','PREFIX','prefix',CHAR_LENGTH(seed.prefix),seed.prefix,'migration',CURRENT_TIMESTAMP
    FROM (
        SELECT 'TRANSFER_RECEIPT' rule_code,'TRI' prefix UNION ALL SELECT 'TRANSFER_ISSUE','TRO'
    ) seed JOIN sys_auto_code_rule r ON r.rule_code=seed.rule_code
    WHERE r.attr1='V56_STOCK_TRANSFER_RULE_SEED'
      AND NOT EXISTS (SELECT 1 FROM sys_auto_code_part p WHERE p.rule_id=r.rule_id);

    INSERT INTO sys_auto_code_part
        (rule_id,part_index,part_type,part_code,part_name,part_length,date_format,create_by,create_time)
    SELECT r.rule_id,2,'NOWDATE','DATE','date',8,'yyyyMMdd','migration',CURRENT_TIMESTAMP
    FROM sys_auto_code_rule r
    WHERE r.attr1='V56_STOCK_TRANSFER_RULE_SEED'
      AND (SELECT COUNT(*) FROM sys_auto_code_part p WHERE p.rule_id=r.rule_id)=1;

    INSERT INTO sys_auto_code_part
        (rule_id,part_index,part_type,part_code,part_name,part_length,seria_start_no,seria_step,
         cycle_flag,cycle_method,create_by,create_time)
    SELECT r.rule_id,3,'SERIALNO','SERIAL','daily serial',3,1,1,'Y','DAY','migration',CURRENT_TIMESTAMP
    FROM sys_auto_code_rule r
    WHERE r.attr1='V56_STOCK_TRANSFER_RULE_SEED'
      AND (SELECT COUNT(*) FROM sys_auto_code_part p WHERE p.rule_id=r.rule_id)=2;

    UPDATE sys_auto_code_rule SET attr1=NULL WHERE attr1='V56_STOCK_TRANSFER_RULE_SEED';
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,is_default,status,create_by,create_time)
SELECT 100,'调拨入库','TRANSFER_RECEIPT','stock_document_in_type','N','0','migration',CURRENT_TIMESTAMP
WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='stock_document_in_type' AND dict_value='TRANSFER_RECEIPT');
INSERT INTO sys_dict_data(dict_sort,dict_label,dict_value,dict_type,is_default,status,create_by,create_time)
SELECT 30,'调拨出库','TRANSFER_ISSUE','stock_document_out_type','N','0','migration',CURRENT_TIMESTAMP
WHERE NOT EXISTS(SELECT 1 FROM sys_dict_data WHERE dict_type='stock_document_out_type' AND dict_value='TRANSFER_ISSUE');

-- 保留原菜单主键及角色、功能授权，受限领料用户继续通过原授权进入出库。
UPDATE sys_menu SET menu_name='出库单',path='outboundDocuments',component='wm/outboundDocuments/index'
WHERE component='wm/stockDocuments/index' AND menu_type='C';
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT '入库单',p.parent_id,p.order_num+1,'inboundDocuments','wm/inboundDocuments/index','1','0','C','0','0',NULL,'documentation','migration',CURRENT_TIMESTAMP
FROM sys_menu p WHERE p.component='wm/outboundDocuments/index' AND p.menu_type='C'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE component='wm/inboundDocuments/index');
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT '库存调拨',p.parent_id,p.order_num+2,'stockTransfers','wm/stockTransfers/index','1','0','C','0','0',NULL,'documentation','migration',CURRENT_TIMESTAMP
FROM sys_menu p WHERE p.component='wm/outboundDocuments/index' AND p.menu_type='C'
AND NOT EXISTS(SELECT 1 FROM sys_menu WHERE component='wm/stockTransfers/index');
-- 只向已经持有通用库存单列表权限的角色补入库和调拨导航，不扩大领料专用授权。
INSERT INTO sys_role_menu(role_id,menu_id)
SELECT DISTINCT granted.role_id,target.menu_id
FROM sys_role_menu granted JOIN sys_menu capability ON capability.menu_id=granted.menu_id
CROSS JOIN sys_menu target
WHERE capability.perms='business:wm:stock:document:list'
AND target.component IN ('wm/inboundDocuments/index','wm/stockTransfers/index')
AND NOT EXISTS(SELECT 1 FROM sys_role_menu existing WHERE existing.role_id=granted.role_id AND existing.menu_id=target.menu_id);

    COMMIT;
END$$
CALL mes_v56_seed_stock_transfer_rules()$$
DROP PROCEDURE mes_v56_seed_stock_transfer_rules$$
DELIMITER ;
