# Java格式与检查

`.editorconfig`规定编码、换行和基础缩进。修改仅覆盖本次任务文件，避免格式化无关源码。

仓库提供 `scripts/format-java`，调用IntelliJ IDEA原生格式化器。使用前需安装IDEA，必要时通过 `MES_IDEA_HOME` 指定安装目录，并准备脚本引用的项目Code Style。当前脚本依赖本机 `.idea/codeStyles/Project.xml`，该目录不随Git分发；新机器需先准备相同的风格配置。

```sh
scripts/format-java path/to/Changed.java
scripts/format-java --check path/to/Changed.java
git diff --check
```

实际测试范围按改动选择；格式检查不能代替业务测试。后端完整验证使用 `mvn -B -ntp verify`，生成器可按[独立构建说明](codegen.md)验证。个人IDE路径、运行日志和本次检查回执不提交到公共指南。
