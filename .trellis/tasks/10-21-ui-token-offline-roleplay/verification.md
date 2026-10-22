# P04 线下扮演与会话阅读验证记录

## 状态与授权

2026-10-21 用户在四批最终规划摘要后回复“可以”，批准方案并先启动本批。2026-10-22 实施与独立检查完成后，用户回复“可以，还有任务吗？”接纳14张候选；接纳后93/93真实截图、1677/1677完整JVM通过。当前待具体提交清单审阅及提交 / 归档，任务保持 `in_progress`；父任务仅协调，P05等子任务待前序收尾，未发布本批。

## 实施前冻结

- HEAD：`4f457044`，暂存区为空；工作区旧任务搬移、用户文件、manifest 并发历史段落保持。
- `research/worktree-before.json` 保存原始状态及 928 个已跟踪 app / 配置文件的 SHA-256；`research/baseline-sha256-before.json` 保存 78 张接纳后 PNG 的哈希。
- P04 的 PRD / design / implement 和两类各 10 条 JSONL 路径校验通过。实现 / 检查均按本批路径派发；缺少原生注入时由子代理自行加载上下文。
- 不重跑上轮试点冻结验证；本批新增代码和共享影响形成后运行对应检查。旧试点 1642 / 79 的通过结果不记成本批结果。
- 新页缺图探针：`e04_scene_matcha_light` 通过真实内容 / 舞台守卫，verify 因 `original file not found` 预期失败并产生 `app/build/outputs/roborazzi/E04_scene-matcha-light_actual.png`。此时生产 Kotlin 和旧 78 张图均未变，正在捕获全 14 个迁移前证据；不把缺图失败写成截图通过。
- 首轮 14 个 actual 齐备；视觉检查发现阅读背景图在异步加载前捕获，不能充当“背景成功”证据。实现代理先修测试就绪检查，保留首轮证据，待重新核对后才锁定最终 before；生产代码仍未改动。
- 主会话目视 `before/E04_scene-narrow-light.png`，建议展开时正文只剩薄带。后续行为验收要求真实正文行可读、消息可滚动到达，不能仅用 viewport 大于零代替；这属于 O5 / OA2 既有可用性要求。
- 执行时 adb 探测无连接设备；本轮不启动模拟器，也不输出物理性能结论。
- 最终迁移前证据已冻结：`before/manifest.json` 的 14 个哈希与实际 PNG / density / 尺寸一致；`before/verify-results.xml` 的 14 项失败均为 Roborazzi 原始基准缺失，无其他错误。主会话独立复核结果见 `research/before-main-check.json`。阅读背景使用测试固定图片响应并检查生产页实际色带，首轮回退证据另存 `before-initial-fallback/`；布局反例保存于 `before/layout-negative.xml`。产品迁移在上述冻结后开始。

## 验收状态

| 验收 | 当前状态 | 证据 |
| --- | --- | --- |
| OA1 默认令牌 / 实际配对 | 定向及独立审查通过 | 六个产品文件实际迁移、精确源清单与真实绘制颜色测试 |
| OA2 线下操作 / 200% / 48dp | 宿主及独立审查通过 | 两种模式、展开组合至少一行可读、真实回调 / 标签 / 触控 |
| OA3 会话阅读 | 宿主及独立审查通过 | 空 / 混排 / 长文、固定关闭与实际配色；进入保存门槛未改 |
| OA4 共享 / 个性化兼容 | 定向及旧截图通过 | P03、13 皮肤、用户原值与材质 / 稳定 key / 滚动规则；旧78图一致 |
| OA5 14 张 E04 视觉 / 质量 | 已人工接纳并通过 | 用户接纳14图后93/93真实verify、1677/1677完整JVM通过；AndroidTest编译 / Lint / dev构建通过 |
| OA6 交付与边界 | 已整理 | 独立check、spec / 版本记录和审阅材料齐备；待提交 / 归档，真机 / 性能 / 远端CI缺口保留 |

## P04.1 实施与定向验证过程

- 首轮场景编译及布局 / P03 共享回归通过：50 秒、退出 0，`research/p04-1-first.log`。此轮只覆盖当时已有断言，不代表后续新增动作 / 守卫也通过。
- 扩充动作 / 守卫后 19 项中有 4 个失败；实现代理定位为选择器与新增文件登记，并继续修正。实际颜色检查须按绘制 span 的有效颜色，不能把未被使用的 Text 默认色当作实际前景；新增 5 项实际绘制面颜色检查已有通过记录，最终数量以交接报告为准。
- 新增反例 `research/portrait-negative.xml` 证实 200% 字体 / 发送 / 立绘组合下“说话中”与角色标签 Rect 重叠。按 design 中的条件范围纳入 `RoleplayPortraitLayer.kt`，只修状态纵排与自然高度，不扩展资产 / 动效规则；继续复验后再进入阅读检查点。
- P04.1 检查点已通过：42 项定向测试（8 个线下真实行为、5 个实际绘制配色、6 个 P03 无障碍、4 个全皮肤 / 几何、7 个主题 / 玻璃、12 个精确源码守卫），退出 0，53 秒；见 `research/p04-1-checkpoint.log`。组合测试按实际 message_list 边界要求 200% 字体下完整正文行可读；立绘状态重叠反例已转绿。

## P04.2 阅读检查点

