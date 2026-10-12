# 架构、设计取舍与评分证据

## 数据流

```text
关卡配置 / 原创对白 / 候选电路
                ↓
玩家选择条件 → Digital参考模型 → 有效观察与预测记录
                                      ↓
                         候选模型预测与真实反例核对
                                      ↓
玩家连线编辑 → 独立验证副本 → 检验回信 / 剧情线索
                                      ↓
                         明确保存后写术式与进度
```

界面通过Java Swing绘制。所有真正的电路求值复用Digital；角色、纸页、灯光、声音只是表现层。多输出按关卡顺序编码，第五章C/S对应高低两灯，不能把多灯读数简化成单个真假值。

| 评分维度（主办方截图） | 可核对的贡献 | 证据位置 |
|---|---|---|
| 完成度25 | 五章、两个模式、逐章衔接、手动保存 | levels/；AcademyHome；Chapter4/5与Unlock检查；VALIDATION.md |
| 实用性20 | 新手教程、可回看、预测和具体反例 | AcademyLesson；ExperimentSession；Investigation；无学习效果量化承诺 |
| 创新性15 | 原创午夜调查；残页解释、证据排除、修复后线索 | WORLD_BIBLE；各章story.properties；README相近作品表 |
| 代码实现15 | 真实仿真、独立验证、无讯语义、保存保护 | ReferenceDevice；LevelValidator；AcademyProgress；相关测试 |
| 项目架构15 | 资源/模型/会话/表现分离，复用开源底座 | academy目录；LevelDefinition；本文件数据流；构建脚本 |
| 展示效果10 | 实际多屏、人物与灯光动效、可运行包 | screenshots/；AcademyCharacters/HomeMotion/Cinematic；录制指南 |

所有类路径均位于digital-source/src/main/java/de/neemann/digital/academy/，测试位于对应src/test/java目录。自动化截图采用临时演示进度，不包含玩家存档。未实现功能见README。
