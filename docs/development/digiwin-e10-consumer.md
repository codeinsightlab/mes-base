# 鼎捷 E10 SDK 消费方接入

日期：2026-10-17。范围：MES 自有配置、按现有工厂连接绑定复用 Client、显式工单 Query 示例及离线测试。
真实 ERP 请求为 0；没有修改 SDK、继续协议研究、生成 SDK、迁移数据或切换任何工厂模式。

## 依赖与装配位置

`mes-admin/pom.xml` 引入独立 Java 8 JAR：

```xml
<dependency>
    <groupId>com.lixin</groupId>
    <artifactId>lixin-common-capability-digiwin-e10</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

MES 继续使用 Java 21 / Spring Boot 4。没有引入 all-starter、E10 Starter、systemPath 或复制 SDK 源码。
SDK 本身不读取 application.yml，不具有 Spring 自动装配。

- `DigiwinE10Configuration`：本项目手工配置类，读取 MES 前缀并用 SDK Builder 创建 Client。
- `DigiwinE10MoQueries`：显式调用的类型化工单查询，按已准入 FactoryIntegrationContext 检查绑定，原样传播 SDK 异常。
- `FactoryIntegrationRegistry.requireConnection`：装配时复用现有注册连接校验。未新建模式 Resolver 或工厂授权来源。

## 显式配置

配置前缀 `mes.integration.digiwin-e10` 属于本业务项目。application.yml 中默认 `enabled: false`。
启用但没有连接配置，或任一必需字段缺失/为空，装配明确失败；唯一允许显式空字符串的是 `host.id`。
不读取旧项目、截图、历史联调配置或 SDK 示例身份来补值。

以下是待业务方提供值的配置模板，不是可直接用于 ERP 的身份：

```yaml
mes:
  integration:
    # 既有连接注册入口；工厂持久化的 erp_config_ref 必须引用同一个 key。
    connections:
      binding-e10-v1:
        factory-id: ${E10_FACTORY_ID}
        provider: DIGIWIN_E10
        service-url: ${E10_ENDPOINT}
        organization: ${E10_ORGANIZATION}
        credential-reference: ${E10_CREDENTIAL_REFERENCE}
        enabled: true
        revision: 1
    digiwin-e10:
      enabled: true
      connections:
        binding-e10-v1:
          endpoint: ${E10_ENDPOINT}
          host:
            ver: ${E10_HOST_VER}
            prod: ${E10_HOST_PROD}
            timezone: ${E10_HOST_TIMEZONE}
            ip: ${E10_HOST_IP}
            id: ${E10_HOST_ID}
            lang: ${E10_HOST_LANG}
            acct: ${E10_HOST_ACCT}
          service:
            prod: ${E10_SERVICE_PROD}
            ip: ${E10_SERVICE_IP}
            id: ${E10_SERVICE_ID}
