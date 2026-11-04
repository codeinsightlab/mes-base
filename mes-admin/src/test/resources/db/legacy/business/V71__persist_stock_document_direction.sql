-- Persist the explicit IN/OUT flow for unified stock documents.
-- This is a one-time mapping of the known formal business keys; unknown keys stop the upgrade.
DELIMITER $$
DROP PROCEDURE IF EXISTS mes_v71_persist_stock_document_direction$$
CREATE PROCEDURE mes_v71_persist_stock_document_direction()
BEGIN
    DECLARE bad_rows BIGINT DEFAULT 0;

    SELECT COUNT(*) INTO bad_rows
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='wm_stock_document' AND column_name='direction';
    IF bad_rows<>0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='wm_stock_document.direction already exists; refusing an ambiguous partial upgrade';
    END IF;

    SELECT COUNT(*) INTO bad_rows
    FROM wm_stock_document
    WHERE document_type NOT IN (
        'PURCHASE_RECEIPT','PRODUCTION_RECEIPT','OUTSOURCE_RECEIPT','MATERIAL_RETURN_RECEIPT',
        'REWORK_RECEIPT','SALES_RETURN_RECEIPT','DEFECT_RECEIPT','SCRAP_RECEIPT','OTHER_RECEIPT',
        'MATERIAL_ISSUE','OTHER_ISSUE'
    );
    IF bad_rows<>0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Unknown stock document type exists; direction mapping requires explicit review';
    END IF;

    ALTER TABLE wm_stock_document
        ADD COLUMN direction VARCHAR(8) NULL COMMENT '正式库存流向：IN入库、OUT出库' AFTER document_type;

    UPDATE wm_stock_document
    SET direction=CASE document_type
        WHEN 'PURCHASE_RECEIPT' THEN 'IN'
        WHEN 'PRODUCTION_RECEIPT' THEN 'IN'
        WHEN 'OUTSOURCE_RECEIPT' THEN 'IN'
        WHEN 'MATERIAL_RETURN_RECEIPT' THEN 'IN'
        WHEN 'REWORK_RECEIPT' THEN 'IN'
        WHEN 'SALES_RETURN_RECEIPT' THEN 'IN'
        WHEN 'DEFECT_RECEIPT' THEN 'IN'
        WHEN 'SCRAP_RECEIPT' THEN 'IN'
        WHEN 'OTHER_RECEIPT' THEN 'IN'
        WHEN 'MATERIAL_ISSUE' THEN 'OUT'
        WHEN 'OTHER_ISSUE' THEN 'OUT'
        ELSE NULL
    END;

    SELECT COUNT(*) INTO bad_rows FROM wm_stock_document WHERE direction IS NULL;
    IF bad_rows<>0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Direction mapping left stock documents unresolved';
    END IF;

    ALTER TABLE wm_stock_document
        MODIFY COLUMN direction VARCHAR(8) NOT NULL COMMENT '正式库存流向：IN入库、OUT出库';
END$$
CALL mes_v71_persist_stock_document_direction()$$
DROP PROCEDURE mes_v71_persist_stock_document_direction$$
DELIMITER ;
