# 贡献指南

欢迎通过GitHub或Gitee提交Issue和Pull Request。先说明问题、复现方式和期望行为，再围绕一个明确目标修改。

## 提交修改

1. Fork仓库并创建功能分支，避免直接修改上游main。
2. 阅读适用AGENTS与[开发文档](docs/README.md)，保持权限、数据空间、事务、幂等及业务事实边界。
3. 完成适用检查，PR说明改动、验证结果与未验证范围；不提交凭据、私有profile、业务数据或本机证据。
4. 原创贡献采用项目Apache-2.0，保留来源组件原有版权与许可。引入依赖时说明来源和许可。

## 主线与镜像

GitHub是正式审核、合并和发布主线；Gitee提供国内代码访问，也可接收贡献。Gitee PR由维护者取其源分支，基于最新GitHub主线集成和验证，再通过GitHub PR纳入，保留作者及原PR关联；随后同步Gitee并回告原请求。

两平台的Issue、PR、审核和附件独立管理。避免分别在两边合并main产生两条主线；主分支保护与同步权限由维护者配置。

维护者在已配置gitee remote的工作区，同步已审核主线：

```sh
git fetch origin
git push gitee origin/main:main
```

这里假定origin从GitHub拉取。remote和推送地址是各机器自己的设置，不随克隆传播，也不要求贡献者配置双推。本机代理、凭据助手、网络故障和具体端口按本机环境处理，不写入项目公共说明。
