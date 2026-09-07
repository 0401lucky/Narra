# 系统悬浮翻译 UI 契约

## 1. 范围与触发

修改 `system/translation/` 的浮球、选中文字、翻译面板、服务任务或截屏授权时使用。
窗口继续由原生 `WindowManager` 管理，业务与存储仍由现有 Service/Repository 协调。
`ScreenTranslationSettingsScreen` 当前未注册日常产品导航；不能因 UI 维护恢复旧入口。

## 2. 接口

- `resolveAppColorScheme(appColorTheme: AppColorTheme, darkTheme: Boolean, dynamicColorScheme: ColorScheme? = null): ColorScheme`：
  Compose 和原生悬浮窗共用的 Material 色板解析，保留抹茶/香草/海洋及应用内深浅色选择。
- `FloatingOverlayController(context, callback)`：生产构造使用 `TYPE_APPLICATION_OVERLAY`
  和 `Settings.canDrawOverlays`。内部构造仅允许测试注入 WindowManager、类型、token 和权限判断。
- Controller 提供 `showLoadingPanel`、`showTranslationResult`、`showErrorPanel`、
  `hideResultPanel`、`dismissAll` 与 `updateTheme`；关闭事件经 `onTranslationPanelDismissed()` 上抛。
- `ScreenTranslationTasks.launch(block)`、`cancel()`、`beginCapturePermission(): Long?`、
  `consumeCapturePermission(requestId: Long): Boolean` 管理同一个取消边界。
- `ScreenCapturePermissionActivity.createIntent(context, requestId)` 将请求编号传给 Activity，
  回调经 `ScreenTranslatorService.submitCapturePermissionResult(..., requestId)` 原样返回。

## 3. 状态与数据契约

- 新任务取消旧翻译；关闭面板、服务停止和新选中文字请求会使等待中的截屏授权失效。
  重复申请返回 null；旧编号、重复消费及关闭后的授权结果不再启动捕获。
- 每次翻译固定目标语言快照，请求、界面和历史记录使用同一语言。更换语言的保存失败
  进入可重试错误态；保存与重译受同一个任务控制。
- 流式 UI 更新属于当前翻译协程；不能再另起 Service 级协程更新已关闭的窗口。
- 截屏前移除本应用的面板/浮球，避免 OCR 识别到自己的译文；OCR 结束或失败都回收 bitmap。
- `FloatingTranslationViews` 只负责原生显示和回调。正文可滚动，固定操作区至少 48dp，
  表面使用 8dp 圆角。Compose 设置页复用 `SettingsHelpButton`；原生窗口用可访问的说明按钮。
- 阅读、帮助、语言列表相互切换时重置滚动位置；同一模式的流式刷新保持阅读位置。
  窗口尺寸由 `FloatingOverlayGeometry` 按可用区域限制，浮球拖动/吸边后仍在屏幕内。

## 4. 校验与错误矩阵

| 条件 | 行为 |
| --- | --- |
| 未授予悬浮权限 | 不添加任何窗口，不绕过权限检查 |
| 重复/迟到授权结果 | 忽略，不重新打开或捕获 |
| 用户取消 | 取消当前任务，不显示失败，不接受迟到译文 |
| 加载中 | 展示进度，复制/语言操作禁用，关闭仍可用 |
| OCR 无文本、空译文或请求失败 | 独立错误面板与可行动提示；错误文案不作为译文复制 |
| 可重试输入仍存在 | 保留显式重译入口，点击后立即进入加载态 |
| 主题或窗口配置变化 | 内容保留，重用共享色板并重新约束窗口几何 |

## 5. 正常、边界与错误用例

- 正常：选中文字 → 加载 → 结果，复制仅包含译文；三主题深浅色保持一致。
- 边界：授权 A 等待时关闭，再申请 B；A 的回调无效，B 仅能消费一次。
- 错误：网络失败后能重译/关闭；打开帮助从头开始，短窗口与大字体仍能到达末尾和关闭按钮。

## 6. 回归断言

- `ScreenTranslationTasksTest`：关闭保存中任务、新任务取消旧流、重复/迟到授权、选中文字使授权失效。
- `FloatingOverlayGeometryTest`：小窗口、越界位置、非有限归一值、吸边。
- `AppColorSchemeTest`：三主题与深浅色、动态色兼容。
- `FloatingOverlayControllerTest`：真实 Activity 附属窗口验证权限缺失、关闭取消、复制、重译、
  选区、拖动、主题变化、短窗口和帮助滚动。
- Activity 托管窗口通过不等于跨应用悬浮、MediaProjection/OCR 或真实网络翻译已通过；
  这些系统端到端条件必须单独记录。

## 7. 错误与正确写法

错误：在翻译 collect 内调用 `serviceScope.launch { showTranslationResult(...) }`，
关闭只移除 View；旧任务仍可能重新创建结果面板。

正确：通过 `ScreenTranslationTasks.launch` 启动翻译，在同一个任务内切回主线程更新窗口；
`onTranslationPanelDismissed` 调用 `cancel()`，捕获 `CancellationException` 后继续抛出。
