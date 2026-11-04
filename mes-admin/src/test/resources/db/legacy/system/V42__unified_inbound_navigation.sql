-- 退出旧交接/仓储接收命令，保留原菜单身份、角色关联及只读历史权限。
UPDATE sys_menu SET menu_name='旧流程物流历史',visible='1' WHERE perms='business:production:receipt:list' AND menu_type='C';
UPDATE sys_menu SET menu_name='旧流程物流详情' WHERE perms='business:production:receipt:detail';
UPDATE sys_menu SET menu_name='历史入库追溯' WHERE perms='business:wm:acceptance:read';
UPDATE sys_menu SET status='1',visible='1' WHERE perms IN ('business:production:receipt:create','business:production:receipt:reverse','business:wm:acceptance:create','business:wm:acceptance:reverse');
UPDATE sys_menu SET menu_name='出入库单' WHERE perms='business:wm:stock:document:list' AND menu_type='C';
UPDATE sys_menu SET menu_name='历史原料入库核实',visible='1' WHERE perms='business:wm:stock:receipt';
