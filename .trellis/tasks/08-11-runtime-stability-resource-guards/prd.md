# 运行时异常、取消与资源边界

## Goal

在 2.2.6-dev 中修复代码级确认的取消误处理、旧任务覆盖、主线程同步 IO 和无界内存
输入，使翻译、更新、语音及系统资源路径在成功、失败、取消和超限场景下都能恢复一致
状态，不再放大为卡顿、错误提示污染或进程崩溃。

## Background

- `TranslationViewModel.kt:191-238` 取消旧任务后立即启动新任务，但 `runCatching` 会把
  `CancellationException` 当失败处理，旧任务可能关闭新任务的 loading 或覆盖状态。
- `MomentsAutoGenerationWorker.kt:22-30`、`AppUpdateRepository.kt:54-77`、
  `AndroidRoleplayScriptEngine.kt:71-80`、`VoiceSynthesisCoordinator.kt:105-147`
  都存在宽泛捕获取消的问题。
- `AppUpdateViewModel.kt:232-254` 每秒在 Main 调用同步 `DownloadManager.query()`；
  `SettingsViewModel.kt:640-663,943-970` 和 `VoiceSynthesisCoordinator.kt:202-231`
  在 Main 路径读取声音文件并 Base64 编码。
- `MimoTtsClient.kt:158-176` 与 `AudioFileStorage.kt:91-107` 没有响应 / 解码大小门禁。
- `ScreenTranslatorService` 当前被 Manifest 禁用，但其根协程、流式兄弟任务和 Bitmap
  清理问题必须在任何恢复入口之前修复。

## Requirements

- **R1 取消语义**：所有宽泛异常捕获必须先原样传播 `CancellationException`；取消不得
  显示为失败、触发重试、写错误状态或禁用脚本。
- **R2 任务所有权**：可被新请求替换的翻译 / 轮询任务必须保存 Job 或 generation identity；
  只有当前任务可以更新共享 UI 状态，旧结果和旧流式帧必须被丢弃。
- **R3 主线程边界**：`DownloadManager.query()`、文件读取、Base64、导出序列化和写入
  必须在明确的后台 dispatcher 执行，并保持页面销毁 / 取消语义。
- **R4 输入大小边界**：声音样本在完整读取前检查文件长度；TTS HTTP body 和 Base64
  音频必须在大分配前拒绝超限输入，错误文案脱敏且可行动。
- **R5 系统资源**：Image、Bitmap、MediaProjection 等截图资源必须在成功、异常和取消
  时释放；悬浮翻译流式 UI 更新必须属于当前翻译任务。
- **R6 功能边界**：本任务不启用被禁用的悬浮翻译 / 无障碍组件，不改变 Provider、更新
  重试和语音产品语义。
- **R7 可验证性**：dispatcher、文件读取、平台查询和响应上限必须可注入或可替换，覆盖
  成功、异常、取消、快速连续请求和边界值测试。
- **R8 文件所有权**：`SettingsViewModel.kt` 仅修改声音样本请求构造和读取 / Base64 区域，
  必须在启动设置子任务完成订阅 / 草稿同步改动后开始；`RoleplayActionMessageCards.kt`
  及语音卡播放器完整归 RP 子任务，本任务不得修改。

## Acceptance Criteria

- [ ] **AC1（R1）**：Worker 停止、页面离开、更新取消、脚本取消和语音取消都不会被记录
      或展示为普通失败，也不会产生额外业务写入。
- [ ] **AC2（R2）**：快速连续翻译时，旧任务晚到的取消、成功或流式帧均不能修改新任务的
      loading、结果和错误状态。
- [ ] **AC3（R3）**：线程 / dispatcher 测试证明同步下载查询、声音文件读取、Base64 和
      导出写入不在 Main 执行；取消后不再继续更新失活页面。
- [ ] **AC4（R4）**：声音样本略低于、等于、略高于上限的测试通过，超限输入在
      `readBytes()` / Base64 大分配前被拒绝。
- [ ] **AC5（R4）**：TTS body 与 Base64 音频的正常、边界和超限 MockWebServer 测试通过；
      超限错误不包含响应正文、Base64、密钥或本地路径。
- [ ] **AC6（R5）**：截图 / OCR 异常和取消时 Bitmap、ImageReader 等资源在 `finally`
      释放；流式翻译不再为每个 token 创建脱离父任务的兄弟协程。
- [ ] **AC7（R6）**：Manifest 中被禁用组件保持禁用，设置 / 导航不新增入口；已有普通翻译、
      更新、语音和脚本成功路径行为不变。
- [ ] **AC8（R7、R8）**：新增定向 JVM / 系统组件测试覆盖成功、失败、取消、旧任务和最大负载；
      相关 Lint、源码编译、单测和 debug 构建结果有记录。
- [ ] **AC9**：本子任务不修改 Room schema、全局网络重试、远程崩溃 SDK 或 RP Compose
      列表实现。

## Out of Scope

- AppGraph 启动任务、共享设置流和根主题投影，由启动 / 设置子任务负责。
- RP Lazy 列表 identity、自动贴底和 streaming UiState，由 RP Compose 子任务负责。
- 会话编辑 / 发送串行化和跨表事务，由数据一致性子任务负责。
- 启用悬浮翻译、无障碍服务或新增远程 Crashlytics / Sentry。

## Confirmed Decisions

- 取消是正常控制流，不能使用 `runCatching` 的通用失败分支处理。
- 超限检查必须发生在完整读取、字符串化或解码之前。
- 当前禁用的系统组件只修复启用前安全边界，不恢复产品入口。
- 所有日志和用户错误继续复用现有脱敏能力。
- 运行时子任务在启动设置子任务之后修改 `SettingsViewModel` 的声音样本区域；RP 子任务
  独占语音卡动画和 `MediaPlayer` 文件。
