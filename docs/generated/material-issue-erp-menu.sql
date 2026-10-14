-- 查询ERP状态独立权限，增量添加按钮并保留既有菜单及角色关联。本文件只生成，不自动执行或授予角色。
SET @issue_parent := (SELECT menu_id FROM sys_menu WHERE perms='business:mes:material:issue:list' AND menu_type='C' ORDER BY menu_id LIMIT 1);
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT '查询ERP状态',@issue_parent,9,'#',1,0,'F','0','0','business:mes:material:issue:erp:query','#','codegen',NOW()
WHERE @issue_parent IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='business:mes:material:issue:erp:query');
UPDATE sys_menu SET menu_name='查询ERP状态',parent_id=@issue_parent,menu_type='F',status='0'
WHERE perms='business:mes:material:issue:erp:query' AND @issue_parent IS NOT NULL;

-- 核实并同步ERP状态独立权限，增量添加按钮并保留既有菜单及角色关联。本文件只生成，不自动执行或授予角色。
SET @issue_parent := (SELECT menu_id FROM sys_menu WHERE perms='business:mes:material:issue:list' AND menu_type='C' ORDER BY menu_id LIMIT 1);
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
SELECT '核实并同步ERP状态',@issue_parent,10,'#',1,0,'F','0','0','business:mes:material:issue:erp:reconcile','#','codegen',NOW()
WHERE @issue_parent IS NOT NULL AND NOT EXISTS (SELECT 1 FROM sys_menu WHERE perms='business:mes:material:issue:erp:reconcile');
UPDATE sys_menu SET menu_name='核实并同步ERP状态',parent_id=@issue_parent,menu_type='F',status='0'
WHERE perms='business:mes:material:issue:erp:reconcile' AND @issue_parent IS NOT NULL;
