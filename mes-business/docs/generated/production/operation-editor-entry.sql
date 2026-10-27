START TRANSACTION;
-- 工序能力人员统一从工序编辑维护；保留原菜单编号、权限和角色绑定。
-- 隐藏旧菜单而非删除授权对象，旧地址由前端跳转到工序页面。
UPDATE sys_menu SET visible = '1'
WHERE menu_type = 'C' AND component = 'production/mesOperationUser/index'
  AND perms = 'business:mes:operation:user:list';
COMMIT;
