-- 撤回检测项与缺陷字典页面及其权限，先清角色授权，保留无关菜单和角色。
DELETE FROM sys_role_menu WHERE menu_id IN (
    SELECT menu_id FROM sys_menu
    WHERE component IN ('qc/qcIndex/index','qc/qcDefect/index','mes/qc/qcindex/index','mes/qc/qcdefect/index')
       OR perms LIKE 'business:qc:index:%' OR perms LIKE 'business:qc:defect:%'
       OR perms LIKE 'qc:index:%' OR perms LIKE 'qc:defect:%'
);
DELETE FROM sys_menu
WHERE component IN ('qc/qcIndex/index','qc/qcDefect/index','mes/qc/qcindex/index','mes/qc/qcdefect/index')
   OR perms LIKE 'business:qc:index:%' OR perms LIKE 'business:qc:defect:%'
   OR perms LIKE 'qc:index:%' OR perms LIKE 'qc:defect:%';

-- 只清已撤回页面的列偏好，不影响其他业务偏好。
DELETE FROM sys_user_table_preference
WHERE url IN ('/qc/qcIndex','/qc/qcDefect','/qc/qc-index','/qc/qc-defect','/mes/qc/qcindex','/mes/qc/qcdefect');

-- 仅清生成器创建且已空的质检目录，共享目录或用户维护目录保留。
DELETE FROM sys_role_menu WHERE menu_id IN (
    SELECT parent.menu_id FROM sys_menu parent
    LEFT JOIN sys_menu child ON child.parent_id=parent.menu_id
    WHERE parent.parent_id=0 AND parent.path='qc' AND parent.menu_type='M'
      AND parent.create_by='codegen' AND child.menu_id IS NULL
);
DELETE FROM sys_menu WHERE menu_id IN (
    SELECT menu_id FROM (SELECT DISTINCT parent.menu_id FROM sys_menu parent
        LEFT JOIN sys_menu child ON child.parent_id=parent.menu_id
        WHERE parent.parent_id=0 AND parent.path='qc' AND parent.menu_type='M'
          AND parent.create_by='codegen' AND child.menu_id IS NULL) empty_groups
);
