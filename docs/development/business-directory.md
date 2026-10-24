# 业务引用目录

`BusinessDirectoryService`为业务页面和业务Service提供只读引用资料：人员与工艺选择、关联物料/用户/工艺/工序批量回显，以及历史账号存在性查询。它不维护主数据，不决定库存、权限、人员生产资格或工单适用性。

## 职责与缓存边界

| 组件 | 职责 |
| --- | --- |
| BusinessDirectoryController | 对当前页面使用的目录查询授权，裁剪HTTP响应字段 |
| BusinessDirectoryService | 查询条件、引用资料批量加载及展示字段转换；历史身份实时查询 |
| DetailEntityCache | 详情Key、批量命中、未命中加载、序列化、TTL及失效版本保护 |
| BusinessCacheProcessor / BusinessCacheAspect | 工厂资源命名空间、通用CRUD缓存接入、提交后失效 |

目录的批量查询复用现有详情缓存，不另建名称缓存或目录缓存Key。列表直接经Mapper分页；历史身份存在性也直接查询数据库。不要将目录查询再包装成一套缓存Service，或把状态、存在性和生产准入交给缓存决定。通用缓存机制见[业务缓存指南](business-cache.md)。

## 前端目录入口

| GET接口 | 正式使用场景 | 权限 |
| --- | --- | --- |
| /api/business/directory/personnel | 不良订正责任人选择 | business:directory:personnel:read |
| /api/business/directory/personnel/{id} | 已选责任人回显 | business:directory:personnel:read |
| /api/business/directory/routings | 生产执行列表工艺筛选 | routing:list |
| /api/business/directory/routings/{id} | 已选工艺回显 | routing:list |
| /api/business/directory/report-operators | 补报历史实际操作人员选择 | business:production:report:supplement |

人员选择复用`src/components/business/directory/PersonnelSelect.vue`；工艺筛选复用`BusinessDirectorySelect.vue`并显式传`resource="routings"`。`src/api/business/directory.ts`仅声明当前使用的personnel/routings资源。

```vue
<script setup lang="ts">
import { ref } from 'vue'
import PersonnelSelect from '@/components/business/directory/PersonnelSelect.vue'
const responsibleUserId = ref<string | null>(null)
</script>
<template>
  <PersonnelSelect v-model="responsibleUserId" />
</template>
```

组件支持modelValue/v-model、disabled、clearable、placeholder；人员支持departmentId过滤。支持远程搜索、加载更多、单项回显和错误重试；无权限不请求。切厂、撤权或过滤部门变更时清空选择和候选，拒绝迟到响应。ID保持字符串。

personnel/routings列表参数为keyword、offset、limit，分页默认0/20，limit最多100；keyword最多30字符。人员另支持departmentId，沿用中心Mapper的部门及子部门过滤。补报人员keyword最多100字符，分页同样最多100。响应保留items、total、offset、limit；HTTP字段由Controller白名单裁剪。

部门、岗位目录选择器及对应接口没有正式业务消费者，已移除；系统部门和岗位管理保持独立。工艺编辑选择工序调用`/api/production/routing/references/operations`，不经过目录operations接口。批量引用只供后端内部调用，不提供闲置的routing-references、operation-references、user-references HTTP包装。

补报查询统一使用`/api/business/directory/report-operators`，没有第二套`/api/production/report-operators`入口。

## 后端批量回显

业务Service先收集本批去重ID，再调用目录方法，避免行循环中的逐条数据库或Redis调用。

| 方法 | 数据及消费者 |
| --- | --- |
| materialEntities(ids) | 当前工厂完整MaterialEntity；工艺关联产品、工单工艺图 |
| userLabels(ids) | 中心用户安全字段生成的DirectoryLabel；报工、执行、质量及仓储名称回显 |
| routingLabels(ids) | 当前工厂工艺资料；执行列表、工单执行汇总 |
| routingLabel(id) | 工艺单项回显及执行路径；复用routingLabels |
| operationLabels(ids) | 当前工厂工序资料；工艺草稿、工单工艺图及执行路径 |

单批最多200个不同的非空ID；只对未命中的ID执行一次参数化IN查询。物料、工艺、工序缓存完整Entity，再由上层裁剪。用户仅允许缓存userId、userName、nickName、deptId、status、delFlag，不缓存密码、联系方式、角色或权限。

物料按当前工厂与`MaterialResource.PREFIX`隔离，与物料通用详情共享Key；工艺、工序同样使用各自规范资源前缀。不存在另一份reference/directory/label缓存或IDs组合Key。详情与批量查询双向复用同一实体、同一提交后失效规则；回滚不失效。写事务直接读取当前数据库，不向共享缓存发布未提交对象。缓存命中前后保留固定事务/租约校验，缺Factory不回退其他范围。

关联物料缺失时保留原ID并明确待确认。当前编码和名称不代替业务事实快照，也不证明物料已启用、单位正确或库存可用；对应业务写入仍实时校验。

## 中心身份与历史查询

人员资料来自中心库sys_user，通过既有SysUserMapper和具名masterTransactionManager查询。中心目录跨厂共享，HTTP仍需当前Factory与业务权限；共享目录不意味着账号所属当前工厂，也不授予派工或报工资格。

- personnel列表/详情仅供启用、未逻辑删除账号选择；详情先查数据库确认当前状态，再复用安全字段详情缓存。
- historicalUsers供补报检索，不限制当前启用状态；现有列表SQL排除逻辑删除账号。
- historicalUserExists供补报和不良责任人写入校验，不缓存；检查账号行是否仍存在，包括停用或逻辑删除账号，不替代当前操作权限与生产资格校验。
- 普通中心查询使用注入Mapper；已有业务事务中确需查询中心身份时，复用PlatformPersistence独立只读窗口，不混用中心/业务本地事务。

历史事实优先使用已保存快照。当前引用名称只能作为当前展示信息，不能伪装成历史姓名。
