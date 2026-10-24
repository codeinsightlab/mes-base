# Base MES

面向中小型离散制造企业的轻量化生产执行与管理系统。

Base MES 围绕工单、批次和实际加工路径，连接材料领用、工序报工、不良申报、生产入库与库存追溯，帮助工厂看清正在做什么、做到哪一步、完成多少，以及材料和产品流向哪里。

本仓库为后端服务；管理与现场操作界面见 [mes-base-ui](https://github.com/codeinsightlab/mes-base-ui)。

代码托管：[GitHub](https://github.com/codeinsightlab/mes-base) · [Gitee](https://gitee.com/zlxStudyHome/mes-base)

## 产品定位

适用于五金加工、机械加工、汽车零部件等离散制造场景，优先服务希望减少纸单和人工统计的中小型工厂。

- **LOCAL 独立运行**：由 MES 管理必要的生产与本地库存业务。
- **ERP 协同方向**：沿用同一套生产核心，对接 ERP 的工单和库存体系；正式库存命令仍在规划中。
- **轻量现场操作**：围绕批次扫码、完成报工和不良申报组织操作，派工按需使用。

## 核心能力

| 领域 | 当前能力 |
| --- | --- |
| 物料与工艺 | 物料、分类、单位、版本化多阶 BOM 维护，工序、工艺路线与执行路径锁定 |
| 生产执行 | 工单、批次 Execution、BOM 单材料投产领料、同批追加、工序报工及数量追踪 |
| 质量管理 | 独立不良申报、核实、不良品/报废品入仓及来源追溯 |
| 本地仓储 | 仓库、库位、库存余额与流水，正式单据草稿和审核，生产分批入库 |
| 生产收尾 | 正常品或质量入库审核后，按统一数量规则检查 Execution 和工单收尾 |
| 平台管理 | 用户、角色、菜单、工厂、数据源、私有文件、操作审计、任务及系统监控 |
| 集成基础 | OpenAPI 客户端管理、Factory 集成配置、ERP 查询与同步准备 |

## 设计亮点

- **一套生产核心，多工厂隔离**：中心库维护平台配置，工厂业务使用独立业务库；业务请求按当前工厂路由。
- **围绕批次追溯**：Execution 关联实际工艺路径、投入、报工、不良和入库，批次身份与扫码行为分离。
- **数量来自业务事实**：统一计算有效产出、不良损失和累计入库，支持分批入库与同批追加，避免重复计量。
- **审核驱动库存生效**：正式仓储单据审核后记录库存动作；领料入口向同一单据审核模型收敛中。
- **保留操作身份与留痕**：提交幂等、订正/作废留痕，结果不确定时明确保留状态，便于核实。

## 当前阶段

项目处于开发与交付准备阶段。LOCAL 单材料生产主链已在指定测试环境完成真实业务验收，包含 BOM 比例领料、同批追加、正常品与不良品分批入库，以及审核后的执行/工单收尾。

这不代表所有业务或生产环境已验收。当前优先完善统一领料/出入库审核、请求恢复和数量查询稳定性，再补齐生产退料、调拨、现场工作站与标签、管理统计及首厂交付。

多阶 BOM 的维护能力已具备，生产投产目前只支持一条直接材料组件。部分仓储类型尚未接通正式业务来源；ERP 正式库存交易和完整委外流程尚未交付。

## 技术栈与结构

Java 21 · Spring Boot 4 · MyBatis · MySQL 8 · Redis · Flyway · Maven

采用模块化单体，按 Controller → Service → Mapper 组织业务。

| 模块 | 职责 |
| --- | --- |
| `mes-business` | 物料/BOM、生产、质量、仓储等工厂业务 |
| `mes-system` | 用户、组织、角色、菜单及系统设置 |
| `mes-framework` | 认证授权、工厂上下文、持久化路由与基础设施 |
| `mes-common` | 公共类型和工具 |
| `mes-quartz` | 任务调度与执行记录 |
| `mes-generator` | 基于数据库元数据的代码生成 |
| `mes-admin` | 应用启动、装配及数据库版本迁移 |

## 本地运行

准备 Java 21、Maven、MySQL 8 和 Redis，并将前端与本仓库配套使用。

1. 从 [`config/application-dev.example.yml`](config/application-dev.example.yml) 复制开发配置到 `mes-admin/src/main/resources/application-dev.yml`，填写自己的中心库、Redis、文件目录及数据源加密密钥。真实配置被 Git 忽略。
2. 按[平台配置与初始化说明](docs/development/platform.md)准备中心库和首个管理员；空库没有通用默认账号密码。
3. 按[数据库升级说明](docs/development/database-upgrades.md)初始化/升级目标业务库，通过平台管理建立工厂与业务数据源绑定，并配置所需权限。中心库迁移与业务库迁移是两个独立步骤。
4. 构建后，使用实际配置的端口启动：

```sh
mvn -B -ntp verify
java -jar target/mes-base-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev
```

前端的 `MES_API_TARGET` 需要与后端监听地址一致。`prod` 使用对应的生产配置示例与 `--spring.profiles.active=prod`；环境准备及数据库升级须在目标环境单独核实。本地构建产物会包含本机 profile 配置，应按该环境的运行产物管理。

## 路线图

| 阶段 | 方向 |
| --- | --- |
| 近期 | 统一业务出入库审核、稳定性收尾、退料与调拨、工作站和标签打印 |
| 交付完善 | 交期/在制/质量/人员产量统计，安装升级、备份恢复演练和首厂验收 |
| 后续扩展 | ERP 正式业务、委外闭环、多材料生产 |
| 长期探索 | 设备运维与采集、辅助排程、只读生产 AI |

路线图表达方向，不构成已实现能力或交付日期承诺。专门返工、独立质量放行和定时完结已退出当前产品方案；不良管理负责申报与入仓，入仓后的物流归仓储。

## 文档与相关项目

- [前端项目](https://github.com/codeinsightlab/mes-base-ui)
- [当前规划与待办](docs/development/next-stage-plan.md)
- [生产投产与 BOM 规则](docs/development/production-input.md)
- [LOCAL / ERP 业务边界](docs/architecture/local-erp-business-boundary.md)
- [数据库版本升级](docs/development/database-upgrades.md)
- [架构文档](docs/architecture/README.md)
- [GitHub / Gitee 双托管](docs/development/dual-hosting.md)

开发过程、专项审查和验收证据保存在 `docs/development`、`docs/reviews` 与 `docs/operations`，与本页的产品介绍分开维护。

## 来源与第三方声明

项目原创贡献采用 [Apache License 2.0](LICENSE)，可按许可条款使用、修改和分发。项目复用并演进了 RuoYi、ktg-mes 等来源产品的部分能力，相关组件继续遵守原有 MIT 许可；来源清单和原许可保留在 [`docs/legal`](docs/legal)，归属说明见 [NOTICE](NOTICE)。第三方依赖遵守各自许可证。
