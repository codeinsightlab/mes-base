
<!-- 来源：docs/generated/basic/equipment-permissions.md -->
# 设备

Scope: FACTORY_SCOPED; Mapper owner: BUSINESS

Path: `/api/basic/equipment`

- `business:equipment:read`
- `business:equipment:create`
- `business:equipment:update`
- `business:equipment:delete`

Menu INSERT SQL is generated beside this file for reviewed center-database execution; no automatic execution or role grants. Mapper ownership follows its fixed package; no Mapper annotation or central registration is required.

<!-- 来源：docs/generated/resource/personnel-permissions.md -->
# 生产人员

Scope: FACTORY_SCOPED; Mapper owner: BUSINESS

Path: `/api/resource/personnel`

- `business:personnel:read`
- `business:personnel:create`
- `business:personnel:update`
- `business:personnel:delete`

Menu INSERT SQL is generated beside this file for reviewed center-database execution; no automatic execution or role grants. Mapper ownership follows its fixed package; no Mapper annotation or central registration is required.

账号关联选择另需 business:directory:personnel:read，保持中心目录独立只读权限。设备归基础数据；人员归生产资源。菜单 SQL 不包含角色授权。
