# 业务目录只读组件

不新增表，只查询中心库现有人员、部门、岗位；不提供新增、修改、删除。

## 数据与授权

mes-business 的 BusinessDirectoryService 直接复用 SysUserMapper、SysDeptMapper、SysPostMapper，Mapper/XML 仍由 masterSqlSessionFactory 装配，Service 使用具名 masterTransactionManager 的只读事务。
前端请求携带当前 X-Factory-Id；后端业务授权链按当前工厂校验查询权限，平台角色合并业务权限的既有规则适用。接口不需要 system:user/dept/post 管理权限，不改变平台管理接口。

这些表是中心库共享目录。不同工厂可读取同一套启用引用数据，本接口不宣称按工厂隔离目录行。人员为现有启用且未删除 sys_user，不创建独立花名册；停用/删除人员与部门、停用岗位不供选择和回显（404）。历史单据显示停用引用时应保存业务快照或采用另外明确的历史查询契约。


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
const operatorUserId = ref<string | null>(null)
const departmentId = ref<string | null>(null)
const postId = ref<string | null>(null)
</script>
<template>
  <DepartmentSelect v-model="departmentId" />
  <PersonnelSelect v-model="operatorUserId" :department-id="departmentId ?? undefined" />
  <PostSelect v-model="postId" />
</template>
```

三个组件支持 modelValue/v-model、disabled、clearable、placeholder；人员支持 departmentId。change 返回完整白名单选项或 null。支持远程搜索、加载更多、GET 单项回显、错误重试；无权限不请求。切换工厂、授权修订或过滤部门变更清空选择与候选，过期请求不得回填。业务目录缓存按当前 Factory 与资源隔离，中心用户等共享引用按中心范围读取，不提供管理弹窗和写入 API。详情与批量回显复用统一详情缓存和提交后失效规则，不能缓存跨厂身份或绕过授权。
