# 首次启动合规声明与未成年人限制：技术设计

## 1. Scope and Boundaries

本任务实现一个应用级合规门禁，而不是普通导航页面：只有当前条款版本已经确认，
`AppNavHost`、启动更新检查和自动动态生成等可使用 AI/网络的入口才允许继续工作。

边界分为四层：

```text
CompliancePolicy（条款内容与版本）
        ↓
ComplianceConsentStore（DataStore 持久化）
        ↓
ComplianceViewModel（加载、确认与状态协调）
        ↓
AppRoot / ComplianceScreen / Settings route
```

不新增 Room 表，不接入服务端账号，不收集真实身份凭证。

## 2. Policy Contract

新增单一的条款定义对象，集中提供：

- 当前条款版本：建议初始值为 `2026-07-15-v1`，与应用版本解耦。
- 页面标题、摘要和分节正文。
- 18+ 使用条件、AI 非真人提示、准确性风险、专业建议限制、沉浸/心理风险、
  隐私风险、内容责任和服务可用性说明。
- 设置页与首次确认页共享同一份内容，禁止复制两套文案。

条款版本只在文案含义、年龄条件或重要风险范围变化时提升；单纯排版调整不提升。

## 3. Persistence

新增独立的 `ComplianceConsentStore`，使用 Preferences DataStore 保存：

- `accepted_policy_version: String`
- `accepted_at_epoch_millis: Long`

对外提供：

- `consentFlow: Flow<ComplianceConsent>`
- `accept(policyVersion, acceptedAt)`
- `isAccepted(policyVersion)`，供后台 Worker 在执行时再次校验。

选择独立 Store，而不是继续扩张 `AppSettingsStore`，原因是合规准入属于应用生命周期
状态，不是可编辑的显示或 AI 设置；独立存储也能减少对现有 `AppSettings`、设置保存
接口和大量测试的扰动。Store 仍通过 `AppGraph` 统一装配，保持依赖方向一致。

同版本匹配规则为严格相等；版本为空或不同均视为未确认。确认时间只用于展示和审计，
不参与准入判断。

## 4. State and Data Flow

`ComplianceViewModel` 暴露三态 UI 状态：

```text
Loading  ──DataStore 首次发射──→ Required
   │                              │
   └──── 已确认当前版本 ────────→ Accepted
                                  ↑
Required ──完成阅读+双确认──accept()
```

- `Loading`：仅显示空白背景或轻量加载状态，绝不提前构建主导航，避免闪现和绕过。
- `Required`：显示全屏确认页。
- `Accepted`：构建现有 `AppNavHost` 和应用内更新弹窗。
- 持久化失败时保持 `Required`，显示错误提示，不允许“先进入后补写”。

滚动到底、两个复选框的状态属于页面临时交互状态，放在 Composable 中；持久化状态和
写入错误由 ViewModel 管理。

## 5. UI Design

### 5.1 首次确认模式

- 全屏页面，不使用可点击遮罩关闭的 Dialog。
- 顶部展示“Narra 使用须知与年龄声明”和当前条款版本。
- 中部为可滚动条款正文；只有滚动到末尾后才开放勾选/确认阶段。
- 底部固定区域包含：
  - “我已阅读并同意以上使用条款与风险提示”；
  - “本人确认已年满 18 周岁”；
  - 主按钮“同意并进入 Narra”；
  - 次按钮“不同意并退出”。
- 主按钮启用条件：已滚动到底、两个复选框均选中、当前未在保存。
- 系统返回键与“不同意并退出”使用同一个退出回调。

`MainActivity` 向 `AppRoot` 传入退出回调，由 Activity 执行 `finishAndRemoveTask()`；
Composable 不通过强制类型转换获取 Activity。

### 5.2 设置查看模式

- 新增设置路由和“使用条款与年龄限制”入口，放在设置页“通用”区域、版本更新附近。
- 复用相同条款页面内容，但不显示确认复选框和退出按钮。
- 展示当前条款版本、已接受版本和接受时间；只提供返回操作，不清除确认。

## 6. App Entry Integration

`AppRoot` 在创建主导航前收集 `ComplianceViewModel.uiState`：

- `Loading`：不创建 `NavController` 依赖的业务内容。
- `Required`：只显示 `ComplianceScreen`。
- `Accepted`：沿用当前主题、设置 ViewModel、导航和更新 UI。

主题仍可使用现有设置流；合规门禁不能依赖主导航。为了避免确认前发起不必要网络
请求，`AppUpdateViewModel.onAppStarted()` 仅在状态进入 `Accepted` 后调用。

深链或 Activity 任务栈恢复仍会先经过 `AppRoot` 状态判断，未确认时不会实例化
`AppNavHost`，因此没有可导航的业务目标。

## 7. Background Work Guard

当前 `ChatApplication.onCreate()` 会无条件调度 `MomentsAutoGenerationWorker`，且 Worker
可能调用 AI 网络生成内容。仅隐藏 UI 不能阻止已存在的周期任务，所以需要两层保护：

1. 首次成功确认后再调度周期任务。
2. `MomentsAutoGenerationWorker.doWork()` 在每次执行前调用
   `ComplianceConsentStore.isAccepted(CURRENT_POLICY_VERSION)`；未确认时直接成功结束，
   不访问 AI 仓储。

第二层用于覆盖升级前已经存在的 WorkManager 周期任务。设置迁移、内置预设初始化等
纯本地启动任务可以继续执行。

## 8. Error Handling and Accessibility

- DataStore 读取异常：记录脱敏错误，UI 保持门禁状态并给出重试入口。
- 写入异常：恢复按钮可用状态并显示“确认状态保存失败，请重试”，不得进入主界面。
- 复选框整行可点击，提供明确语义；条款正文支持系统字体缩放和屏幕阅读器。
- 颜色、禁用态和滚动提示遵循 Material 3 主题，不只依赖颜色表达状态。

## 9. Compatibility and Migration

- 现有用户没有 `accepted_policy_version`，升级到 2.2.4-dev 后首次打开会进入门禁。
- 无数据库迁移；DataStore 新键为空即走未确认路径。
- 条款版本与应用版本分离，未来可只提升条款版本触发重新确认。
- 不删除或重写现有设置、角色、会话和媒体数据。

## 10. Testing Strategy

- JVM 单测：版本匹配、空状态、新版本失效、接受写入、ViewModel 状态和写入失败。
- Worker 单测或可测试守卫：未确认时不调用生成协调器，确认后才执行。
- Compose UI 测试：滚动、双复选框、按钮启用、保存中、查看模式和返回行为。
- 集成检查：全新数据首次启动、确认后重启、模拟条款版本升级、设置页查看。
- 回归：现有主导航、设置页、更新检查和动态周期任务在确认后保持原行为。

## 11. Trade-offs

- 统一 18+ 门槛实现简单、边界清晰，但会排除未成年人的全部功能；这是用户明确选择。
- 自我声明不能证明真实年龄，但避免收集身份证等高敏感数据；页面会明确这是使用条件，
  不宣称已完成实名核验。
- 独立 Store 增加一个小型持久化类，但明显降低对庞大 `AppSettingsStore` 的耦合和
  回归风险。

## 12. Rollback Shape

如门禁导致严重启动问题，可回退 `AppRoot` 的合规分支和 Worker 守卫，同时保留独立
DataStore 文件；残留的确认状态不会影响旧代码。不得通过默认视为已确认的方式临时
绕过，因为这会破坏本任务的核心安全边界。
