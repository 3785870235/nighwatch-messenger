# UX4 原创素材与提示词

2026-10-12。使用内置 image_gen；未使用 API CLI。Fu / Glimmer 以 v1 原创形象为身份参照。Luo 是现有故事中守档人的新原创立绘。提示词按以下规格执行；图集不是人物骨骼动画，运行时按镜头选择姿态并叠加漂浮、眨眼、视线和环境动态。

## 提示词集

- **fu-poses.png** — identity-preserve / production transparent 2x2 full-body sprite atlas. Same blue-black short hair, amber eyes, elf ears, navy gold-trimmed cloak, ivory shirt, brown mail satchel. Four poses: attentive with envelope, worried reading parchment, offering a sealed letter, relieved discovery gesture. Equal cells, full silhouettes, transparent gutters; no background, labels, UI, franchise references. Input: v1/characters/fu-messenger.png.
- **glimmer-poses.png** — identity-preserve / transparent 2x2 atlas. Same cream origami wings, gold rims, dark navy face, cyan eyes, warm lantern chest, blue ribbon tail. Listening / inquisitive / startled / delighted poses. First generation had unsafe overlapping quadrant bounds and was not selected. Layout revision: square canvas, shrink each full figure to 65% of its cell, at least 17% padding on every side, no geometry crossing centre lines, preserve four poses and identity. Input: v1/characters/glimmer-spirit.png, followed by generated first atlas for layout repair.
- **luo-poses.png** — illustration-story / original middle-aged East Asian archive keeper, tied silver-grey hair, bronze spectacles, dark plum scholar coat, ivory collar, blue ledger, brass key. Four full-body poses: attentive, skeptical, concerned reading missing page, relieved offering parchment. Transparent 2x2 atlas, identical face and costume, no text or scenery.
- **moonlit-alcove.png** — stylized-concept / landscape 16:9 painterly midnight academy reading alcove. Tall arched window on left with clear indigo sky; bookshelves, brass astronomical ornament, desk and three unlit candles on right. Quiet dark lower quarter for dialogue. No people, clouds, candle flames, UI or labels; clouds and flames are separate runtime layers. Original setting, midnight blue and warm amber palette.

All selected originals are preserved. visual-manifest.json records actual pixel dimensions, RGBA transparency, and SHA256; equal atlas cells are selected by game code without modifying PNGs. First rejected spirit atlas remains in the generator output directory only.

## 声音

16种原创合成素材，生成器 tools/generate-ux4-audio.py。44.1kHz / stereo / 16-bit PCM。分层滤波噪声、共鸣泛音、包络与短混响，包含 wind、embers、paper、page、step、seal、click、bell、spirit、wonder、rune、open、close、success、question、transition。不是实地录音；不含他人音乐或录音。参数、峰值、RMS、时长与SHA256见 audio/manifest.json。
