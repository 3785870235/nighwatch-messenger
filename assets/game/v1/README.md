# 正式美术资产 v1

2026-10-11，Codex使用内置imagegen生成。此处PNG是正式背景/状态素材，不是界面截图；真实界面中的中文、按钮、电路、记录和状态文字由Java/Swing绘制。

全部1672×941（工具返回尺寸，接近16:9）；保留原图，无后期裁剪/改绘。SHA256见manifest.json。旧assets/previews不覆盖。

- menu-background.png：原创午夜大学档案馆，左侧深蓝留白，右侧金色门廊。用于首页/关卡选择背景，保持左侧UI可读。
- archive-closed.png：正面完整双扇档案门，门扇关闭；用于有效许可0。
- archive-open.png：以closed为编辑目标，仅请求打开门扇、露出暖色档案室，保持机位/建筑；用于有效许可1。人工查看两状态，均无文字或电路线，门状态清楚。生成编辑保留总体构图，细节不保证逐像素相同；首版用立即状态切换而非像素差分/渐变交叉动画。

未知/未运行/未连接/错误采用独立文字与图标/遮罩，不宣称关闭画面就是有效0。图像层不得按A/B自行判真值。

2026-10-12 动态接入更新：新增 archive-interior.png（1672×941），以open图为编辑目标移除门扇、保留暖色室内与框架。运行时用它作后景，把closed图门扇纹理分层绕门轴连续展开；不再把整张开/关图硬切当作交付动画。open图仍用于可信验证后的过场。内景原件exec-915673e1-265e-49d5-8900-a22c5f41024f.png保留在上述缓存目录。角色在characters目录，详见其中README；manifest现在包含四场景与两角色。

## 生成提示记录

menu：production-ready original 2D knowledge puzzle game background，midnight navy/teal、brass gold、cream，原创moonlit university archive observatory，左55%低细节暗留白、右45%档案门与阶梯；禁止文字/按钮/电路。

closed：同风格正面居中closed archive door，完整不透明双门、金属饰边、石拱、左右灯和窗、外围书架；禁止人物/物理钥匙/电流/文字/界面。

open编辑：reference=archive-closed.png，保留相同尺寸机位及外围建筑，只打开中央双门，门后暗暖色档案阅读室；禁止人物/文字/电路、物理钥匙消失或虚构电学。

## 生成原件

缓存目录<local-private-path>
menu=exec-9925ef6a-5f02-4364-8b28-b5802bb12d3e.png；closed=exec-eb2a435a-4f49-496e-9e1c-816855fd2ef8.png；open=exec-6dcc79dc-c29e-464f-b885-0aadb55838e2.png。
代码只读取项目assets/game/v1路径，不依赖缓存。
