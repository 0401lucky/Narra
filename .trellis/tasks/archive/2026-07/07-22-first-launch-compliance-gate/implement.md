# 首次启动合规声明与未成年人限制：实施计划

## Completion Definition

只有以下条件全部满足才算完成：应用所有 UI 与会发起 AI 网络工作的后台入口均受当前
条款版本控制；首次和版本升级确认流程可用；设置页可查看；自动化测试、Lint、debug
构建及实际界面验证完成；2.2.4-dev 版本记录已同步。

## Ordered Checklist

1. **加载开发规范与确认改动面**
   - 使用 `trellis-before-dev` 读取 UI、DataStore、错误处理、质量和跨层指南。
   - 再次搜索启动入口、DataStore、WorkManager、设置导航和相关测试，确认没有遗漏
     其他可绕过的 Activity 或后台 AI 入口。

2. **先建立条款与持久化契约**
   - 新增单一 `CompliancePolicy` 内容与 `CURRENT_COMPLIANCE_POLICY_VERSION`。
   - 新增 `ComplianceConsent`、`ComplianceConsentStore` 和可注入时钟/测试边界。
   - 先写版本匹配、空状态、接受写入和版本失效单测。
   - 将 Store 装配到 `AppGraph`，不改动现有 Room schema。

3. **实现状态协调**
   - 新增 `ComplianceViewModel` 和 `ComplianceUiState`。
   - 覆盖 Loading、Required、Accepted、保存中和保存失败状态。
   - 确保持久化成功后才切换到 Accepted。

4. **实现可复用合规页面**
   - 新增全屏 `ComplianceScreen`，支持首次确认模式与设置查看模式。
   - 实现滚动到底检测、双复选框、按钮启用规则、保存失败反馈和无障碍语义。
   - 为关键元素添加稳定测试标签，补 Compose UI 测试。

5. **接入应用启动门禁**
   - `MainActivity` 向 `AppRoot` 提供退出回调。
   - `AppRoot` 在 `Accepted` 前不创建主导航和更新弹窗。
   - 将启动更新检查延迟到成功确认之后。
   - 验证重启、任务栈恢复和导航目标不能绕过。

6. **保护后台 AI 工作**
   - 从 `ChatApplication.onCreate()` 移除无条件动态周期任务调度，改为确认成功后调度。
   - 在 `MomentsAutoGenerationWorker.doWork()` 增加当前条款版本检查，覆盖旧周期任务。
   - 用假 Store/协调器验证未确认时不会调用生成逻辑。

7. **增加设置页查看入口**
   - 新增设置路由和条款查看页面。
   - 在设置页通用区域增加“使用条款与年龄限制”。
   - 显示当前版本、已接受版本和接受时间，返回后确认状态保持不变。

8. **同步 2.2.4-dev 记录**
   - 在 `版本功能添加/优化.md` 新增或更新 `2.2.4-dev` 条目。
   - 若实现阶段触及发版记录，再按仓库约定同步对应文档；本任务不主动发包。

9. **分层验证并修复发现的问题**
   - 先跑新单测和受影响测试，再跑全量 JVM 单测。
   - 运行 `lintDebug` 与 `assembleDebug`。
   - 有设备/模拟器时运行相关 Android UI 测试，并实际操作首次启动、滚动确认、退出、
     二次启动、版本失效和设置查看。
   - 检查日志中没有条款正文、年龄声明或其他不必要信息泄漏。

10. **最终审查**
    - 使用 `trellis-check` 对照 PRD、设计、跨层数据流和测试结果检查。
    - 检查 `git diff`/`git status`，只汇报本任务相关文件，不覆盖既有 41 项工作区改动。
    - 用户确认功能结果后，再进入 Trellis 收尾流程；除非用户另行要求，不提交或发包。

11. **用户授权的 2.2.4-dev 发布**
    - 将 `gradle.properties` 的版本覆盖值更新为 `2.2.4`，同步版本文档和
      `docs/updates/dev.json`，先完成构建再写入最终 APK 下载地址。
    - 按项目约定构建 `assembleDebug`，计算 APK SHA-256，上传到
      `narra-updates/dev/2.2.4/`，拉回远端对象校验哈希，并对公开下载地址执行 HEAD 200 检查。
    - 只 stage 本任务、版本记录和 APK 元数据相关文件，提交并推送到 GitHub `main`；
      不纳入工作区已有的无关用户改动。

## Planned Validation Commands

```powershell
.\gradlew.bat testDebugUnitTest --console=plain
.\gradlew.bat lintDebug --console=plain
.\gradlew.bat assembleDebug --console=plain
```

有可用设备/模拟器时：

```powershell
.\gradlew.bat connectedDebugAndroidTest --console=plain
```

单元测试调用设置 60 秒工具超时；若 Gradle 仍在后台运行，使用任务句柄持续读取输出，
不以超时当作通过。

## Risky Files and Review Gates

- `AppRoot.kt`：全局启动门禁，改错会导致白屏或主界面闪现；完成后优先做冷启动验证。
- `ChatApplication.kt` / `MomentsAutoGenerationWorker.kt`：关系到后台 AI 网络调用；必须验证
  未确认路径不会调用生成协调器。
- `SettingsScreen.kt` / `SettingsNavGraph.kt`：函数参数较多，修改后必须编译并跑现有设置页测试。
- DataStore：只新增独立键与 Store，不改旧键、不清空旧数据。

## Rollback Points

- 持久化层与 UI 层分开提交/审查思路；任一层不稳定时先回退调用点，不修改旧设置数据。
- Worker 守卫必须与启动调度调整同时交付，避免只改一侧留下升级用户绕过窗口。
- 不采用“读取失败默认已同意”的降级；故障时宁可保持门禁并允许重试。
