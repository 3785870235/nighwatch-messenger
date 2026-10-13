> 真实历史开发记录；其中阶段性未完成项不代表最终状态。最终状态见根目录README。

# 第二组角色图集交付（2026-10-12）

依据：docs/CHARACTER_ART_PROMPTS.md，2026-10-12 22:26:52 版本。工具：内置 image_gen；未使用 API CLI。只生成素材与文档，未修改 Java 或动画坐标，未发布。

## 最终素材

| 角色 | 文件 | 实际尺寸 | 单格 | 四格顺序（左上、右上、左下、右下） |
|---|---|---|---|---|
| 芙 | assets/game/v2/characters/fu-poses-b.png | 1132×1390 | 566×695 | 空手关切；回头警惕；按信犹豫；笑并抓信袋肩带 |
| 洛 | assets/game/v2/characters/luo-poses-b.png | 1132×1390 | 566×695 | 背手审视；扶镜勉强；转身归还卷宗；翻书讲解 |
| 微光 | assets/game/v2/characters/glimmer-poses-b.png | 1254×1254 | 627×627 | 平展专注；侧倾偷看；收翼前倾好奇；抬翼困惑 |

工具返回的实际尺寸与建议尺寸不完全相同，但均为偶数、可按宽高各一半切图。未对图片进行额外缩放、裁切或抠图，保留内置生成器提供的 alpha。旧 poses.png 三张未覆盖。

## 检查与接入

- 目视检查人物身份、服装、四种姿态、完整四肢/翼尾及无背景/网格/标题。
- Pillow只读取像素和元数据，不编辑图像。详细尺寸、SHA256、每格alpha>16主体范围见 CHARACTER_B_VALIDATION.json。
- 三张均 RGBA，alpha范围0–255；中央两列/两行最大alpha=1（生成器残留的极低透明度，非严格全0），没有可见主体跨越分格；未声称每个背景像素都绝对为0。
- 洛首版左下袍角越界，微光首版左上翼尖越界，均未交付为游戏素材，已通过内置图像编辑生成留白修正版。
- 各图留白与旧图不同，Claude接入时需按可见主体调角色显示尺度/脚部基线，避免仅按整格高度造成换姿势缩放跳动。
- 微光必须重新量四帧眼睛中心、翼根/翼尖和尾根；不要直接沿用旧LIMBS/640网格眼睛表。新素材为静态姿态，翅膀弯曲和鼠标注视尚未验证。
- 本轮没有修改pose()。不能仅因文件存在就声称8帧均已接入游戏。按原交接由Claude接入新帧并检查剧情选择与动画。
- 洛左下不画书架，使用归档动作供运行时场景组合。避免把固定家具烘焙到透明角色里。
- 芙留白较窄但主体未触边，洛留白更宽。微光前倾是有限的二维姿态表现，非骨骼动画。
- 正式文件已在assets目录，无需从scratch复制；生成源保留于Codex generated_images目录。

## 接入状态（Claude，2026-10-13）

- 微光第二组四帧已接入为第 4–7 帧（专注、偷看、好奇、困惑），台词名字表在 `AcademyCharacters.NAMED_POSES`。四帧都重新量了翼根/翼尖/尾根（`LIMBS` 第 4–7 行），专注、好奇两帧另量了眼睛中心（`EYES` 第 4、6 行，第二组图眼睛比第一组小，带自己的缩放）；偷看、困惑两帧眼神是画出来的，不做鼠标跟随也不眨眼。整组按第一组的体量放大 `SECOND_ZOOM`=1.12，换姿势时大小不跳。
- 五章 27 句微光台词都按意思指定了姿态（`.pose=`），StoryPoseTest 锁定。困惑帧是皱眉的那张，图上没有问号（提示词明确不要问号）；序幕第四镜的「?」是程序另画的。
- 芙/洛的行走与口型用的是现有画作的程序形变（腿按胶囊权重绕髋转动、前摆脚抬起、身体随步下沉；嘴沿画出的唇线张开），不是逐帧手绘。行走只用每人第一组第 0 帧（持信 / 抱书）；走到位后 0.3 秒溶解到台词指定的姿态。
- 检查截图：`work/claude-scratch/motion/`（步态条、腿部放大、每个姿态嘴闭/半开/全开）、`work/claude-scratch/story2/`（每句台词、入场序列、实际显示尺寸的嘴部放大、序幕走入）。

