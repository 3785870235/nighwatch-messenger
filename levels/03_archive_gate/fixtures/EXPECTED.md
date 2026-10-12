# 03_archive_gate 判题夹具：期望结果
# 判定 = 结构检查（端口/允许元件）+ Digital 测试（tests.dig 四组合）。任一失败即不通过。
# "CLI 期望" 为对 v0.31 `CLI test <fixture> -tests ../tests.dig` 的预期，尚未实际运行验证。

fixture          | 最终判定 | 应被哪一层拦截              | CLI 期望
and_correct.dig  | 通过     | -（拓扑与 reference 不同）  | 4/4 通过
or_wrong.dig     | 不通过   | 结构（Or 不在 allowed）+ 逻辑 | 01/10 失败：期望 0 实际 1
const_one.dig    | 不通过   | 结构（Const）+ 逻辑          | 00/01/10 失败：期望 0 实际 1
const_zero.dig   | 不通过   | 结构（Const）+ 逻辑          | 11 失败：期望 1 实际 0
unconnected.dig  | 不通过   | 逻辑/模型错误（PERMIT 无驱动）| 已验证：报“输入端口未连接”，无“实际 0”（ArchiveGateTest）
missing_port.dig | 不通过   | 结构（缺 PERMIT）             | 已验证：报“缺少输出端口 PERMIT”（ArchiveGateTest）

# 注：or_wrong / const_* 被结构层先拦截时，仍应单独以"仅逻辑测试"模式验证其逻辑反例，
# 以确认逻辑层本身也不会误通过（不依赖结构层兜底）。