```

- `endpoint` 是完整 HTTP(S) 地址，必须与同引用下现有 `service-url` 完全一致，沿用注册入口的 URL 校验。
- `host` 显式传入 `new E10Host(ver, prod, timezone, ip, id, lang, acct)`；timezone 为已确认 UTC offset。
  `E10_HOST_ID` 必须存在，可以显式设为空；没有默认值。
- `service` 显式传入 `new E10Service(prod, ip, id)`，`service.id` 原样传入，不追加后缀；OAPI_MO 不是身份。
- `DIGIWIN_E10` 是本消费方采用的 provider 标识；不是新增的全局模式或供应商枚举。
- `credential-reference` 是现有 FactoryIntegrationRegistry 的管理准入要求，必须解析到服务端非空配置值；
  本次没有改变这一规则，也不将其作为 E10 Token、签名或 Header 发送。SDK 没有新增 Token 获取或认证流程。
- 连接引用负责工厂归属与配置版本，新增 E10 子配置不会改变中心库工厂模式或解锁历史工厂。
  当前 LOCAL 工厂不能因此发起 E10 Query。正式模式切换仍须遵守项目既有停旧启新规则。

目前未提供任何真实 endpoint、host/service 身份或授权的 ERP 连接绑定，保留关闭状态。
示例配置中的 Factory、组织、凭证引用和模式绑定同样需要业务方确认，不自动创建或写入数据库。

## Client 生命周期与调用边界

启用时每个已注册连接引用创建一个 Client，并复用到应用退出；构造只做本地校验和资源初始化，不访问 ERP。
装配依赖项目既有 Factory Runtime 提供的 FactoryIntegrationRegistry；未装配该入口时不创建替代 Registry。
使用 SDK 默认连接/借连接/响应/整体超时 3000/3000/10000/30000ms、最大连接20、响应上限4MiB。
不修改 SDK 网络策略，不自动重试或翻页。

手工 Bean 使用 `@Bean(destroyMethod = "close")` 管理 `DigiwinE10MoQueries`；其 `close()` 逐一关闭自有 SDK Client。
多连接装配中途失败时，已创建的 Client 也会关闭。Gateway 共享这些 Client，不自行管理连接池。

调用时要求当前请求或显式任务已取得合法 FactoryIntegrationSnapshot，mode=ERP，provider=DIGIWIN_E10，
当前 Factory、configurationRef、configurationRevision 与启动时登记的 Client 相符；不接受调用方参数选厂或改派连接。
缺上下文、LOCAL、跨厂、错误 provider/引用或版本不符均在发送前拒绝。
HTTP 使用已有请求准入；后台任务使用已有 FactoryIntegrationExecutor 在执行线程建立上下文。

没有注册 Controller、启动查询、健康检查、定时任务或自动同步，也没有把此示例实现成已验收的
ProductionErpAdapter / WarehouseErpAdapter。工单 Query 本身不能证明库存、可投产或真实出库生效。

## 工单查询调用示例

下面的消费方类可编译，只有业务代码显式调用方法时才会发送一次 Query。
调用者必须已经处于上述工厂集成上下文，不能从应用启动或健康检查调用。

```java
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.lixin.capability.digiwin.e10.E10Exception;
import com.lixin.capability.digiwin.e10.request.E10QueryRequest;
import com.lixin.capability.digiwin.e10.response.mo.MoQueryRow;
import dev.mesbase.server.integration.e10.DigiwinE10MoQueries;
import java.util.List;

