# 原创人物素材与运行时动作 v1

2026-10-12，Codex 使用内置 imagegen 生成，真正透明RGBA，保留缓存原件；项目副本SHA256见上级manifest.json。

- `fu-messenger.png`：1024×1536，巡夜信使芙。当前序幕中的轻量待机呼吸，尚无走路、挥手或口型。
- `glimmer-spirit.png`：1312×1199，档案精灵微光。纸翼由Java分层摆动，整体漂浮/呼吸光，瞳孔平滑追踪游戏窗口内的鼠标。不是生成视频，PNG本身是单张透明姿态素材。

程序仅消费素材与鼠标/Reading状态；不生成仿真输出或改变电路。精灵不提前告诉未知回应。减少动效会停止漂浮/纸翼，并直接更新视线。

## 提示记录（本次内置工具最终提示）

信使：Use case: stylized-concept. Asset type: transparent full-body character cutout for an ORIGINAL 2D midnight magical academy investigation game, to be composited in a Java game scene. Subject: one young adult elf postal messenger named Fu, warm and curious, short slightly tousled teal-black hair, pointed ears, navy hooded academic travelling cape, ivory shirt, brass trim, practical satchel full of sealed reports, holding one ivory report envelope stamped with a small ORIGINAL geometric gold seal. Painterly polished storybook game illustration, softly shaded 2.5D with clean readable silhouette, rich but restrained details, matching moonlit navy/teal university archive backgrounds with warm brass lighting. Entire character including boots inside frame, generous transparent margins, 3/4 pose facing slightly left toward scene center, inviting calm expression, one character only. No lettering, no watermark, no UI, no background, no floor, no circuit diagram, no existing franchise appearance. True transparent alpha. The character is a messenger of reports, not a source of magic lightning. Suitable for subtle runtime breathing and layered floating effects; deliver one clean pose, not an animation sheet.

精灵：Use case: stylized-concept. Asset type: transparent companion sprite for an ORIGINAL 2D midnight magical academy investigation game. One small archive spirit named Glimmer: an endearing floating creature formed from folded ivory parchment, two broad asymmetrical folded-paper wings, a small midnight-blue round face with two gentle cyan luminous eyes nestled in a brass octagonal frame, tiny dangling ribbon tail, one warm golden glow held inside its paper chest. Expressive but simple, curious listening pose, original design, NOT a recognizable franchise fairy. Painterly polished storybook 2.5D shading, clean silhouette, navy/teal/cyan/ivory/brass palette matching a moonlit university archive, delicate gold highlights. Entire one creature inside frame, generous genuinely transparent margins, no background or floor. No lettering, no logo, no watermark, no UI, no circuit diagram. Small controlled glow near the body, no giant particles baked in: motion, dust, pulses will be generated in code. One clean pose only, not sprite sheet.

原件缓存：`<local-private-path>`，信使 `exec-f79220b3-5241-4396-b6dc-d8846c8f7a38.png`；精灵 `exec-cdbd8e67-ecea-4922-80a0-134ab47dd28c.png`。运行时只读项目内副本。
