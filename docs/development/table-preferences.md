# 个人列表列配置

2026-10-12。只保存列顺序与显示偏好，不能授予数据或字段权限。

## 存储与接口

中心库 Flyway 新迁移 `mes-admin/src/main/resources/db/system/V23__personal_table_preferences.sql` 创建
`sys_user_table_preference`。访问使用已有 `masterTransactionManager` 和中心 Mapper 扫描包。
四维唯一约束为 factory_id + user_id + url + table_key；URL 使用 512 字符二进制 utf8mb4，table_key 使用
192 字符 ascii，完整索引长度在 MySQL 3072 字节限制内。保存使用原子 upsert，恢复默认删除记录。
本次只提交迁移文件；真实数据库尚未执行该迁移。

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

（2026-10-12 后续已移除 reviewed 覆盖；当前统一走通用列表模板，正式重生成由用户操作。以下为早期试点记录。）
现有六个基础业务采用配置中显式 `frontendTemplate` 指向的 reviewed 页面模板，保留既有引用选择、
字典、编辑、详情、同步与权限交互。更新模板后通过标准生成命令发布，不手改生成页面。

## 验证边界

后端 H2/MySQL 模式测试使用真实迁移结构、XML Mapper、Spring 事务代理及 HTTP 权限拦截器，覆盖
四维隔离、原子并发保存、恢复默认、校验及服务端身份。前端 Vitest 使用实际 CrudPage/配置组件，覆盖
草稿取消、失败、字段合并、固定列和切换身份迟到请求。浏览器验收与真实 MySQL 执行必须独立记录，
不能把 H2 或 jsdom 测试称为真实数据库或浏览器验收。

### 本轮交付结果

- 使用最新生成器与真实 metadata 运行 `scripts/mes-business-codegen`，六个基础业务的 backend/frontend/test
  正式重新生成，报告 GENERATED，126 项（其中删除 18 个旧独立同步类）。没有手动补生成页面。
  菜单和持久化未重生成，也未执行菜单 SQL；本轮配置与报告保存在 `docs/generated/codegen/2026-10-12-column-preferences.*`。
- 生成后 `mvn -B -ntp test` 全 reactor 通过；个人偏好 4 测试、缓存 13 测试、生成同步真实 Spring
  代理事务与 HTTP Fixture 测试通过。前端 `npm test` 150 通过，`npm run build` 通过。
- 后续执行 `mvn -B -ntp clean package -DskipTests` 通过：清除删除源码对应的旧字节码，防止旧同步
  Controller 或旧命名渲染器残留到 JAR。两仓 `git diff --check` 通过。未提交、推送或部署。
- 隔离浏览器 Fixture 验证实际生成物料页面的入口、草稿取消、真实拖拽、隐藏、保存和刷新保持；
  使用内存 HTTP API，不代表真实数据库验收。按用户最新要求停止后续视觉验证，最终交互与真实
  环境验收由用户执行。真实 MySQL 的 V23 迁移尚未执行。
