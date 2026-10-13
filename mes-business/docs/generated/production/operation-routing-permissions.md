# 生产基础权限

2026-10-13；Codegen 生成后按本批用户指定契约审查调整。

- operation:list/query/create/update/delete/sync
- routing:list/query/create/update/delete/sync/publish/copy/version

步骤与适用产品从父级继承工厂边界，通过 routing:query/update 维护；无独立菜单或直通 CRUD。
菜单 SQL 为 add-or-update，只包含菜单与权限声明，不含角色授权。
生成的缓存命名空间 business:operation/business:routing 与 HTTP 权限分开；都按当前工厂隔离。