## 生成来源

- 芙：exec-117a6cdf-7305-4004-ae2f-ef8abcbbf178.png
- 洛：初稿exec-e0578432-e685-4bdf-974c-7410dbcce9ca.png → 最终exec-b7951517-d1bf-4f03-b7d7-412204771b75.png
- 微光：初稿exec-d6c5e623-c919-497e-b264-bbb4795debaa.png → 最终exec-5cf88941-d513-4d47-98de-343ebfb6c29c.png
- 源目录：<local-path>
- 身份参考：对应角色原assets/game/v2/characters/{name}-poses.png。首轮生成均显式传入对应本地图像，已查看。

## 最终提示词与迭代

### 芙

```text
Use case: stylized-concept. Asset: production-ready second 2x2 pose atlas for Fu in an original midnight magical academy investigation game. Input image is an identity and costume reference, not poses to copy. Preserve EXACTLY this young adult elf messenger's face, short tousled teal-black hair, pointed ears, navy travelling cape with brass constellation embroidery and blue lining, ivory loose-sleeved shirt, dark trousers, brown boots, crossbody brown report satchel, brass belt lantern, keys, seal ornament, body proportions and painterly polished storybook rendering.
Create FOUR DISTINCT NEW poses in strict equal 2x2 quadrants, reading order:
TOP LEFT: frontal full body, both hands empty resting at sides, cape naturally gathered, chin slightly lifted, attentive concerned expression looking toward viewer.
TOP RIGHT: full body three-quarter BACK view, turning to look OVER SHOULDER at viewer, eyebrows lowered in quiet alertness, cape slightly flaring mid-turn.
BOTTOM LEFT: frontal full body, one hand pressing a wax-sealed letter against chest, hesitant troubled expression, gaze lowered.
BOTTOM RIGHT: frontal full body, clearly warm smiling eyes and mouth, one hand on hip, other grasping satchel strap.
Requested canvas 1256x1544 portrait, even width and height. Four equal-sized cells. Every figure including hair, cloak, hands, envelope and boots FULLY contained within its own quadrant; each occupies about central 80 percent of cell height. Generous transparent padding on ALL sides within each cell, especially the horizontal center divide. Same figure scale across four cells. No overlap, no cropped boots, no extra limbs. Real transparent RGBA background, no checkerboard painted into image, no scenery, floor, cast shadow, labels, text, borders, gridlines or watermark. No large magical effects. Preserve reference palette and design faithfully.
```

### 洛首次

```text
Use case: stylized-concept. Asset: production-ready SECOND 2x2 pose atlas of Luo, archivist of an ORIGINAL midnight magical academy investigation game. Input image is strict character identity, clothing and painterly style reference. Preserve same mature silver-haired elf man with low tied silver hair and face-framing locks, pointed ears, fine gold-rimmed spectacles, severe thoughtful face, long deep plum scholar coat with pale lining and fine brass embroidery, ivory shirt wide cuffs, dark sash/trousers, brown tall boots, brass waist key, navy ledger brass corner fittings. Do not age him younger or redesign costume.
Four DISTINCT new full-body poses, exact equal 2x2 cells, reading order:
TOP LEFT: three-quarter side view, hands clasped behind back, ledger may be held behind back, chin slightly tucked, scrutinizing viewer over spectacles, reserved appraising expression.
TOP RIGHT: facing slightly right, one hand adjusts bridge of glasses, other arm holds CLOSED navy ledger at side, grudging reluctant concession, lips pressed together.
BOTTOM LEFT: three-quarter BACK view, torso turned away, head partly in profile, a hand extending a rolled parchment as if returning it to an OFF-CANVAS shelf; guarded evasive expression. Do NOT render shelf or furniture, only character and held parchment. Ledger tucked under other arm.
BOTTOM RIGHT: faces viewer with open ledger resting on one hand, opposite index finger pointing at a line on the page, earnest explaining expression. Page marks only abstract illegible texture, no actual lettering.
Requested canvas 1256x1544, even dimensions. Every full figure INCLUDING boots hair coat and held objects strictly INSIDE its own quadrant. Each pose centered at same figure scale, about central 80 percent of cell height. Transparent margins on all sides and a generous clear central horizontal and vertical gutter. Exactly four characters. Clean silhouettes, rich restrained detail, moonlit navy warm brass ivory palette. Genuine transparent RGBA. No environment, floor, cast shadow, grid, border, text labels, lettering, watermark, UI. Preserve original design faithfully.
```

