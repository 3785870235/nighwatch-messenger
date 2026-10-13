# 角色动态更新版验证

当前JAR SHA-256：`aebf594f60e83e9480e0e58326c949b4f3ec4fec7bd12e7fc8cb4aaa04bce45a`。

- 发布前重新执行完整Maven单元测试（239个套件）：**880项，失败/错误/跳过均0**。[本次完整输出](validation/motion-full-unit-tests.txt)
- 本次便携Java+发布JAR（classpath无target/classes）五章直接开放、通过标记、衔接和返回首页检查：**32项通过**。[本次输出](validation/motion-package-unlock.txt)
- 复用角色锚点112项检查：[输出](validation/motion-AcademyPoseAnchorCheck.log.txt)。Codex另用同一新JAR渲染微光八姿态，exit0、无缺图提示。
- 本次额外重跑Gui、P2Gui、UxGui、Ux3、Ux4、Story1、Chapter4、Chapter5、HomeMotion、PoseAnchor共10组检查，全部exit0，且没有缺图替代绘制警告。统一显式指定academy.root，修正旧检查工作目录引起的证据缺口。保存失败测试会故意注入错误，错误日志不单独代表流程失败。[结果表](validation/motion-gui-results.json)
- 当前截图由本次JAR运行/渲染取得，使用临时进度；不等于完整人工通关。保留此前版本验证作为历史证据，旧878/24数字不代表当前版本。

行走、口型是原画的程序形变，不是逐帧手绘或语音对口型；微光偷看/困惑姿态保持画中眼神。尚无高DPI/跨平台全覆盖、音频主观验收或学习效果研究。视频待团队录制。

同一JAR内全部1731个产品class文件与当前target/classes逐字节一致；源码及关卡快照在检查后保持不变。GUI使用测试驱动类加发布JAR，classpath不含产品target/classes。
