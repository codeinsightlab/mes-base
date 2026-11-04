-- 新增只读BOM展开导航，复用物料详情权限；角色授权由正式授权入口维护。
-- 已有同义入口必须完全一致，避免覆盖自定义导航或制造重复入口。
DELIMITER $$
CREATE PROCEDURE mes_v57_material_bom_explosion_navigation()
BEGIN
    DECLARE basic_menu BIGINT;
    DECLARE matched_rows BIGINT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    START TRANSACTION;

    SELECT COUNT(*),MIN(menu_id) INTO matched_rows,basic_menu
    FROM sys_menu WHERE parent_id=0 AND path='basic' AND menu_type='M' AND status='0';
    IF matched_rows<>1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Active basic navigation is missing or ambiguous';
    END IF;

    SELECT COUNT(*) INTO matched_rows FROM sys_menu
    WHERE perms='business:material:query' AND status='0';
    IF matched_rows=0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Existing material query permission is missing';
    END IF;

    SELECT COUNT(*) INTO matched_rows FROM sys_menu
    WHERE component='basic/bomExplosion/index'
       OR (parent_id=basic_menu AND (path='bom-explosion' OR menu_name='物料BOM展开'));
    IF matched_rows>1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='BOM explosion navigation is ambiguous';
    END IF;
    IF matched_rows=1 THEN
        SELECT COUNT(*) INTO matched_rows FROM sys_menu
        WHERE parent_id=basic_menu AND path='bom-explosion' AND component='basic/bomExplosion/index'
          AND menu_name='物料BOM展开' AND menu_type='C' AND visible='0' AND status='0'
          AND perms='business:material:query' AND is_frame='1' AND is_cache='0'
          AND (`query` IS NULL OR `query`='') AND (route_name IS NULL OR route_name='');
        IF matched_rows<>1 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Existing BOM explosion navigation conflicts with the reviewed definition';
        END IF;
    ELSE
        INSERT INTO sys_menu
            (menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
        VALUES
            ('物料BOM展开',basic_menu,3,'bom-explosion','basic/bomExplosion/index','1','0','C','0','0',
             'business:material:query','List','migration',CURRENT_TIMESTAMP);
    END IF;

    COMMIT;
END$$
CALL mes_v57_material_bom_explosion_navigation()$$
DROP PROCEDURE mes_v57_material_bom_explosion_navigation$$
DELIMITER ;