### 洛布局修正

```text
Edit target is the supplied Luo 2x2 transparent character atlas. Fix ONLY atlas layout and padding. Preserve all four existing poses, expressions, face identity, costume colors, boots, hands, books, scroll and illustration style exactly. The bottom-left figure's cloak currently crosses the vertical center line and the top/bottom rows touch. Recompose each COMPLETE figure uniformly 20 percent smaller within its OWN EXACT quadrant, centered in that quadrant. Make the same scaling adjustment to every figure, preserving comparable full-body heights. Each quadrant must have at least 8 percent cell-width/height transparent padding on every edge. The central horizontal and vertical bands must be completely empty transparent alpha=0, at least 50 pixels wide each. Bottom-left coat MUST terminate safely LEFT of the vertical middle, and all top-row boots must end safely ABOVE horizontal middle. Four equal quadrants on an even-dimension portrait canvas. Real transparent RGBA, no scenery or shadows, no text, no gridlines. Do not remove costume parts to fit. Shrink and reposition whole figures, do not crop them.
```

### 微光首次

```text
Use case: stylized-concept. Asset: SECOND production-ready 2x2 transparent pose atlas for Glimmer, original folded-paper archive spirit. The supplied image is the exact identity, proportions, materials and rendering reference. Preserve this same NON-humanoid parchment creature: small midnight-blue round face in pointed brass frame, two cyan luminous eyes with clearly visible eye centers, folded ivory paper wings with navy underside and delicate gold constellation motifs, compact paper torso and warm gold chest core, slender blue-and-gold ribbon tail. Same painterly polished storybook shading. Do not redesign into owl, robot, butterfly, angel or humanoid.
Four NEW pose variants in EXACT equal 2x2 quadrants:
TOP LEFT: level hovering, both wings spread symmetrically left/right, eyes wide attentive directly forward, steady warm chest core.
TOP RIGHT: gently banked sideways, one wing raised one wing lowered, eyes glancing to one side as if peeking uncertainly, keep body centered.
BOTTOM LEFT: leaning a little toward viewer, wings half folded close beside body, big bright curious eyes. Keep consistent head-to-body scale, not giant-head chibi redesign.
BOTTOM RIGHT: BOTH wings lifted high, subtly uneven half-closed eyes expressing puzzled uncertainty (NOT smiling closed crescent eyes), tail curves upward from underneath while remaining below/alongside body. No question-mark symbol.
Request square 1256x1256 RGBA, even dimensions. Each complete creature including wings and ribbon in central 78 percent of its OWN quadrant with transparent padding on all sides; NO wing, ribbon or glow touches or crosses center divides. At least 45px fully transparent central vertical/horizontal gutters. Preserve reference body proportions and relative face/core placement so programmer can recalibrate wing roots and mouse-tracking eyes. Every pose contains exactly one creature, two wings, two eyes, one tail. True transparent background. No scenery, floor, cast shadows, checkerboard painting, gridlines, frames, labels, letters or watermark. Local small controlled glow only, no particles or light trails: animation added by code.
```

### 微光布局修正

```text
Precise atlas layout edit of the supplied transparent 2x2 Glimmer pose sheet. KEEP all four current expressions and poses, colors, body details and paper textures. The TOP LEFT right wing crosses the exact vertical midpoint and is unusable when slicing. Fix by scaling EACH of the four COMPLETE creatures down uniformly to 78% of its current size and recentering each in its own exact quadrant. Do NOT redraw or crop wing tips. Keep all four at comparable head/body scale. Canvas square even dimensions, genuine transparent alpha. Four equal 2x2 cells. Use a large entirely transparent cross-shaped gutter through the middle at least 90px wide horizontally and vertically. All creatures and every last wing tip, tail and glow must be completely inside their own cell, at least 35px from cell edge. Specifically first creature maximum x must remain well to the LEFT of center minus 35px; its left wing must also have padding. All four retain full intact wings and tails, preserve reference proportions. No added gridlines, labels, symbols, scenery, floor, shadow or background. Only scale and reposition the complete four illustrations.
```

