# 2.2.6-dev 应用性能与稳定性专项：实施计划

## Completion Definition

AC1～AC12 全部满足：建立可重复基准和脱敏诊断闭环，阻断代码级确认的 P0 进程风险，
至少一个 Trace 证实热点获得可重复改善，核心功能和数据一致性无回归，所有可执行质量
门禁均有真实结果。缺少物理设备时不得把本任务标记为完整性能验收通过。

## Pre-Start Gate

1. 按设计创建五个独立子任务，并为每个子任务完成 PRD / design / implement 和真实
   `implement.jsonl` / `check.jsonl`。
2. 运行所有父子任务 `task.py validate`。
3. 展示最终规划摘要；只有 lucky 在该摘要之后明确批准，才运行 `task.py start`。

## Ordered Checklist

### Wave 1：基准、Trace、Profile 与诊断基础

1. 加载 `trellis-before-dev` 和基准 / 质量 / 日志规范。
2. 固定 benchmark 工具版本、构建 JDK、设备信息、刷新率和温控记录格式。
3. 增加 baseline-only fixture、合规准备、后台污染隔离和稳定 ready 合约。
4. 修正启动 / Profile 等待为强断言，新建 S0～S3、F1～F3、N1 场景。
5. 增加 trace section 和独立 Compose report 归档。
6. 实现本地脱敏 `ApplicationExitInfo` 诊断、限量轮转和主动导出；保存优化前原始结果。

### Wave 2：按文件所有权执行 P0 / P1 修复

#### A. 启动、设置共享与导航

1. 为 AppGraph 启动任务和 DataStore 读取建立局部异常 / 降级策略及故障注入测试。
2. 建立应用级共享设置 StateFlow，将解析移出 Main。
3. 提供根主题窄投影，删除 SettingsViewModel 双轨原始订阅。
4. 运行启动、设置打开和导航 Trace，复测相同场景。

本步骤先于运行时子任务对 `SettingsViewModel.kt` 的声音样本区域修改；完成后提供最新文件
给运行时子任务继续，两个代理不并行编辑该文件。

#### B. RP Compose 与流式帧

1. 新增稳定 `uiItemId` 和 `contentType`，补正文增长、前插、删除和多分段测试。
2. 统一自动贴底策略，隔离光标正文和静止语音卡动画，播放器异步准备。
3. 先复测；只有 Trace 仍证明整表映射为主要热点时拆分 streaming 状态。
4. 检查流式顺序、闪烁、重复、取消、贴底和会话切换。

#### C. 运行时异常、取消与资源边界

1. 修复翻译、Worker、更新、脚本、语音路径的取消传播和旧任务写入。
2. 将下载查询、文件 / Base64、导出序列化与写入移出 Main。
3. 在 TTS 响应和音频解码前增加大小门禁，补最大负载测试。
4. 为禁用的翻译系统组件补启用前异常 / Bitmap 资源测试，但不恢复产品入口。

本步骤只修改 `SettingsViewModel.kt` 的声音样本读取 / Base64 区域，并在启动设置子任务
完成订阅区域后开始；语音卡 `MediaPlayer` 完整归 RP 子任务。

#### D. 会话 mutation 与跨表一致性

1. 建立会话级 mutation 串行化，覆盖编辑 / 删除 / 重试 / 发送交错。
2. 将数据库范围的剧情删除 / 重启和关联清理纳入单一事务。
3. 将文件清理放到事务提交后，并记录失败重试语义。
4. 运行每个失败点的回滚测试，确认无部分提交或旧快照覆盖。

### Wave 3：Profile、同环境复测与父任务集成

1. 生成 Profile，检查源码与合并文件包含 Narra 应用规则，并验证设备安装状态。
2. 在同一物理设备重新运行 S0～S3、F1～F3、N1，保留原始 JSON / Perfetto。
3. 对比 P50 / P90 / P95、启动中位数、PSS / RSS 和 Compose 报告。
4. 指标未改善的优化单独回退；不可判定结果如实标记。
5. 做全项目回归、范围检查、版本文档同步和父任务最终质量审查。

## Validation Commands

```powershell
.\gradlew.bat :baselineprofile:connectedBaselineAndroidTest --console=plain
.\gradlew.bat app:generateBaselineProfile --console=plain
.\gradlew.bat assembleBaseline --console=plain
.\gradlew.bat :app:testDebugUnitTest --console=plain
.\gradlew.bat :app:compileDebugAndroidTestKotlin --console=plain
.\gradlew.bat lintDebug --console=plain
.\gradlew.bat assembleDebug --console=plain
```

- 有独立测试类后先运行定向 JVM / Compose / Room / Macrobenchmark 窄测，再运行完整门禁。
- 每个 JVM 单测命令最大等待 60 秒。
- 设备前记录型号、API、刷新率、电量、温控、动画缩放和构建 commit。
- 无设备、温控异常、超时或 Profile 安装失败只能记录为未取得结果。

## Acceptance Mapping

- AC1～AC3 → Wave 1。
- AC4 → Wave 1 的本地诊断实现与父任务隐私审查。
- AC5～AC6 → Wave 2A。
- AC7～AC8 → Wave 2B。
- AC9 → Wave 2C。
- AC10 → Wave 2C、2D。
- AC11 → Wave 1、Wave 3。
- AC12 → 所有 Wave 与父任务集成审查。

## Risky Files / Rollback Points

- `StartupBenchmarks.kt`、`BaselineProfileGenerator.kt`、baseline-only fixture：ready 或隔离错误会
  生成伪成功数据；必须先证明等待失败会中止测试。
- `AppSettingsStore.kt`、`AiSettingsRepository.kt`、`AppRoot.kt`、`SettingsViewModel.kt`：
  共享流迁移影响广，按消费者逐步迁移并保留行为测试。
- `AppGraph.kt`：异常边界不能掩盖必需初始化失败，也不能把取消当普通错误。
- RP mapper、三条 Lazy 列表和 streaming 状态：identity 必须稳定且唯一，状态拆分必须由
  Trace 触发。
- TTS / Base64 上限：数值必须基于现有合法语音时长和 fixture，不得误拒绝正常输入。
- 会话 mutation / RoleplayRepository 事务：与已有消息删除改动可能相邻，实施前先审查工作区
  差异，不覆盖 lucky 的现有修改。
- 诊断文件 / 远程 SDK：任何写入或上传失败都不能成为新崩溃源；敏感数据泄露立即回滚。
