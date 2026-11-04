-- 撤回独立生产人员 CRUD；已有业务人员只读目录仍直接读取 sys_user。
-- 将被挂入旧生产人员页面的共享查询权限恢复为独立权限目录项，保留原菜单标识和角色绑定。
UPDATE sys_menu
SET parent_id=0, menu_name='业务人员查询', path='business/directory/personnel'
WHERE perms='business:directory:personnel:read'
  AND parent_id IN (SELECT menu_id FROM (SELECT DISTINCT menu_id FROM sys_menu
      WHERE component='resource/personnel/index' OR perms LIKE 'business:personnel:%'
         OR perms LIKE 'personnel:%') removed_pages);

-- 先删除旧角色授权，再删除页面及按钮，避免授权指向不存在的菜单。
DELETE FROM sys_role_menu WHERE menu_id IN (
    SELECT menu_id FROM sys_menu
    WHERE component='resource/personnel/index'
       OR perms LIKE 'business:personnel:%' OR perms LIKE 'personnel:%'
);
DELETE FROM sys_menu
WHERE component='resource/personnel/index'
   OR perms LIKE 'business:personnel:%' OR perms LIKE 'personnel:%';

-- 列偏好仅属于已撤回的独立页面，不删除账号或其他页面偏好。
DELETE FROM sys_user_table_preference
WHERE table_key='business:personnel:list' AND url IN ('/resource/personnel','/basic/personnel');

-- 仅删除由上一轮生成器创建且已空的生产资源目录，其他能力和用户目录不受影响。
DELETE FROM sys_role_menu WHERE menu_id IN (
    SELECT parent.menu_id FROM sys_menu parent
    LEFT JOIN sys_menu child ON child.parent_id=parent.menu_id
    WHERE parent.parent_id=0 AND parent.path='resource' AND parent.menu_type='M'
      AND parent.create_by='codegen' AND child.menu_id IS NULL
);
DELETE FROM sys_menu WHERE menu_id IN (
    SELECT menu_id FROM (SELECT DISTINCT parent.menu_id FROM sys_menu parent
        LEFT JOIN sys_menu child ON child.parent_id=parent.menu_id
        WHERE parent.parent_id=0 AND parent.path='resource' AND parent.menu_type='M'
          AND parent.create_by='codegen' AND child.menu_id IS NULL) empty_groups
);
