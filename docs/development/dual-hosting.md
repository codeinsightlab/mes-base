# GitHub / Gitee 双托管

Base MES 前后端分别维护一个 Git 仓库，每个仓库的同一套提交同步到 GitHub 和 Gitee。

| 项目 | GitHub | Gitee |
| --- | --- | --- |
| 后端 | https://github.com/codeinsightlab/mes-base | https://gitee.com/codeinsightlab/mes-base |
| 前端 | https://github.com/codeinsightlab/mes-base-ui | https://gitee.com/codeinsightlab/mes-base-ui |

## 日常操作

当前工作区配置为：从 GitHub 拉取，向 GitHub 和 Gitee 两个地址推送，保留单独的 `gitee` remote 供查询和失败补推。

在前端或后端各自仓库中，完成本次工作、检查并提交后：

```sh
git push origin main
```

此命令依次向两个托管地址推送已提交的 main。未提交文件不会上传。推送标签使用 `git push origin --tags`；新分支使用 `git push origin 分支名`。

两个远端推送不是跨平台原子操作。一边成功、一边失败时，保留成功结果，排查后只补推失败的一边：

```sh
# 仅补 Gitee
git push gitee main
# 仅补 GitHub
git push https://github.com/codeinsightlab/mes-base.git main
```

前端补推 GitHub 时，将上例地址换成 mes-base-ui。不要使用 force 或 mirror 处理普通同步失败。

## 新机器配置

remote 设置只保存在本地 `.git/config`，不会随克隆或提交自动传播。以下配置在每个仓库首次执行一次。

后端：

```sh
git clone https://github.com/codeinsightlab/mes-base.git
cd mes-base
git remote add gitee https://gitee.com/codeinsightlab/mes-base.git
git remote set-url --add --push origin https://github.com/codeinsightlab/mes-base.git
git remote set-url --add --push origin https://gitee.com/codeinsightlab/mes-base.git
```

前端：

```sh
git clone https://github.com/codeinsightlab/mes-base-ui.git
cd mes-base-ui
git remote add gitee https://gitee.com/codeinsightlab/mes-base-ui.git
git remote set-url --add --push origin https://github.com/codeinsightlab/mes-base-ui.git
git remote set-url --add --push origin https://gitee.com/codeinsightlab/mes-base-ui.git
```

已有 remote 时先用下述命令检查，避免重复添加。HTTPS 推送需要该机器分别具备 GitHub 和 Gitee 的有效认证；浏览器已登录不必然代表 Git 可推送。凭证由本机凭证管理器维护，不放进 remote URL、脚本或文档。

```sh
git remote -v
git remote get-url --push --all origin
git ls-remote origin refs/heads/main
git ls-remote gitee refs/heads/main
```

最后两条的提交哈希相同，说明两边 main 指向同一提交；不代表 Issues、PR、Wiki、附件或 CI 已同步。

## 协作约定

- GitHub 作为主要拉取与合并入口，Gitee 提供国内访问与代码镜像。
- Gitee 收到的修改先纳入同一主线审查，再从本地向两边推送。避免两边网页独立编辑造成分叉。
- 在网页合并 PR 或从其他机器推送 GitHub，不会自动触发本机推送；本地在处理好工作区后快进更新，再执行双推。
- 本方案是本机双推，没有部署自动同步任务，也没有保存新增云端密钥。
- 原创贡献采用 Apache-2.0；来源组件的 MIT 版权和许可继续保留，见根目录 LICENSE、NOTICE 及来源声明。

Git 多 push URL 的语义见[官方 git-push 文档](https://git-scm.com/docs/git-push)。

## 2026-10-24 配置与验证记录

用户授权创建 Gitee 前后端开源仓库，并明确选择 Apache-2.0。补充两仓 LICENSE、NOTICE、README 托管入口，前端附完整上游 MIT 声明。当前任务只提交自身许可和文档，不包含并行开发中未提交的业务代码。

发布前只读核查：当前可达历史未发现私有 application-dev/application-prod 配置；后端13,064、前端2,079个 main 可达对象的私钥/GitHub令牌/AWS访问键常见格式检查未命中。该检查不等于完整安全审计；旧README的历史凭据描述未在当前历史复现，不据此改写历史或声称凭据已轮换。

实际结果：前后端Gitee仓库已创建，用户将个人空间改为codeinsightlab后，两仓URL和本地remote均已更新。Gitee公开API确认两仓private=false、默认分支main。首次完整双推成功：后端2780865、前端4ed0037分别包含数据空间说明与新地址，GitHub/Gitee均已收到。

首次GitHub传输遇到HTTP/2错误，使用单次HTTP/1.1覆盖后成功，不修改全局Git设置。若再次出现同类错误，可在对应仓库执行：

```sh
git -c http.version=HTTP/1.1 push origin main
```

Git凭证助手另输出credential storage lock告警，但上述推送进程退出0且远端已接受；这不等于凭证保存问题已修复，本轮未读取、变更或清理认证存储。

用户补充：Factory不仅可按物理工厂划分，也可按业务、年度或业务＋年度配置独立空间；两仓README增加实例及权限、数据库、年度承接边界，未修改Factory业务模型或实现自动结转。

本轮只有许可、文档和本地Git远端配置变更。没有修改业务代码、执行数据库或ERP操作、启停服务或运行新的业务验收；并行Agent已提交的业务提交随主线推送，未提交的业务改动未纳入本轮提交。