public final class MesE10MoUsage {
    public static List<MoQueryRow> queryFirstPage(DigiwinE10MoQueries queries) {
        E10QueryRequest request = new E10QueryRequest();
        request.setPageNo(1);
        request.setPageSize(20);
        request.setIsGetSchema(false);
        request.setIsGetCount(false);
        request.setConditions(JsonNodeFactory.instance.arrayNode());
        request.setOrders(JsonNodeFactory.instance.arrayNode());
        try {
            return queries.queryMo(request).getRows();
        } catch (E10Exception failure) {
            // 原样交给调用用例处理，Reason/Outcome/完整响应仍在原异常中。
            // 不记录异常堆栈、响应或报文，不转换为空列表，不自动重试。
            throw failure;
        }
    }
}
```

内部调用为 `new OapiMoGateway(client).mo().query(request)`，不手工拼 Header、信封或服务名。
需要数量时显式 `request.setIsGetCount(true)`，从同一个返回对象读取 `getRows()` 和 `getCnt()`；
缺少 cnt 不补 0。非 Query SDK 使用 `getRecords()`，本轮没有增加非 Query 调用。
conditions/orders 必须由调用方显式传入，内部结构不推测。Request 可变，禁止跨线程并发修改同一实例。

SDK 正常返回已通过 HTTP、业务状态及已知结构校验；不再调用已删除的
isHttpSuccess()/requireBusinessResponse()。E10Exception 原样向上抛出，保留 Reason、Outcome、
完整响应和适用的原始正文；超时 UNKNOWN、部分成功与失败都不重放、不伪造成功。
业务方如需诊断只能按授权范围读取原异常，不将完整异常、Header、digi-key、Token、身份或报文
写日志/返回前端；不要开启 Apache wire/header 日志。
业务落库、业务幂等与状态转换不在此传输示例中实现，也不承诺跨 ERP/MES 原子提交。

## 延期操作与验证范围

本轮不使用 EMP_CLAIM_DOC / EMP_LOAN_DOC / SUPPLIER_FI / SUPPLIER_PURCHASE / CUSTOMER_FI /
CUSTOMER_SALES 的 ReqConfirm，以及 SUPPLIER / CUSTOMER 的 ReqApproval。
8 项操作共 10 个属性仍为 DEFERRED / CONTRACT_UNVERIFIED，不研究绑定、不生成或修改 SDK。

已阅读 SDK 仓库 `/Users/user/work/lixin/lixin-common-capability` 的
`skills/digiwin-e10-client-usage-skill.md`、README“鼎捷 E10”章节及
`docs/reviews/digiwin-e10-stage-closeout.md`。
本地测试 `DigiwinE10ConsumerTest` 只向随机端口 127.0.0.1 Mock HTTP Server 发送合成内容，
不加载应用真实配置、数据库、Redis或真实 ERP。

### 实际执行结果

| 命令或检查 | 结果 |
|---|---|
| `mvn -B -ntp dependency:get -Dartifact=com.lixin:lixin-common-capability-digiwin-e10:0.1.0-SNAPSHOT -Dtransitive=true` | BUILD SUCCESS，SDK、父 POM 及传递依赖可解析；没有 install/deploy 或修改 SDK |
| `mvn -B -ntp -pl mes-admin -am dependency:tree '-Dincludes=com.lixin:*,com.fasterxml.jackson.core:*,org.apache.httpcomponents:*,org.slf4j:*'` | BUILD SUCCESS，消费方实际使用 HttpClient 4.5.14、Jackson 2.21.5、SLF4J 2.0.18；无 Starter |
| `./scripts/format-java`，仅本轮 4 个 Java 文件 | 通过，使用项目 IntelliJ formatter |
| `E10_IT_ENABLED=false E10_MO_IT_ENABLED=false WXPUSHER_REAL_TEST=false mvn -B -ntp verify` | 全 reactor BUILD SUCCESS；710 项，668 通过、42 项环境门控跳过，0 失败/错误 |
| `E10_IT_ENABLED=false E10_MO_IT_ENABLED=false WXPUSHER_REAL_TEST=false mvn -B -ntp -pl mes-admin -am -Dtest=DigiwinE10ConsumerTest,FactoryIntegrationConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false verify` | 最后补充缺失 factory-id 的拒绝校验后重新编译打包；E10 消费方 34 项、既有集成配置 4 项，38/38 通过、0 跳过 |
| 文档 Java 示例以最终消费方测试 classpath 执行 `javac -encoding UTF-8 --class-path ... -d /tmp/mes-e10-consumer-doc-20261017 /tmp/mes-e10-consumer-doc-20261017/MesE10MoUsage.java` | 编译通过，没有执行示例 |
| `/usr/bin/python3 scripts/smoke-packaged.py` | 10/10，通过隔离随机回环端口验证打包应用；仅加载打包默认配置，E10 关闭，未访问开发连接或真实 ERP |
| 打包内容及工作区检查 | SDK 独立 JAR 已进入 BOOT-INF/lib，无其他 lixin Starter；SDK 仓库保持干净；`git diff --check` 通过 |

SDK 制品 class major=52，确认为 Java 8 字节码；本机使用的 SNAPSHOT JAR SHA-256：
`2267e3574dfd6ac5c4d1121b52cb397acbef3b021f96b9245bf46083a09ff98e`。

日志：`/tmp/mes-e10-dependency-resolution.log`、`/tmp/mes-e10-dependency-tree.log`、
`/tmp/mes-e10-verify-final.log`、`/tmp/mes-e10-consumer-final.log`、`/tmp/mes-e10-packaged-smoke.log`。
全量验证后最后一处增强仅涉及连接装配的 factory-id 缺失拒绝及对应离线负例，已由最后的定向 verify
重新编译测试打包；打包 smoke 和文档编译使用最终产物。

离线验证覆盖：启动零请求、默认关闭、配置缺失/空值、注册绑定失效/不匹配、工厂与模式准入、
显式请求字段、原样 service.id、合法空查询、BigDecimal 数量、cnt 缺失语义、完整部分结果、
HTTP 错误、超时 UNKNOWN 无重试，以及应用退出关闭 Client。
未运行任何真实 ERP 查询或写入，没有自动探测，没有触及用户现有服务生命周期。
没有 SDK 变更、数据库/菜单迁移、业务落库或正式业务状态流转；不能据此宣称真实 E10 接口或 ERP 业务闭环验收。
本任务在消费方接入完成后停止，真实联调须另行明确授权与提供完整配置。

回滚本轮接入时，移除该依赖、两个 E10 消费方类、手工装配开关和对应测试，并撤销 Registry 新增的
`requireConnection` 方法；没有数据库状态需要回滚。
