-- The production-input scene retains issue:update; unified document cancellation
-- retains issue:void. Retired administrative CRUD endpoints grant no replacement
-- stock-document access, so existing roles cannot gain other document types.
DELIMITER $$
CREATE PROCEDURE mes_v53_retire_material_issue_crud()
BEGIN
    DECLARE input_menu_id BIGINT;
    DECLARE stock_menu_id BIGINT;
    DECLARE stock_list_menu_id BIGINT;
    DECLARE resolved_count INT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    SELECT COUNT(*), MIN(menu_id) INTO resolved_count, input_menu_id
    FROM sys_menu
    WHERE menu_type='C' AND component='production/productionInput/index'
      AND perms='business:mes:material:issue:update';
    IF resolved_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Production input menu is missing or ambiguous';
    END IF;
    SELECT COUNT(*), MIN(menu_id) INTO resolved_count, stock_menu_id
    FROM sys_menu
    WHERE menu_type='C' AND component='wm/stockDocuments/index'
      AND perms='business:wm:stock:document:list';
    IF resolved_count <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Stock document menu is missing or ambiguous';
    END IF;

    CREATE TEMPORARY TABLE mes_v53_permission_before (
        role_id BIGINT NOT NULL,
        perms VARCHAR(100) NOT NULL,
        PRIMARY KEY(role_id,perms)
    ) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
    INSERT INTO mes_v53_permission_before(role_id,perms)
    SELECT DISTINCT granted.role_id, permission_menu.perms
    FROM sys_role_menu granted
    JOIN sys_menu permission_menu ON permission_menu.menu_id=granted.menu_id
    WHERE permission_menu.perms IN (
        'business:mes:material:issue:list','business:mes:material:issue:query',
        'business:mes:material:issue:item:list','business:mes:material:issue:item:query',
        'business:mes:material:issue:update','business:mes:material:issue:void',
        'business:wm:stock:document:list','business:wm:stock:document:query'
    );

    SELECT COUNT(*), MIN(menu_id) INTO resolved_count, stock_list_menu_id
    FROM sys_menu
    WHERE menu_type='F' AND perms='business:wm:stock:document:list'
      AND parent_id=stock_menu_id;
    IF resolved_count > 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Stock document list permission is ambiguous';
    END IF;

    START TRANSACTION;
    IF stock_list_menu_id IS NULL THEN
        INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,`query`,route_name,is_frame,is_cache,
                             menu_type,visible,status,perms,icon,create_by,create_time)
        VALUES('单据列表',stock_menu_id,0,'#',NULL,NULL,NULL,'1','0','F','0','0',
               'business:wm:stock:document:list','#','migration',CURRENT_TIMESTAMP);
        SET stock_list_menu_id=LAST_INSERT_ID();
    END IF;
    -- The page node becomes navigation only; keep its former list capability on a function node.
    INSERT INTO sys_role_menu(role_id,menu_id)
    SELECT granted.role_id,stock_list_menu_id
    FROM sys_role_menu granted
    WHERE granted.menu_id=stock_menu_id
      AND NOT EXISTS(SELECT 1 FROM sys_role_menu existing
                     WHERE existing.role_id=granted.role_id AND existing.menu_id=stock_list_menu_id);
    UPDATE sys_menu SET perms=NULL WHERE menu_id=stock_menu_id;

    -- Retain the four scoped read permissions and move them into the unified workspace.
    UPDATE sys_menu SET parent_id=stock_menu_id, menu_name='生产领料列表'
    WHERE menu_type='F' AND perms='business:mes:material:issue:list';
    UPDATE sys_menu SET parent_id=stock_menu_id, menu_name='生产领料详情'
    WHERE menu_type='F' AND perms='business:mes:material:issue:query';
    UPDATE sys_menu SET parent_id=stock_menu_id, menu_name='生产领料明细列表'
    WHERE menu_type='F' AND perms='business:mes:material:issue:item:list';
    UPDATE sys_menu SET parent_id=stock_menu_id, menu_name='生产领料明细详情'
    WHERE menu_type='F' AND perms='business:mes:material:issue:item:query';

    IF (SELECT COUNT(*) FROM sys_menu WHERE menu_type='F'
        AND perms='business:mes:material:issue:list' AND parent_id=stock_menu_id) <> 1
       OR (SELECT COUNT(*) FROM sys_menu WHERE menu_type='F'
        AND perms='business:mes:material:issue:query' AND parent_id=stock_menu_id) <> 1
       OR (SELECT COUNT(*) FROM sys_menu WHERE menu_type='F'
        AND perms='business:mes:material:issue:item:list' AND parent_id=stock_menu_id) <> 1
       OR (SELECT COUNT(*) FROM sys_menu WHERE menu_type='F'
        AND perms='business:mes:material:issue:item:query' AND parent_id=stock_menu_id) <> 1 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Scoped production issue read permissions are missing or ambiguous';
    END IF;

    -- Old page nodes carried list permissions themselves; transfer those grants to their scoped functions.
    INSERT INTO sys_role_menu(role_id,menu_id)
    SELECT DISTINCT granted.role_id, scoped.menu_id
    FROM sys_role_menu granted
    JOIN sys_menu old_page ON old_page.menu_id=granted.menu_id
      AND old_page.component='issue/mesMaterialIssue/index'
    JOIN sys_menu scoped ON scoped.parent_id=stock_menu_id
      AND scoped.perms='business:mes:material:issue:list'
    WHERE NOT EXISTS(SELECT 1 FROM sys_role_menu existing
                     WHERE existing.role_id=granted.role_id AND existing.menu_id=scoped.menu_id);
    INSERT INTO sys_role_menu(role_id,menu_id)
    SELECT DISTINCT granted.role_id, scoped.menu_id
    FROM sys_role_menu granted
    JOIN sys_menu old_page ON old_page.menu_id=granted.menu_id
      AND old_page.component='issue/mesMaterialIssueItem/index'
    JOIN sys_menu scoped ON scoped.parent_id=stock_menu_id
      AND scoped.perms='business:mes:material:issue:item:list'
    WHERE NOT EXISTS(SELECT 1 FROM sys_role_menu existing
                     WHERE existing.role_id=granted.role_id AND existing.menu_id=scoped.menu_id);

    -- Share one permissionless workspace entry without granting stock:list to issue-only roles.
    INSERT INTO sys_role_menu(role_id,menu_id)
    SELECT DISTINCT prior.role_id,stock_menu_id
    FROM mes_v53_permission_before prior
    WHERE prior.perms IN (
        'business:mes:material:issue:list','business:mes:material:issue:query',
        'business:mes:material:issue:item:list','business:mes:material:issue:item:query',
        'business:mes:material:issue:update','business:mes:material:issue:void',
        'business:wm:stock:document:list','business:wm:stock:document:query'
    )
      AND NOT EXISTS(SELECT 1 FROM sys_role_menu existing
                     WHERE existing.role_id=prior.role_id AND existing.menu_id=stock_menu_id);

    UPDATE sys_menu SET parent_id=input_menu_id, menu_name='生产投入操作'
    WHERE menu_type='F' AND perms='business:mes:material:issue:update';
    UPDATE sys_menu SET parent_id=stock_menu_id, menu_name='撤销生产领料'
    WHERE menu_type='F' AND perms='business:mes:material:issue:void';

    -- A navigation grant carrying the same permission does not add an operation.
    INSERT INTO sys_role_menu(role_id,menu_id)
    SELECT DISTINCT granted.role_id,input_menu_id
    FROM sys_role_menu granted
    JOIN sys_menu current_menu ON current_menu.menu_id=granted.menu_id
    WHERE current_menu.perms='business:mes:material:issue:update'
      AND NOT EXISTS(SELECT 1 FROM sys_role_menu existing
                     WHERE existing.role_id=granted.role_id AND existing.menu_id=input_menu_id);
    INSERT INTO sys_role_menu(role_id,menu_id)
    SELECT DISTINCT granted.role_id,parent.menu_id
    FROM sys_role_menu granted
    JOIN sys_menu child ON child.menu_id=granted.menu_id AND child.menu_id=input_menu_id
    JOIN sys_menu parent ON parent.menu_id=child.parent_id AND parent.menu_type='M'
      AND (parent.perms IS NULL OR parent.perms='')
    WHERE NOT EXISTS(SELECT 1 FROM sys_role_menu existing
                     WHERE existing.role_id=granted.role_id AND existing.menu_id=parent.menu_id);

    DELETE FROM sys_role_menu WHERE menu_id IN (
        SELECT menu_id FROM sys_menu WHERE perms IN (
            'business:mes:material:issue:create','business:mes:material:issue:delete',
            'business:mes:material:issue:item:create','business:mes:material:issue:item:update',
            'business:mes:material:issue:item:delete'
        )
    );
    DELETE FROM sys_menu WHERE perms IN (
        'business:mes:material:issue:create','business:mes:material:issue:delete',
        'business:mes:material:issue:item:create','business:mes:material:issue:item:update',
        'business:mes:material:issue:item:delete'
    ) AND menu_type='F';

    IF EXISTS(
        SELECT 1 FROM sys_menu child JOIN sys_menu retired ON retired.menu_id=child.parent_id
        WHERE retired.component IN ('issue/mesMaterialIssue/index','issue/mesMaterialIssueItem/index')
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Retired issue menu has an unreviewed child';
    END IF;
    DELETE FROM sys_role_menu WHERE menu_id IN (
        SELECT menu_id FROM sys_menu
        WHERE component IN ('issue/mesMaterialIssue/index','issue/mesMaterialIssueItem/index')
    );
    DELETE FROM sys_menu
    WHERE component IN ('issue/mesMaterialIssue/index','issue/mesMaterialIssueItem/index');

    -- Remove only an empty legacy directory; unrelated custom children keep it.
    DELETE FROM sys_role_menu WHERE menu_id IN (
        SELECT directory.menu_id FROM sys_menu directory
        LEFT JOIN sys_menu child ON child.parent_id=directory.menu_id
        WHERE directory.menu_type='M' AND directory.path='issue' AND child.menu_id IS NULL
    );
    DELETE directory FROM sys_menu directory
    LEFT JOIN sys_menu child ON child.parent_id=directory.menu_id
    WHERE directory.menu_type='M' AND directory.path='issue' AND child.menu_id IS NULL;

    CREATE TEMPORARY TABLE mes_v53_permission_after (
        role_id BIGINT NOT NULL,
        perms VARCHAR(100) NOT NULL,
        PRIMARY KEY(role_id,perms)
    ) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
    INSERT INTO mes_v53_permission_after(role_id,perms)
    SELECT DISTINCT granted.role_id, permission_menu.perms
    FROM sys_role_menu granted
    JOIN sys_menu permission_menu ON permission_menu.menu_id=granted.menu_id
    WHERE permission_menu.perms IN (
        'business:mes:material:issue:list','business:mes:material:issue:query',
        'business:mes:material:issue:item:list','business:mes:material:issue:item:query',
        'business:mes:material:issue:update','business:mes:material:issue:void',
        'business:wm:stock:document:list','business:wm:stock:document:query'
    );
    IF EXISTS(
        SELECT 1 FROM mes_v53_permission_before before_row
        WHERE NOT EXISTS(SELECT 1 FROM mes_v53_permission_after after_row
                         WHERE after_row.role_id=before_row.role_id AND after_row.perms=before_row.perms)
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Role permission set lost an existing capability during issue retirement';
    END IF;
    IF EXISTS(
        SELECT 1 FROM mes_v53_permission_after after_row
        WHERE NOT EXISTS(SELECT 1 FROM mes_v53_permission_before before_row
                         WHERE before_row.role_id=after_row.role_id AND before_row.perms=after_row.perms)
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='Role permission set changed during issue retirement';
    END IF;
    DROP TEMPORARY TABLE mes_v53_permission_after;
    DROP TEMPORARY TABLE mes_v53_permission_before;
    COMMIT;
END$$
CALL mes_v53_retire_material_issue_crud()$$
DROP PROCEDURE mes_v53_retire_material_issue_crud$$
DELIMITER ;
