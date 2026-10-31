-- Support factory-scoped material name prefix lookup used by MaterialSelect.
DELIMITER $$
DROP PROCEDURE IF EXISTS mes_v82_material_name_prefix_index$$
CREATE PROCEDURE mes_v82_material_name_prefix_index()
BEGIN
    DECLARE column_count INT DEFAULT 0;
    DECLARE index_rows INT DEFAULT 0;
    DECLARE matching_rows INT DEFAULT 0;

    SELECT COUNT(*) INTO column_count
    FROM information_schema.columns
    WHERE table_schema=DATABASE() AND table_name='material'
      AND column_name IN ('factory_id','name');
    IF column_count<>2 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='material factory_id/name columns are required before indexing';
    END IF;

    SELECT COUNT(*) INTO index_rows
    FROM information_schema.statistics
    WHERE table_schema=DATABASE() AND table_name='material'
      AND index_name='idx_material_factory_name';
    IF index_rows=0 THEN
        ALTER TABLE material
            ADD KEY idx_material_factory_name(factory_id,name);
    ELSE
        SELECT COUNT(*) INTO matching_rows
        FROM information_schema.statistics
        WHERE table_schema=DATABASE() AND table_name='material'
          AND index_name='idx_material_factory_name'
          AND non_unique=1 AND sub_part IS NULL AND collation='A'
          AND index_type='BTREE' AND is_visible='YES' AND expression IS NULL
          AND ((seq_in_index=1 AND column_name='factory_id')
            OR (seq_in_index=2 AND column_name='name'));
        IF index_rows<>2 OR matching_rows<>2 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Existing idx_material_factory_name has an unexpected definition';
        END IF;
    END IF;
END$$
CALL mes_v82_material_name_prefix_index()$$
DROP PROCEDURE mes_v82_material_name_prefix_index$$
DELIMITER ;
