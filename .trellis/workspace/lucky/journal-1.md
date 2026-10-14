# Journal - lucky (Part 1)

> AI development session journal
> Started: 2026-07-19

---



## Session 1: 2.2.4-dev 首次启动合规门禁与发布收尾

**Date**: 2026-07-22
**Task**: 2.2.4-dev 首次启动合规门禁与发布收尾
**Branch**: `main`

### Summary

完成首次启动统一18+合规门禁、条款版本持久化、设置页查看、未确认状态下暂停更新与AI动态任务；通过单测、AndroidTest编译、Lint和Debug构建，APK上传Cloudflare R2并校验哈希，更新内置更新JSON，推送main。归档本次Trellis任务。

### Main Changes

- Detailed change bullets were not supplied; see the summary above.

### Git Commits

| Hash | Message |
|------|---------|
| `08bc26b1` | (see git log) |
| `c6a37984` | (see git log) |
| `f7a016d2` | (see git log) |

### Testing

- Validation was not recorded for this session.

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 2: 2.2.5-dev 稳定性与可用性修复

**Date**: 2026-08-07
**Task**: 2.2.5-dev 稳定性与可用性修复
**Branch**: `main`

### Summary

完成群聊多角色回复可靠性、角色音色弹窗滚动、普通聊天与 RP 单条消息删除；通过全量测试、Lint 与 Debug 构建，并发布 2.2.5-dev 到 R2 和内置更新。

### Git Commits

| Hash | Message |
|------|---------|
| `c193510f` | (see git log) |
| `bbd02982` | (see git log) |
| `860c3408` | (see git log) |
| `83bb7167` | (see git log) |
| `59844460` | (see git log) |

### Status

[OK] **Completed**


## Session 3: Narra UI 重设计收口与归档

**Date**: 2026-09-07
**Task**: Narra UI 重设计收口与归档
**Branch**: `main`

### Summary

Moments、PhoneCheck、系统悬浮翻译及全 App UI 收口完成；1526项JVM和97项设备用例完成验证。用户接受跨应用悬浮/截屏与真实软键盘未实测边界，要求归档并随2.2.11-dev发布。

### Git Commits

| Hash | Message |
|------|---------|
| `7b7903a1` | (see git log) |

### Status

[OK] **Completed**


## Session 4: 2.2.12-dev 线上气泡开关与朋友圈评论间距

**Date**: 2026-09-08
**Task**: 2.2.12-dev 线上气泡开关与朋友圈评论间距
**Branch**: `main`

### Summary

完成全局线上背景玻璃气泡开关和朋友圈评论预览间距修复，三个任务已归档。

### Main Changes

- 新增默认开启的线上背景玻璃气泡偏好，旧配置兼容，关闭后保留背景与主题气泡。
- 评论预览改为自然行高、上下各 2dp 内边距及 4dp 条目间距，保留单击和长按交互。
- 同步主题配置规范、版本记录及归档后的上下文路径。

### Git Commits

| Hash | Message |
|------|---------|
| `b35cc403e12f3a13d645aa145fe0e9a20d562356` | (see git log) |
| `4f24dc80bb71ea256ac176efdf6f0feb43d7ebc0` | (see git log) |
| `0eb14782646620182c59dbd4176d1822f08e3714` | (see git log) |

### Testing

- [OK] 1534 项 JVM 测试通过；主源码和 AndroidTest 编译、Lint、dev 构建通过。
- [OK] Android 16/API 36 模拟器 18 项界面测试通过；定向复跑 5 项取回六张截图并完成视觉复核。

### Status

[OK] **Completed**

### Next Steps

- 2.2.12-dev 尚未发布；需要发包时按既有 dev 发版流程另行执行。


## Session 5: v2.3 视觉回归安全网与基线核验（实施轮）

**Date**: 2026-10-14
**Task**: v2.3 视觉回归安全网与基线核验（实施轮）
**Branch**: `main`

### Summary

接入 Roborazzi + Robolectric，落地首批 76 个截图用例与 78 张基准（人工确认 + CI 复验一致）、负向门禁与 CI 截图门禁；性能与设备项保持未验收。

### Main Changes

- 接入 Roborazzi 1.76.0 + Robolectric 4.17：固定 API 35、零像素阈值、固定时钟/字体/图片/尺寸，基准位于 app/src/test/snapshots。
- 首批 76 个用例（H01–H04、B01–B04、C01）与 78 张基准（人工确认 + CI 复验一致）、用例登记表 ScreenshotCases 与计数守卫 ScreenshotBaselineGuardTest、manifest 运行清单。
- fixture 调色板改为调用生产 deriveImmersiveGlassPalette（仅可见性 private→internal），19 张 P03/B02 基准按生产派生路径定向重录。
- CI 新增独立 screenshots 门禁（ubuntu-24.04、push/PR 只 verify、record 仅手动 dispatch、失败上传差异报告）；远端 run 38573095548 全部通过（含 setup-android 外部漂移修复 073dba6c）；同步更新质量规范与任务研究文档。

### Git Commits

- `c5835620` test(screenshot): 接入 v2.3 视觉回归安全网与 78 张已确认基线
- `073dba6c` fix(ci): 修复 setup-android 安装已下架 tools 包导致的门禁失败

### Testing

- [OK] record 1 次 + verify 连续两次真实通过（--rerun-tasks 35 tasks 全量执行）；78/78 基准 SHA-256 前后零差异。
- [OK] 负向门禁三轮：1px 差异与缺失基准均真实失败（exit 1 + compare/actual 产物），恢复后通过。
- [OK] 全量 1613 项 JVM 单测 0 失败/0 跳过；assembleDebug 与 lintDebug 通过。
- [OK] 远端 CI 已触发：run 38572287401 因 setup-android 外部漂移失败（修复 073dba6c）；run 38573095548 screenshots（零阈值，78 张基准两宿主一致）与 quality（assemble/单测/lint）全部通过。
- [OK] 无真机：性能归档核验为合成 fixture（decidable=false），A6 与 D01 保持未完成。

### Status

[OK] **Completed**

### Next Steps

- 78 张基准已人工确认（2026-10-14）并随 c5835620 提交；远端 CI 已通过（run 38573095548）。
- 连接固定真机后补录 D01 与 S0–S3/F1–F3/N1，再更新 performance-baseline.md。
