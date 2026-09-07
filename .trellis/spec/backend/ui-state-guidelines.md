# UI 与状态管理规范

## 页面与导航边界

- 当前产品入口是 `AppRoot` 的 `ROLEPLAY_GRAPH`，默认进入角色手机主壳。
  `AppNavHost` 只在 `BenchmarkRuntimePolicy.isBenchmarkSessionActive` 时调用
  `registerChatNavGraph`；普通 Chat 和独立 Translator 源码仍在，但不属于 dev/release
  的日常可见页面。清点 UI 范围必须沿实际导航注册与入口回调确认，不能只看文件名。
  不因重设计恢复旧入口；共享 Chat 组件仍可能被 Roleplay 使用，应逐个检查调用方。
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

## 角色扮演阅读与通话界面

- 日记列表和详情使用不透明的 `MaterialTheme.colorScheme.surface` 阅读面。
  用户背景只在阅读面之外展示，不在正文上叠加灰色玻璃或黑色 scrim。
- 日记顶部标题和图标使用独立空间，篇数/重新生成放第二行。月份 sticky header
  必须是全宽不透明表面，防止滚动正文从后面透出。
- 视频通话无场景立绘时，使用角色头像，再回退姓名；保留用户设置的背景与立绘，
  立绘用 Fit 展示主体，不能以全屏灰色占位代替头像。
- 纯色页面调用 `ApplyRoleplaySurfaceSystemBars(surfaceColor, immersiveMode)`；
  图片沉浸页面继续用 `ApplyRoleplaySystemBars`。系统栏只由 RESUMED 目的地设置，
  ON_PAUSE 恢复主题，已退到 STARTED/DESTROYED 的旧页不能在 onDispose 覆盖新页。
- 对应回归：`RoleplayDiaryScreenTest` 覆盖长文、翻页、搜索和三主题像素；
  `RoleplayVideoCallScreenTest` 覆盖输入与挂断；`RoleplaySystemBarsTest` 覆盖离场页
  延迟销毁与目标页全屏设置之间的竞态。

## 设置页面说明与主题

- 长规则使用 `SettingsHelpButton(title, description, modifier)`，默认只显示至少 48dp
  的信息按钮，打开后滚动正文、固定关闭按钮。`SettingsPageIntro` 和
  `SettingsSectionHeader` 已共用此实现；不要把注释式长文写成标题或重复显示。
- 当前模型、连接健康、下载失败、禁用原因等决策信息直接展示，不能随说明收起。
- `SettingsPalette`、状态标签及 Provider 品牌色以实际 Material 主题取色，
  不能用 `isSystemInDarkTheme()` 代替应用内用户强制选择的深浅色。
- 保存中禁用重复保存。模型能力的取消/系统返回仅关闭弹窗；恢复自动必须经过显式按钮。
- 主页面分组在 LazyColumn 内，设备测试定位远端入口先在列表上 `performScrollToNode`。
  大字体下的长显示选项面板完整展开且可滚动，仍验证最后一项可见、可点击。
- 回归参考：`SettingsComponentsTest`、`SettingsAppearanceScreenTest`、
  `ProviderSettingsUiTest`、`AssistantSettingsRedesignTest` 和 `AppUpdateScreenTest`。

## 附属页面与确认弹窗

- Moments 发布成功以 `MomentsUiState.lastPublishedPostId` 的新值为准；它是瞬时 UI 状态，
  不写入数据库。不能依靠先观察到 `isPublishing=true` 再变为 false 来关闭编辑器：
  本地发布可能在两帧之间完成。发布失败保留正文、配图、位置与身份，后续角色回复失败
  不撤销已经成功的发布。回归见 `MomentsViewModelTest`、`MomentsScreenTest`。
- PhoneCheck 的分类栏可横向滚动，并把当前分类滚入可见区域；选择与刷新勾选项随
  conversation/scenario/owner 键保存。搜索详情失败保留重试入口，空分类显示明确空态。
  回归见 `PhoneCheckScreenTest`，不在 Screen 中生成或重写手机快照。
- `NarraAlertDialog` 保持平台窗口的返回/外部点击关闭语义；标题和长正文放有界滚动区，
  确认/取消放独立可换行操作区。不能再用占满窗口的背景 Box 破坏外部点击判断。
  关闭只触发 `onDismiss`，删除只触发显式 `onConfirm`；回归见 `NarraAlertDialogTest`。
- 上下文日志的启停与容量不改变旧日志可读性；删除先确认，旧快照无分段时仍提供原文。
  来源文字使用 Material 内容色，在深浅色阅读表面都应可辨认。回归见 `ContextLogScreenTest`。
- 原生系统悬浮窗口的主题、取消和授权契约见
  [系统悬浮翻译 UI](./floating-translation-ui-guidelines.md)。

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
