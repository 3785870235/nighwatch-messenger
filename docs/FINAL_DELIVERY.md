# 最终参赛交付清单

版本：competition-final-2026-10-13。题目：The Midnight Academy。

完整交付只需要下载MidnightLogicAcademy-Final-Submission.zip，解压后进入MidnightLogicAcademy目录。

| 材料 | 文件 / 入口 | 状态 |
|---|---|---|
| 可运行游戏 | Run-Academy.cmd、app/Digital.jar、assets/、levels/ | 已包含；五章全开与自由工坊 |
| Java运行环境 | runtime/（完整ZIP内） | 已包含legal/NOTICE，无需安装Java即可试玩 |
| 源码与构建 | digital-source/、tools/Build-Academy.ps1 | 已包含；开发构建需JDK11+与Maven |
| 功能状态和核心文件 | README.md | 已逐项说明 |
| AI使用与真实记录 | AI_USAGE.md、ai-logs/ | 已包含脱敏项目节选，非全量会话 |
| 实际运行截图 | screenshots/ | 7张，符合3–8张要求 |
| 演示视频 | demo演示.mp4 | 团队已上传，2分58.55秒 |
| 设计、原创性和相近作品 | docs/WORLD_BIBLE.md、ARCHITECTURE_AND_EVIDENCE.md、README.md | 已包含；区分Digital复用与团队新增 |
| 开源和素材来源 | LICENSE、docs/ATTRIBUTION.md、assets/ASSET_SOURCES.md | 保留Digital GPL、版权及Java许可 |
| 测试与交付证据 | docs/VALIDATION.md、docs/validation/、docs/VIDEO_AUDIT.md | 实测数据与边界明确 |

## 用户要求在本版的体现

- 真实Computer Organization逻辑：改变报告、预测、观察、用反例排除残页解释，再实际连线与独立检验；未知/无讯不冒充0。
- 原创午夜学院故事、芙/微光/洛、五章调查；与Digital和Turing Complete的差异在README说明，不把原软件整体称为原创。
- 关卡模式与自由工坊；五章可直接选，通过仍可进入下一章。
- 手动保存/另存为；退出取消和失败保护；没有自动存档。
- 多屏简洁交互、手写字体回退、可后退及跳过的小课、可跳过的动态序幕；Esc跳整段，空格跳当前幕。
- 魔法元件和场景、程序灯光/纸翼/瞳孔及音效；微光八姿态、人物入场与说话嘴型。
- 精确边界：偷看/困惑姿态保持画中眼神；行走/口型是原画程序形变，不是逐帧手绘或语音对口型；部分第三章姿态和信封美术仍可打磨。
- 只提交本项目必要材料；不包含玩家work、账户凭据、内部memory或无关会话。

## 运行与检查

完整解压到可写目录，双击Run-Academy.cmd。不要在ZIP内启动。游戏不联网、不调用模型；需要时手动保存，玩家文件随后写入work/。

核心软件版本与已验证角色动态版相同：880项单元测试，32项章节检查，以及另外10组GUI检查通过；无缺图替代绘制警告。完整包提供MANIFEST.sha256用于逐文件校验。源码、素材、视频和运行环境在同一个包里，无需拼接下载。

这是一份最终交付清单，不声称主办方已完成收件/审核。未进行完整人工逐关通关、全部高DPI/跨平台测试或学习效果研究。
