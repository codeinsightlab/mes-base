# 个人列表列配置

只保存列顺序与显示偏好，不能授予数据或字段权限。

## 存储与接口

中心库 Flyway 迁移 `mes-admin/src/main/resources/db/system/V23__personal_table_preferences.sql` 创建
`sys_user_table_preference`。访问使用已有 `masterTransactionManager` 和中心 Mapper 扫描包。
四维唯一约束为 factory_id + user_id + url + table_key；URL 使用 512 字符二进制 utf8mb4，table_key 使用
192 字符 ascii，完整索引长度在 MySQL 3072 字节限制内。保存使用原子 upsert，恢复默认删除记录。

通用入口 `/api/factory/table-preferences`：

- GET：query 参数 `url`、`tableKey`，返回 `{columns:null}` 或 `{columns:[{key,visible}]}`。
- PUT：body `{url,tableKey,columns}`，成功 204。
- DELETE：同 GET 标识，幂等删除，成功 204。

每个接口 AUTHENTICATED。URL 属业务请求上下文，因此由现有请求入口绑定当前 FactoryContext；
持久化仍在中心库。Service 从 FactoryContext 和 SecurityUtils 取得工厂及用户，忽略客户端身份字段。
不引入 Redis，不创建数据源或事务管理器。URL 最大 512 字符，tableKey 最大 192，最多 100 列，
字段 key 最大 64、唯一，visible 必须 JSON 布尔值，至少显示一列，序列化最大 16KiB。
JSON 不含脚本、HTML、格式化器或 SQL。

## 前端与生成器

`CrudPage` 复用现有 fields 的稳定 key、标题、渲染和排序信息。最小接入：

```vue
<CrudPage title="物料" table-key="business:material:list" :fields="fields"
  :api="api" permission="business:material" />
```

`ColumnSettings` + `useTableColumns` 统一负责读取、草稿、拖拽、保存、恢复、失败提示和过期请求拒绝。
选择和操作列不参与个人配置。字段可使用 `columnVisible`、`columnHideable`、`columnMovable`、
`columnFixed` 限定默认与固定位置。已删字段忽略；重复/未知 key 忽略；新增字段按默认顺序补到已有可移动
字段之后，固定列保持位置。保存成功后才应用；失败保留草稿；取消不修改生效状态。

`tableRouteUrl` 优先选稳定 matched 路由模板，统一处理 hash/history、域名、query 和末尾斜杠。
特殊页面可传 `table-config-url`。用户、token、工厂、上下文 revision、页面 URL 或 tableKey 变化时，
立即恢复默认并重读；代次与完整身份比较拒绝迟到响应。

通用 `index.vue.vm` 无论 generateSync 是否开启均生成 `${permission}:list` 作为 tableKey；
其中 permission 来源于显式权限 prefix/resource 配置，同一业务重生成稳定、跨模块不重名。
个人配置不依赖同步开关，不生成独立业务配置接口/配置表。当前只有 CrudPage 列表模板，不对非列表
页面添加入口。
