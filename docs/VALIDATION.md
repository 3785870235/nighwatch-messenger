# 发布验证 · 2026-10-13

本次五章构建来自 Claude 明确交还后的稳定源码。源码及关卡共2312个文件的快照在打包与GUI复核后保持一致。没有用旧JAR冒充新源码。

| 检查 | 结果 | 证据 |
|---|---|---|
| 交还源码全量 Maven 单元测试 | 878，失败0、错误0、跳过0；复用本次交还日志，发布阶段未重复整套 | [原始输出脱敏版](validation/claude-unit-tests.txt) |
| 当前源码重新 package | BUILD SUCCESS；此步骤显式跳过测试 | [构建输出](validation/package-build.txt) |
| 便携Java + 新JAR，第五章实际Swing流程 | 63项通过 | [检查输出](validation/package-chapter5.txt) |
| 同一运行环境，逐章衔接与最终返回首页 | 24项通过 | [检查输出](validation/package-unlock.txt) |
| 运行截图 | 六张实际窗口截图，已逐张查看 | [screenshots](../screenshots/) |

`app/Digital.jar` SHA-256：

```text
a7747caa5edb884299c7dda27a5424b201f78c891b866fc84bf6eb779f763830
```

构建等价命令（本地使用已缓存依赖离线执行）：

```powershell
mvn -B -o -ntp -P no-git-rev '-Dgit.commit.id.describe=v0.31-816eff5-academy' -DskipTests -f digital-source/pom.xml package
```

关键GUI检查使用测试类作为驱动，但产品类只来自新JAR，classpath中不含`target/classes`：

```powershell
$cp = '<source>/digital-source/target/test-classes;<portable>/app/Digital.jar'
& '<portable>/runtime/bin/java.exe' '-Dfile.encoding=UTF-8' -cp $cp de.neemann.digital.academy.AcademyChapter5Check '<portable>'
& '<portable>/runtime/bin/java.exe' '-Dfile.encoding=UTF-8' -cp $cp de.neemann.digital.academy.AcademyUnlockCheck '<portable>' '<temporary-screenshots>'
```

日志中的机器绝对路径已脱敏。测试使用临时进度，截图不代表人工逐关完整通关。尚未完成高DPI全覆盖、音频主观验收、跨系统兼容测试和学习效果研究；动态美术不是录制好的视频。微光第二组图集尚未接入，部分姿态及封口美术仍可改进。演示视频待团队按录制指南补充。
