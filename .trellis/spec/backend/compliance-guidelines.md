# 合规门禁与条款确认规范

## Scenario：应用级 18+ 条款门禁

### 1. Scope / Trigger

- 适用范围：首次启动、条款版本升级、设置页查看和所有会触发 AI/网络的后台任务。
- 触发原因：Narra 的角色扮演和拟人化互动能力必须在当前条款确认后才能使用；仅隐藏
  Compose 主界面不能阻止已存在的 WorkManager 周期任务。
- 依赖方向必须保持：

```text
CompliancePolicy → ComplianceConsentRepository/DataStore
→ ComplianceViewModel → AppRoot/Screen/Settings
→ Worker 当前版本守卫
```

### 2. Signatures

```kotlin
object CompliancePolicy {
    const val CURRENT_VERSION: String
}

interface ComplianceConsentRepository {
    val consentFlow: Flow<ComplianceConsent>
    suspend fun accept(policyVersion: String, acceptedAtEpochMillis: Long)
    suspend fun isAccepted(policyVersion: String): Boolean
}

data class ComplianceConsent(
    val acceptedPolicyVersion: String = "",
    val acceptedAtEpochMillis: Long = 0L,
)
```

Preferences DataStore 键固定为：

- `accepted_policy_version: String`
- `accepted_at_epoch_millis: Long`

### 3. Contracts

- 版本判断必须严格相等：`acceptedPolicyVersion == CURRENT_VERSION`；空值或旧版本均为
  未确认。
- 接受写入必须同时保存非空条款版本和大于 0 的确认时间；任一参数非法立即拒绝。
- `ComplianceViewModel` 只有在 `accept()` 成功返回后才能进入 `isAccepted = true`。
- DataStore 写入失败时保持门禁，向 UI 返回安全中文错误；不能默认视为已同意。
- `AppRoot` 在 `isAccepted` 前不构建 `AppNavHost`，也不调用启动更新检查。
- `MomentsAutoGenerationWorker` 每次执行前都重新调用
  `isAccepted(CURRENT_VERSION)`；未确认时直接结束，不访问 AI 生成协调器。
- 设置页复用同一份 `CompliancePolicy.sections`，只读查看不能清除确认状态。
- 条款版本只在风险、年龄条件或使用义务有实质变化时提升；排版调整不提升版本。

### 4. Validation & Error Matrix

| 条件 | 结果 | 用户/日志行为 |
| --- | --- | --- |
| 版本为空或与当前版本不同 | 门禁 Required | 不进入主界面；不触发 AI Worker |
| 已滚动到底且双复选框均选中 | 允许提交 | 保存成功后进入 Accepted |
| 未滚动到底或任一复选框未选 | 禁用确认按钮 | 页面提示继续阅读/完成确认 |
| DataStore 读取 IOException | 按未确认处理 | 脱敏记录日志，可重试 |
| DataStore 写入异常 | 保持 Required | UI 显示“确认状态保存失败，请重试” |
| 协程 CancellationException | 重新抛出 | 不显示失败消息、不伪装成成功 |
| Worker 未确认当前版本 | `Result.success()` | 不调用 `generateDueAssistantPosts` |
| Worker 读取状态异常 | `Result.retry()` | 不执行 AI 生成 |

### 5. Good / Base / Bad Cases

- Good：用户滑到条款末尾、勾选两项、DataStore 写入成功；重启后同版本不重复弹出。
- Good：条款版本从 `2026-07-15-v1` 变为 `v2`；旧确认保留为历史信息，但应用重新拦截。
- Base：新安装没有任何确认键；页面显示门禁，用户拒绝后 Activity 退出。
- Bad：把缺失版本当成已确认，导致首次安装直接进入角色扮演。
- Bad：只在 `AppRoot` 隐藏导航，却继续在 `Application.onCreate()` 调度或运行 AI Worker。
- Bad：设置页复制一套不同的条款正文，版本升级后首次页和查看页内容不一致。

### 6. Tests Required

- `ComplianceConsentTest`：空版本为 false、同版本为 true、不同版本为 false。
- `ComplianceConsentStoreTest`：写入后 `consentFlow` 返回版本和时间，`isAccepted` 对旧版本
  返回 false。
- `ComplianceViewModelTest`：空状态门禁、成功接受、旧版本失效、写入失败保持门禁。
- `ComplianceScreenTest`：未读到底按钮禁用；滚动到底并双勾选后按钮可用；查看模式显示
  当前条款和确认信息；返回回调触发。
- Worker 守卫测试或可替换依赖测试：未确认不调用生成协调器，确认后才调用。
- 发布前必须至少执行 debug 构建、JVM 单测、Lint 和 AndroidTest 源码编译；有设备时补充
  冷启动、拒绝退出、确认重启和条款升级流程。

### 7. Wrong vs Correct

#### Wrong

```kotlin
// UI 隐藏了导航，但后台周期任务仍可直接访问 AI。
override fun onCreate() {
    super.onCreate()
    MomentsAutoGenerationWorker.schedule(this)
}
```

#### Correct

```kotlin
// 首次确认成功后再调度，并在 Worker 执行时再次守卫，覆盖升级前遗留任务。
if (complianceConsentStore.isAccepted(CompliancePolicy.CURRENT_VERSION)) {
    momentsGenerationCoordinator.generateDueAssistantPosts(maxPosts = 2)
}
```
