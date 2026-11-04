-- Keep the persisted flow filter indexed without rewriting the published V71 migration.
DELIMITER $$
DROP PROCEDURE IF EXISTS mes_v72_index_stock_document_direction$$
CREATE PROCEDURE mes_v72_index_stock_document_direction()
BEGIN
    DECLARE direction_columns INT DEFAULT 0;
    DECLARE matching_indexes INT DEFAULT 0;
    DECLARE index_name VARCHAR(64);

    SELECT COUNT(*) INTO direction_columns
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='wm_stock_document'
      AND column_name IN ('direction','status');
    IF direction_columns<>2 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='wm_stock_document direction/status columns are required before indexing';
    END IF;

    SELECT COUNT(*) INTO matching_indexes
    FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name='wm_stock_document'
      AND index_name='idx_stock_document_direction_state';
    IF matching_indexes=0 THEN
        ALTER TABLE wm_stock_document
            ADD KEY idx_stock_document_direction_state(direction,status,id);
    ELSE
        SELECT COUNT(DISTINCT seq_in_index) INTO matching_indexes
        FROM information_schema.statistics
        WHERE table_schema=DATABASE() AND table_name='wm_stock_document'
          AND index_name='idx_stock_document_direction_state'
          AND ((seq_in_index=1 AND column_name='direction')
            OR (seq_in_index=2 AND column_name='status')
            OR (seq_in_index=3 AND column_name='id'));
        IF matching_indexes<>3 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Existing idx_stock_document_direction_state has an unexpected definition';
        END IF;
    END IF;
END$$
CALL mes_v72_index_stock_document_direction()$$
DROP PROCEDURE mes_v72_index_stock_document_direction$$
DELIMITER ;
