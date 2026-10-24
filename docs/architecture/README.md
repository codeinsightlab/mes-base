# 当前架构

Base MES采用Java 21 / Spring Boot 4模块化单体。一套生产核心同时服务单个或多个Factory；Factory在当前界面表示工厂，也可按业务或年度组织独立数据空间。

## 模块与调用边界

| 模块 | 职责 |
| --- | --- |
| mes-common | 公共类型与工具 |
| mes-system | RuoYi来源的用户、组织、角色、菜单及系统设置 |
| mes-framework | 认证授权、工厂上下文、持久化与基础设施 |
| mes-quartz | 调度与执行日志 |
| mes-generator | 独立配置、metadata读取、MBG及应用模板生成 |
| mes-business | 物料/BOM、工艺、生产、质量、仓储 |
| mes-admin | 应用启动、HTTP装配与Flyway |

Controller负责HTTP输入输出，Service负责业务、事务和外部协调，Mapper负责数据访问。复杂业务编排不由生成器或前端组件代替。

## 数据空间与事务

中心库维护平台账号、角色、工厂和数据源配置。每个Factory绑定独立业务库，业务表不叠加factory_id行级隔离；不是多个工厂共用一组业务表的Shared模式。

| 归属 | 数据源/会话工厂/事务管理器 |
| --- | --- |
| 中心 | masterDataSource / masterSqlSessionFactory / masterTransactionManager |
| 工厂业务 | businessDataSource / businessSqlSessionFactory / businessTransactionManager |

业务入口按当前Factory租借受管连接池，不随工厂数量新增SessionFactory。请求准入与事务绑定按[请求准入](../development/request-admission.md)执行；事务内固定工厂和连接，禁止切厂/切库及跨中心/业务混写，缺绑定不回退默认库。

业务缓存按Factory隔离，文件另校验所有者、范围和资源。工厂角色只在所属Factory授权；平台角色的管理权限与业务权限分别处理。只有一个数据空间也沿用同一套实现。

## 生产与外部边界

Execution关联锁定路径、正式投入、报工、不良和库存交接。正常品或质量入库审核后复用统一数量规则检查Execution与工单，报工或定时任务不另行自动完结。不良负责申报与入仓，仓内物流归仓储。

每厂最多一个ERP；运行模式由受管集成配置决定，业务来源持久化保留。LOCAL库存与ERP权威库存分开，本地事务不承诺回滚远端HTTP，不确定结果保留原身份。详见[LOCAL/ERP边界](local-erp-business-boundary.md)。

具体开发遵守仓库AGENTS；使用和维护入口见[文档导航](../README.md)。
