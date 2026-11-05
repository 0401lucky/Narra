# P05 会话设置侧栏：执行恢复核对

- 日期：2026-10-22；版本归属 v2.3（开发中，未发布），构建覆盖仍为 2.2.12-dev。
- 用户本轮明确授权继续现有 P05；四批规划已批准，P04 已独立验收、提交并归档。本轮不新建任务，不启动协调父任务。
- 启动前分支 `main`，HEAD `f29f0c98`，相对 `origin/main` ahead 8；暂存区为空。最近提交与用户提供的 P04 四项提交一致。
- `get_context.py` 报告 109 项既有未提交改动，生产代码、JVM / AndroidTest Kotlin 源码启动前没有 diff；原有任务搬移、归档引用、web / promo / APK / 截图等均不纳入 P05。
- `app/src/test/snapshots/manifest.md` 原有并发修改单独保存于 `manifest-existing-diff.patch`；本批不回滚、不整文件暂存。
- 已冻结 92 张已接纳 PNG 的 SHA-256：`accepted-png-hashes-before-p05.txt`（清单自身 SHA-256：`6800a7776ef41f44fdc2d3944337aa45bbb1631bbcdd53557e83bc8299d48f84`）。
- 原始 dirty / staged 路径清单：`workspace-start-status.txt`、`workspace-start-staged.txt`。
- 已读取父任务 PRD / design / implement、sidebar-entry / coverage-and-queue 研究，以及 P05 PRD / design / implement / implement.jsonl / check.jsonl / task.json。
- 已对 `.trellis/tasks/10-21-ui-token-session-sidebar` 执行 `task.py start`，状态 `planning → in_progress`；当前任务源为本会话，不启动父任务。

## 执行约束

实现与独立检查分别派发 Trellis 对应子代理，提示首行为当前 Active task 路径；子代理不得递归派发同类代理。实现代理单一持有 P05 五个生产文件及本批截图 / 行为 / 守卫测试，主会话负责协调、记录与审阅。

必须先取得全部 18 张真实迁移前图，再修改产品代码；独立窗口捕获真实 root。新基准缺图 verify 保持失败，不 record、不 verifyAndRecord、不降低阈值。after 候选与前后对照供用户视觉审阅，人工确认后才接纳。

本次目标停止在实现、独立检查与可审阅候选。视觉接纳、具体提交清单确认、最终截图复验、归档和 journal 仍按后续门槛执行；不推送、不发包、不处理 P10 / P11。
