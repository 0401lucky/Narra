# UI 与状态管理规范

## 页面与导航边界

- 路由常量和构造函数集中在 `ui/navigation/AppRoutes.kt`。
- 顶层与子图分别由 `AppNavHost`、`registerChatNavGraph`、
  `registerRoleplayGraph`、`registerSettingsNavGraph` 注册。
- 导航层负责创建 ViewModel、收集状态、绑定页面回调；页面优先只接收
  `uiState`、必要的派生参数和 `onXxx` 回调。
- 图内共享 ViewModel 使用 `NavigationViewModelOwners.kt` 中的
  `viewModelStoreOwner` 模式，不要在每个目的地重复创建。
- 路由参数写入时 `Uri.encode`，读取时 `Uri.decode`。旧代码有未统一的
  provider/assistant 路由，新代码不要继续复制。

## ViewModel 状态形状

标准模式：

```kotlin
data class FeatureUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

private val _uiState = MutableStateFlow(FeatureUiState())
val uiState: StateFlow<FeatureUiState> = _uiState.asStateFlow()
```

- 使用 `_uiState.update { it.copy(...) }` 做原子状态更新。
- 仓库长期流可用 `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000),
  initialValue)` 暴露，参考 `ChatViewModel.settings`、
  `MomentsViewModel.settings`。
- 多个来源形成页面投影时，在 ViewModel 中用 `combine`、`flatMapLatest`
  汇总，参考 `MomentsViewModel.observeTimeline` 和 `ChatViewModel` 的观察逻辑。
- 高频流式数据可从大 `UiState` 拆出独立 `StateFlow`，例如
  `ChatStreamingState`，避免每个 token 触发整个页面重组。
- 长任务保留 `Job?` 并提供取消入口；取消后恢复 loading/sending 状态。

## Compose 收集与局部状态

- ViewModel 的 `StateFlow` 在 Compose 中使用
  `collectAsStateWithLifecycle()`。标准位置是导航目的地或页面入口。
- 页面内部纯 UI 状态使用 `remember`；需要配置变更或进程重建恢复的草稿、
  tab、展开状态使用 `rememberSaveable`，复杂值提供 `Saver`。
- 路由参数、场景切换等键控副作用使用 `LaunchedEffect(key)`。
- Drawer、Snackbar、文件选择等短生命周期 UI 动作可用
  `rememberCoroutineScope()`。
- 播放器、监听器等资源在 `DisposableEffect` 中释放。
- 不在 Composable 正文直接启动协程或执行持久化写入。

## 回调与页面拆分

- 交互回调统一以 `onXxx` 命名。
- 回调数量过多时按职责分组为不可变回调对象，参考
  `ChatScreenCallbacks.kt` 和 `RoleplayScreenCallbacks.kt`。
- 大页面按职责拆分：局部状态、派生值、副作用、覆盖层、内容面板分别放入
  `XxxScreenLocalState`、`XxxScreenDerivations`、`XxxScreenBehavior` 等文件。
- 纯状态转换优先放 `*Support`；含 Repository、网络或文件依赖的流程放
  `*Coordinator`，不要继续放大主 ViewModel。

## 用户反馈

- Chat、Roleplay、Phone 等页面沿用 `errorMessage` 与 `noticeMessage`
  分离；Settings 现有代码使用单一 `message` 并由 `consumeMessage()` 消费。
- 一次性消息通过 `LaunchedEffect(message)` 显示 Snackbar，显示后调用
  `clearErrorMessage`、`clearNoticeMessage` 或 `consumeMessage`。
- 通用 Snackbar 使用 `AppSnackbarHost`、`TopAppSnackbarHost`；破坏性操作
  使用确认对话框；字段级错误可内联显示。
- 用户可见异常必须先经 `SensitiveTextRedactor.throwableMessageForUi()`
  或等价安全映射，文案使用简体中文。
- Toast 目前只用于复制、导出等轻量旧流程，不作为新业务错误的默认通道。

## ViewModel 创建

- ViewModel 在 `companion object` 提供显式 `factory(...)`，列出所需依赖。
- 工厂统一使用 `typedViewModelFactory { ... }`，不要重复手写未经校验的泛型
  强转。
- 依赖从 `AppGraph` 传入；系统时间、dispatcher、文件保存等易变能力通过
  构造参数或 lambda 注入，以便测试。

## 不要复制的现存问题

- `ChatViewModel`、`RoleplayViewModel` 已很大；新增功能优先拆 Support、
  Coordinator、Parser 或领域服务。
- 部分 Screen 直接接收 ViewModel、部分 system 代码反向引用 ViewModel
  常量，这些是兼容代码，不是新代码范例。
- 协程取消处理存在旧的不一致路径。新代码捕获到
  `CancellationException` 时必须重新抛出或按正常取消处理，不能显示为失败。

## 参考文件

- `ui/AppRoot.kt`
- `ui/navigation/ChatNavGraph.kt`
- `ui/navigation/NavigationViewModelOwners.kt`
- `ui/screen/chat/ChatScreenCallbacks.kt`
- `ui/screen/chat/ChatScreenBehavior.kt`
- `viewmodel/ChatViewModel.kt`
- `viewmodel/MomentsViewModel.kt`
- `viewmodel/TypedViewModelFactory.kt`