- 2026-10-22 恢复时确认原代理在服务繁忙中断前已执行阅读红测：`research/p04-2-negative.log` 的 2 项测试均失败，分别为长文滚动后关闭入口不可见、引号实际文字对比不足；此时生产阅读文件尚未修改。
- 已恢复同一实现代理继续修复阅读局部布局 / 配色，保留导航保存门槛。完整质量检查和 14 个 after 候选待本检查点稳定后生成，不能以 P04.1 的通过结果代替阅读验收。
- 两项阅读红测转绿后，扩展检查 21 项中出现 1 项实际正文颜色失败；确认采样位于正文内边距，问题来自阅读 PANEL / DIALOGUE 的额外内容加深叠加。修复限定在阅读调用点，保留玻璃 wrapper / sheen / 材质，不修改公共 `ImmersiveGlass` / Longform。`research/p04-2-checkpoint-fixed.log` 退出 0、39 秒，21/21 通过：阅读行为 4、实际配色 3、key / contentType 2、源码守卫 12。
- 测试固定 Coil Loader 仅允许合成 URI，显式 opt-in，并只在测试安装过 Loader 时恢复；后续旧截图回归核对其隔离效果。

## 实现交接后的整合结果

2026-10-22，详见 [实现交接](research/implementation-verification.md)。本批默认令牌 / 语义修正限定六个产品文件，无共享 InputBar / Bubble / ImmersiveGlass / Longform 或导航 / 持久化修改。

| 检查 | 真实结果 | 证据 |
| --- | --- | --- |
| 最终定向 JVM | 63/63 通过，0 失败 / 错误 / 跳过，退出0，33秒 | `research/final-targeted-summary.json`、`research/final-targeted-results/` |
| 全截图 verify | 93项真实执行，78项通过；新14因 original PNG不存在 + 计数守卫缺14图，共15个预期失败，退出1，53秒 | `research/after/screenshot-summary.json`、`research/after/all-screenshot-results/` |
| AndroidTest 编译 / Lint / assembleDebug | 全部通过，退出0，3分46秒；Lint 0 error / 55 warning / 3 hint | `research/android-compile-lint-build.log` |
| 原始及迁移前证据 | 原78基准和before14哈希不变，before/after两清单参数 / 哈希通过主会话复核 | `research/before-main-check.json`、两目录 `manifest.json` |
| 候选审阅材料 | 14对 before/after/diff、42张引用无缺图；主会话目视检查大字体 / 阅读 / 背景代表组合 | [审阅页](review/index.html)、[阅读与大字体](review/reading-large-font.png) |

上述测试数量分别登记，不把63/93简单相加当作完整JVM单测通过；本轮没有无理由重跑旧试点1642项。独立 `trellis-check` 已接手全任务范围审查，发生修复时按影响定向复验并更新当前 after 候选，冻结 before 不动。

## 独立审查与规范收口

- [check-report.md](research/check-report.md) 完成全任务范围审查，未发现需要自修的实质问题，未改变产品 / 测试，因此复用本批未变化代码的编译 / Lint / 构建结果，未重复运行整套检查。
- 审查代理独立汇总63项定向XML、93项截图失败类别，并重算旧78与before/after各14哈希，全部一致；对产品 / 登记 / 库存精确路径 `git diff --check` 通过。
- 主会话通过 `trellis-update-spec` 更新 `backend/ui-token-contract.md` 的P04实际布局、固定阅读关闭、嵌套阅读层配色与测试取样契约；`quality-guidelines.md` 补充新增页面缺图actual、冻结前后证据、接纳前守卫仍失败的处理方式。没有修改公共玻璃默认或为缺图加豁免。
- 独立审查当时未接纳候选，后续接纳与最终验证见下节；审查后没有产品或测试修改，因此不重复独立审查 / 编译 / Lint / 构建。

## 用户接纳后的最终验证

- 用户在14张候选接纳问题后回复“可以，还有任务吗？”，批准接纳P04本批14图；只复制审阅actual到 `OfflineRoleplayScreenshotE04Test/`，旧78图原SHA-256不变，没有record、删除用例或放宽阈值。
- 实际运行 `:app:verifyRoborazziDebug --tests "com.example.myapplication.ui.screenshot.*" --console=plain`：退出0、32秒，93项全部通过，0失败 / 错误 / 跳过；测试任务实际执行，92张PNG哈希复验前后一致。证据：`research/verify-accepted-baselines.log`、`research/accepted-verify-result.json`、`research/accepted-verify-results/`。
- 门禁齐备后按父计划执行一次完整 `testDebugUnitTest --console=plain`：退出0、1分1秒，1677项全部通过，0失败 / 错误 / 跳过，测试任务实际执行；92图哈希保持。普通JVM不比较像素，结果与上项截图verify分别登记。证据：`research/unit-final-after-acceptance.log`、`research/unit-final-result.json`、`research/unit-final-results/`。
- `manifest.md` 仅新增本批12行当前接纳记录，原有并发后缀原字节哈希保持；`research/manifest-acceptance-only.patch` 通过 `git apply --cached --check --unidiff-zero`，此时未暂存。完整接受清单及92图哈希见 `research/baseline-acceptance.json`。
- OA1–OA6已满足，后续按具体提交清单完成工作提交、当前P04归档及journal；暂不推送，不归档其他历史任务。P05仍待本批提交 / 归档后开始。
- [提交清单](commit-plan.md) / `commit-plan.json` 已整理两批精确路径：第一批448个实现 / 测试 / 规范 / 已批准规划及证据文件，第二批14个新增PNG与manifest本批12行局部补丁；原有106个范围外状态项和4份任务内Gradle二进制缓存排除。等待本清单一次确认，暂存区为空，没有执行提交。

## 结果边界

本轮P04视觉已人工接纳且本机真实verify通过；先前缺图失败仅作为接纳前过程记录。D01、真实设备 / TalkBack / IME / 系统栏 / 真正玻璃及物理性能与远端新视觉CI均单独待补，不能以宿主结果替代。构建版本维持2.2.12-dev，不改版本 / dev.json，不发包或推送。
