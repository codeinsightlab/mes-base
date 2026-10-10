# 业务目录只读组件

2026-10-10，按用户最终约束：不新增表，只查询中心库现有人员、部门、岗位；不提供新增、修改、删除。

## 数据与授权

mes-business 的 BusinessDirectoryService 直接复用 SysUserMapper、SysDeptMapper、SysPostMapper，Mapper/XML 仍由 masterSqlSessionFactory 装配，Service 使用具名 masterTransactionManager 的只读事务。
前端请求携带当前 X-Factory-Id；后端业务授权链按当前工厂校验查询权限，平台角色合并业务权限的既有规则适用。接口不需要 system:user/dept/post 管理权限，不改变平台管理接口。

这些表是中心库共享目录。不同工厂可读取同一套启用引用数据，本接口不宣称按工厂隔离目录行。人员为现有启用且未删除 sys_user，不创建独立花名册；停用/删除人员与部门、停用岗位不供选择和回显（404）。历史单据显示停用引用时应保存业务快照或采用另外明确的历史查询契约。

V18 只添加 sys_menu 的三个 F 类型查询权限节点，不增加页面路由、不自动授予角色、不新增表。角色须显式分配相应权限。未运行真实数据库迁移或角色赋权。

| 接口 | 查询权限 |
| --- | --- |
| GET /api/business/directory/personnel[/{id}] | business:directory:personnel:read |
| GET /api/business/directory/departments[/{id}] | business:directory:department:read |
| GET /api/business/directory/posts[/{id}] | business:directory:post:read |

列表参数 keyword 按名称模糊搜索（最多30字符）、offset 默认0、limit 默认20且最多100。人员另支持 departmentId，沿用中心 Mapper 的部门及子部门过滤。排序由服务端固定，不接受排序字段或 dataScope SQL。
响应为 `{items,total,offset,limit}`；单项白名单 `{id,name,code,parentId,departmentId}`，ID 为字符串，total/offset/limit 为数值。不存在的可选关联字段为 null。部门无 code；岗位无部门、父级。Controller 显式裁剪 Entity；SysUser 密码、联系方式、角色与认证状态不进入响应。

## 页面嵌入

```vue
<script setup lang="ts">
import { ref } from 'vue'
import PersonnelSelect from '@/components/business/directory/PersonnelSelect.vue'
import DepartmentSelect from '@/components/business/directory/DepartmentSelect.vue'
import PostSelect from '@/components/business/directory/PostSelect.vue'
const personnelId = ref<string | null>(null)
const departmentId = ref<string | null>(null)
const postId = ref<string | null>(null)
</script>
<template>
  <DepartmentSelect v-model="departmentId" />
  <PersonnelSelect v-model="personnelId" :department-id="departmentId ?? undefined" />
  <PostSelect v-model="postId" />
</template>
```

三个组件支持 modelValue/v-model、disabled、clearable、placeholder；人员支持 departmentId。change 返回完整白名单选项或 null。支持远程搜索、加载更多、GET 单项回显、错误重试；无权限不请求。切换工厂、授权修订或过滤部门变更清空选择与候选，过期请求不得回填。不会缓存跨厂目录数据，不提供管理弹窗和写入 API。

## 验证边界

后端 BusinessDirectoryTest 使用 TEST ONLY H2、真实平台 Mapper/XML、PageHelper 和 Spring 中心事务代理验证分页、裁剪、停用/删除引用、独立权限、缺厂拒绝、非 GET 返回405及分页限制。
前端 businessDirectory.test.ts 验证三个实际组件的资源映射、字符串 ID、分页搜索、回显、清空、无权限、切厂竞态和失败重试。
测试通过不等于用户 8081 服务已加载新代码；需要用户编译/重启后，迁移 V18、分配查询权限并刷新授权，再在真实业务页嵌入验收。


### 本轮验证记录

- IDEA 原生 formatter：目录源码及新增测试已格式化。
- `mvn -B -ntp verify`：BUILD SUCCESS；BusinessDirectoryTest 4 项通过。既有真实 MySQL 验证未配置而跳过，不将 H2 结果冒充真实数据库。
- `/usr/bin/python3 scripts/smoke-packaged.py`：10 passed / 0 failed。
- UI `npm run test`：23 个文件、125 项通过；`npm run build`、`npm run lint` 通过。构建仍有既有大 chunk 警告。
- 两仓库 `git diff --check` 通过。
- TEST ONLY 浏览器预览复用正式组件，确认人员单项回显、部门分页/选择、切厂清空、撤权禁用；1440 和390宽度未出现组件溢出。
- 预览启动：UI 仓库用 Node22 执行 `npx vite --config tests/business-directory-preview/vite.config.ts`，端口18193。该入口只在 tests 中，不注册生产路由、不调用真实数据库。
- 真实8081服务未重启、中心库迁移未执行、角色未赋权、真实数据库接口未验收。
