-- Seed one exact business-key rule per supported StockDocumentType only when the rule is absent.
-- Prefixes are provisional migration candidates. Runtime preflight refuses any active prefix collision;
-- existing rules, parts, names, counters and historical document numbers are never edited.
DELIMITER $$
DROP PROCEDURE IF EXISTS mes_v55_seed_stock_document_number_rules$$
CREATE PROCEDURE mes_v55_seed_stock_document_number_rules()
BEGIN
    DECLARE bad_rows BIGINT DEFAULT 0;

    SELECT COUNT(*) INTO bad_rows
    FROM (
        SELECT 'PURCHASE_RECEIPT' rule_code,'STOCK_IN' rule_type,'PR' prefix UNION ALL
        SELECT 'PRODUCTION_RECEIPT','STOCK_IN','PDR' UNION ALL
        SELECT 'OUTSOURCE_RECEIPT','STOCK_IN','OSR' UNION ALL
        SELECT 'MATERIAL_RETURN_RECEIPT','STOCK_IN','MRR' UNION ALL
        SELECT 'REWORK_RECEIPT','STOCK_IN','RWR' UNION ALL
        SELECT 'SALES_RETURN_RECEIPT','STOCK_IN','SRR' UNION ALL
        SELECT 'DEFECT_RECEIPT','STOCK_IN','DFR' UNION ALL
        SELECT 'SCRAP_RECEIPT','STOCK_IN','SCR' UNION ALL
        SELECT 'OTHER_RECEIPT','STOCK_IN','OIR' UNION ALL
        SELECT 'MATERIAL_ISSUE','STOCK_OUT','MI' UNION ALL
        SELECT 'OTHER_ISSUE','STOCK_OUT','OIS'
    ) seed
    JOIN sys_auto_code_rule r ON r.rule_code=seed.rule_code
    WHERE r.rule_type<>seed.rule_type OR r.rule_type IS NULL;
    IF bad_rows<>0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A stock document type key already exists as a numbering rule with a different direction category';
    END IF;

    SELECT COUNT(*) INTO bad_rows
    FROM (
        SELECT 'PURCHASE_RECEIPT' rule_code,'STOCK_IN' rule_type,'PR' prefix UNION ALL
        SELECT 'PRODUCTION_RECEIPT','STOCK_IN','PDR' UNION ALL
        SELECT 'OUTSOURCE_RECEIPT','STOCK_IN','OSR' UNION ALL
        SELECT 'MATERIAL_RETURN_RECEIPT','STOCK_IN','MRR' UNION ALL
        SELECT 'REWORK_RECEIPT','STOCK_IN','RWR' UNION ALL
        SELECT 'SALES_RETURN_RECEIPT','STOCK_IN','SRR' UNION ALL
        SELECT 'DEFECT_RECEIPT','STOCK_IN','DFR' UNION ALL
        SELECT 'SCRAP_RECEIPT','STOCK_IN','SCR' UNION ALL
        SELECT 'OTHER_RECEIPT','STOCK_IN','OIR' UNION ALL
        SELECT 'MATERIAL_ISSUE','STOCK_OUT','MI' UNION ALL
        SELECT 'OTHER_ISSUE','STOCK_OUT','OIS'
    ) seed
    JOIN sys_auto_code_part p ON p.part_index=1 AND p.part_type='FIXCHAR'
                             AND p.fix_character=seed.prefix
    JOIN sys_auto_code_rule r ON r.rule_id=p.rule_id AND r.enable_flag='Y'
    WHERE r.rule_code<>seed.rule_code;
    IF bad_rows<>0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A proposed stock document numbering prefix is already used by an active rule; inspect current rule configuration and choose a conflict-free prefix';
    END IF;

    SELECT COUNT(*) INTO bad_rows
    FROM (
        SELECT 'PURCHASE_RECEIPT' rule_code UNION ALL SELECT 'PRODUCTION_RECEIPT' UNION ALL
        SELECT 'OUTSOURCE_RECEIPT' UNION ALL SELECT 'MATERIAL_RETURN_RECEIPT' UNION ALL
        SELECT 'REWORK_RECEIPT' UNION ALL SELECT 'SALES_RETURN_RECEIPT' UNION ALL
        SELECT 'DEFECT_RECEIPT' UNION ALL SELECT 'SCRAP_RECEIPT' UNION ALL
        SELECT 'OTHER_RECEIPT' UNION ALL SELECT 'MATERIAL_ISSUE' UNION ALL SELECT 'OTHER_ISSUE'
    ) seed
    JOIN sys_auto_code_rule r ON r.rule_name=seed.rule_code AND r.rule_code<>seed.rule_code;
    IF bad_rows<>0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='A stock document type rule display name already exists; resolve the existing name before seeding';
    END IF;

    INSERT INTO sys_auto_code_rule
        (rule_code,rule_type,factory_id,rule_name,rule_desc,max_length,is_padded,padded_char,padded_method,
         enable_flag,remark,attr1,create_by,create_time)
    SELECT seed.rule_code,seed.rule_type,NULL,seed.rule_code,
           CONCAT(seed.rule_code,' document number'),64,'N',NULL,'L','Y','',
           'V55_STOCK_DOCUMENT_RULE_SEED','migration',CURRENT_TIMESTAMP
    FROM (
        SELECT 'PURCHASE_RECEIPT' rule_code,'STOCK_IN' rule_type,'PR' prefix UNION ALL
        SELECT 'PRODUCTION_RECEIPT','STOCK_IN','PDR' UNION ALL
        SELECT 'OUTSOURCE_RECEIPT','STOCK_IN','OSR' UNION ALL
        SELECT 'MATERIAL_RETURN_RECEIPT','STOCK_IN','MRR' UNION ALL
        SELECT 'REWORK_RECEIPT','STOCK_IN','RWR' UNION ALL
        SELECT 'SALES_RETURN_RECEIPT','STOCK_IN','SRR' UNION ALL
        SELECT 'DEFECT_RECEIPT','STOCK_IN','DFR' UNION ALL
        SELECT 'SCRAP_RECEIPT','STOCK_IN','SCR' UNION ALL
        SELECT 'OTHER_RECEIPT','STOCK_IN','OIR' UNION ALL
        SELECT 'MATERIAL_ISSUE','STOCK_OUT','MI' UNION ALL
        SELECT 'OTHER_ISSUE','STOCK_OUT','OIS'
    ) seed
    WHERE NOT EXISTS (SELECT 1 FROM sys_auto_code_rule r WHERE r.rule_code=seed.rule_code);

    INSERT INTO sys_auto_code_part
        (rule_id,part_index,part_type,part_code,part_name,part_length,fix_character,create_by,create_time)
    SELECT r.rule_id,1,'FIXCHAR','PREFIX','prefix',CHAR_LENGTH(seed.prefix),seed.prefix,'migration',CURRENT_TIMESTAMP
    FROM (
        SELECT 'PURCHASE_RECEIPT' rule_code,'PR' prefix UNION ALL
        SELECT 'PRODUCTION_RECEIPT','PDR' UNION ALL SELECT 'OUTSOURCE_RECEIPT','OSR' UNION ALL
        SELECT 'MATERIAL_RETURN_RECEIPT','MRR' UNION ALL SELECT 'REWORK_RECEIPT','RWR' UNION ALL
        SELECT 'SALES_RETURN_RECEIPT','SRR' UNION ALL SELECT 'DEFECT_RECEIPT','DFR' UNION ALL
        SELECT 'SCRAP_RECEIPT','SCR' UNION ALL SELECT 'OTHER_RECEIPT','OIR' UNION ALL
        SELECT 'MATERIAL_ISSUE','MI' UNION ALL SELECT 'OTHER_ISSUE','OIS'
    ) seed JOIN sys_auto_code_rule r ON r.rule_code=seed.rule_code
    WHERE r.attr1='V55_STOCK_DOCUMENT_RULE_SEED'
      AND NOT EXISTS (SELECT 1 FROM sys_auto_code_part p WHERE p.rule_id=r.rule_id);

    INSERT INTO sys_auto_code_part
        (rule_id,part_index,part_type,part_code,part_name,part_length,date_format,create_by,create_time)
    SELECT r.rule_id,2,'NOWDATE','DATE','date',8,'yyyyMMdd','migration',CURRENT_TIMESTAMP
    FROM sys_auto_code_rule r
    WHERE r.attr1='V55_STOCK_DOCUMENT_RULE_SEED'
      AND (SELECT COUNT(*) FROM sys_auto_code_part p WHERE p.rule_id=r.rule_id)=1;

    INSERT INTO sys_auto_code_part
        (rule_id,part_index,part_type,part_code,part_name,part_length,seria_start_no,seria_step,
         cycle_flag,cycle_method,create_by,create_time)
    SELECT r.rule_id,3,'SERIALNO','SERIAL','daily serial',3,1,1,'Y','DAY','migration',CURRENT_TIMESTAMP
    FROM sys_auto_code_rule r
    WHERE r.attr1='V55_STOCK_DOCUMENT_RULE_SEED'
      AND (SELECT COUNT(*) FROM sys_auto_code_part p WHERE p.rule_id=r.rule_id)=2;

    UPDATE sys_auto_code_rule SET attr1=NULL WHERE attr1='V55_STOCK_DOCUMENT_RULE_SEED';
END$$
CALL mes_v55_seed_stock_document_number_rules()$$
DROP PROCEDURE mes_v55_seed_stock_document_number_rules$$
DELIMITER ;
