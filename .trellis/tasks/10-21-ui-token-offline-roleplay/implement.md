# P04 执行计划

2026-10-21 用户在最终摘要后回复“可以”，本批已获实施批准；工作区和旧 78 张基准已冻结于 research，现按两个检查点实施，候选接纳仍需人工确认。

1. 读取本任务 JSONL → PRD → design → implement；核对当前工作区和已接纳基准哈希，探测设备但不强制启动模拟器。
2. 由 `trellis-implement` 为真实 `RoleplaySceneContent` / `RoleplayReadingMode` 增加 E04 fixture、登记和迁移前 actual 探针，冻结 14 个 before。新页缺图失败如实保留。
3. P04.1 迁移场景、顶栏、建议、空态与提议；验证实际承载面、语义、48dp、大字体和真实回调，精确更新源码守卫。条件修改共享输入 / 气泡时定向回归 P03。
4. P04.1 稳定后实施 P04.2 阅读默认几何 / 语义，补空态 / 混排 / 关闭 / 长文滚动测试；不改 P05 保存导航。
5. `trellis-check` 独立检查 / 定向修复，禁止 record、弱化门禁或自动接纳；整理 14 个 after / diff 及所有共享旧图变化供人工审阅。
6. 用户确认后接纳本批 PNG / manifest 新记录，verify 全部已接纳图；按父计划完成 Lint / dev 构建、受影响单测与 AndroidTest 编译，记录真实数量与设备 / CI / 性能缺口。
7. 主会话更新必要 spec / 版本记录、审阅具体提交清单，再按 Phase 3.4 和 finish-work 提交、归档、journal。只完成本批，不自动越过 P05 的检查 / 视觉接纳。

定向检查优先 `RoleplaySceneContentTest` / `RoleplayScreenQuickActionsTest` 对应宿主覆盖、新增阅读与 P04 无障碍测试、`RoleplaySemanticColorsTest`、`RoleplayThemeSwitchUiTest`、`UiTokenSourceGuardTest`；未改的玻璃 / 滚动算法只复用原回归，不写常量镜像测试。

最终命令与停止 / 回退规则以 [父执行编排](../10-21-ui-token-accessibility-expansion/implement.md) 为准；新增截图类建议 `OfflineRoleplayScreenshotE04Test`，每登记一图对应一个 `@Test` 方法。没有实际执行前不写通过；失败先定向复验，完整试点结果不重复用作本批结论。

## 2026-10-22 交付状态

- [x] 工作区与78基准冻结、真实缺图actual探针、14个最终before；初次背景fallback另存，未拿早期旧actual冒充背景成功。
- [x] P04.1 场景 / 建议 / 立绘组合修复，42项检查点通过；P04.2 固定关闭与实际阅读配色修复，21项检查点通过。
- [x] 产品冻结后最终定向63项通过；全截图93项实际执行，旧78项通过，新14及缺图守卫为15个预期失败，未关闭或豁免门禁。
- [x] AndroidTest编译、Lint与dev构建通过；独立trellis-check无代码问题，复核XML和before/after各14及旧78的哈希一致。
- [x] 主会话更新必要spec / 版本记录 / 验证记录并准备14对审阅图；资料见 [verification.md](verification.md) 与 [check-report.md](research/check-report.md)。
- [x] 用户回复“可以，还有任务吗？”接纳14张候选；只新增已审阅14张PNG、manifest新增12行，旧78图不变。真实verify 93/93通过，接纳后完整JVM 1677/1677通过，0失败 / 错误 / 跳过，92图哈希不变。
- [ ] 最终验证通过后审阅本批具体提交清单，完成提交 / 归档 / journal；此后才能开始P05。设备 / 远端CI缺口继续单列。
