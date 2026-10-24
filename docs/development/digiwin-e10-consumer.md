# 鼎捷 E10 SDK 消费方接入

当前入口为工厂管理的“E10 OAPI 配置”；参数属于工厂中心配置，禁止在 dev/prod YAML 中维护第二份 E10 配置。

## 工厂页面与持久化

用户先保存工厂，再在工厂列表对应行打开“E10 OAPI 配置”。表单维护完整 endpoint、host 的
ver/prod/timezone/ip/id/lang/acct，以及 service 的 prod/ip/id。host.id 必须显式提供，允许空字符串。
时区及 service.id 原样使用，不补模块后缀、不猜测其他身份；旧系统 service.namespace 不属于本 SDK 契约。

真实连接值只通过管理页面填写，不写进文档、测试、样例或日志。

复用现有中心表 `mf_factory`，正式 Flyway V38 增加：

- `oapi_config_ref`：首次保存生成的不透明稳定连接引用。
- `oapi_config_cipher`：整个连接参数 JSON，经现有 AES-GCM 机制加密。
- `oapi_config_revision`：连接参数版本；保存同时校验并递增工厂 revision，拒绝并发覆盖。

只扩展已有工厂基础设施，不新增业务表、独立 CRUD、凭证中心或第三数据源。运行环境仍需要既有
`mes.runtime.datasource-key`，它是部署密钥，不能移到普通工厂响应或浏览器存储中。

`GET/PUT /api/system/factory/{id}/oapi` 均要求平台角色及 `system:factory:edit`，
GET 是有权限管理员专用的连接维护响应，普通工厂列表、详情和连接候选不返回参数或密文。
保存检查字段完整性、长度、控制字符和 HTTP(S) URL；不含 URL 账号、查询参数或片段。
配置损坏或密钥不匹配明确拒绝，不降级、不发送请求。

## 与运行模式分离

保存参数只做连接准备，不切换 LOCAL/ERP，不写 `erp_provider/erp_config_ref`，不解锁工厂，
不创建 EXTERNAL 业务记录，不写库存，也不向 ERP 发送请求。页面显示“真实 ERP 读取尚未验证”。
配置准备完成不等于已授权切换，已有业务仍受当前模式与来源约束。

新工厂按既有工厂表单选择 ERP 类型和该厂连接，仍受 Registry 和原有模式锁定控制。
一个工厂只保存一个 E10 配置；进入 ERP 或存在 ERP 绑定后，本入口只读，连接身份变更必须走受控迁移。
不提供普通表单覆盖已冻结身份的路径，模式迁移需要另行受控实施。

FactoryIntegrationRegistry 读取当前厂的数据库 E10 配置，按 factoryId、provider、reference、revision
准入；旧 YAML 中的 DIGIWIN_E10 连接不再进入候选或运行快照。非 E10 的既有通用 Adapter 配置保留原行为。
普通中心查询使用构造器注入 Mapper；只有业务事务活动时，才使用既有独立中心只读窗口。

## SDK 装配与业务调用

继续使用 `com.lixin:lixin-common-capability-digiwin-e10:0.1.0-SNAPSHOT`，
不复制 SDK 的认证、Header、HTTP、序列化和错误解析。没有新增 Token 字段或虚构凭证来满足旧认证引用。

Factory Runtime 启用时装配查询消费方；启动不读取所有连接、不创建 SDK 客户端、不发送网络探测。
显式 Query 要求已有合法 FactoryIntegrationContext，mode=ERP、provider=DIGIWIN_E10，
工厂、连接引用及配置版本与数据库一致。首次业务调用惰性创建并缓存对应客户端，退出时统一关闭。
LOCAL、缺上下文、跨厂、错误引用或过期配置均在请求发送前拒绝。

当前只提供既有 `DigiwinE10MoQueries.queryMo(E10QueryRequest)`，内部复用
`OapiMoGateway.mo().query`。请求显式提供 pageNo/pageSize、isGetSchema/isGetCount、conditions/orders。
SDK 异常继续原样传播，不转为空结果、不自动重试，不记录完整报文或身份。
SDK 查询封装只负责读取。同步落库、库存命令和工单业务写入由各自业务服务负责，不能依据查询成功推定其他操作已接通。
