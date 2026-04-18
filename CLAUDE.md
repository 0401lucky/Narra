# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概览
- 单模块 Android 应用，唯一业务模块为 `:app`。
- 技术栈：Kotlin、Jetpack Compose、Material 3、Navigation Compose、Lifecycle ViewModel、StateFlow、DataStore Preferences、Room、Retrofit、OkHttp、Gson、KSP。
- 构建脚本使用 Groovy DSL，依赖版本集中在 `gradle/libs.versions.toml`。
- 根级 `README.md` 目前仅作占位，仓库级协作说明以本文件为准。

## 常用命令
所有命令都在仓库根目录执行，Windows 环境使用 `./gradlew.bat`。

```bash
./gradlew.bat assembleDebug
./gradlew.bat app:lint
./gradlew.bat test
./gradlew.bat app:testDebugUnitTest
./gradlew.bat app:installDebug
./gradlew.bat app:connectedDebugAndroidTest
./gradlew.bat app:compileDebugKotlin    # 只做编译校验、速度最快
```

### 运行单个测试
```bash
./gradlew.bat app:testDebugUnitTest --tests "com.example.myapplication.viewmodel.ChatViewModelTest"
./gradlew.bat app:testDebugUnitTest --tests "com.example.myapplication.viewmodel.ChatViewModelTest.retryMessage_retriesFailedMessageAndReplacesErrorContent"
```

## 架构总览

### 应用入口与装配（DI 容器：AppGraph）
- `app/src/main/java/com/example/myapplication/ChatApplication.kt`
  - 继承 `Application`，持有 `val appGraph: AppGraph by lazy { AppGraph(this) }`。
  - `onCreate()` 调 `appGraph.launchStartupTasks()`（目前只做一次性的 `settingsStore.migrateSensitiveData()`）。
- `app/src/main/java/com/example/myapplication/di/AppGraph.kt`
  - 手写 DI 容器。所有 Repository / Store / Service / Tool 都在这里以 `by lazy` 的方式装配。
  - 新增依赖的入口：在 `AppGraph` 里加一个 `val xxx by lazy { ... }`。不要在 Activity / ViewModel 里 new Repository。
  - 启动型任务统一走 `startupScope`（`SupervisorJob() + Dispatchers.IO`）。
- `app/src/main/java/com/example/myapplication/MainActivity.kt`
  - 仅 19 行：从 `application as ChatApplication` 取 `appGraph` 并 `setContent { AppRoot(appGraph = ...) }`。
- 共享 Gson 实例：`app/src/main/java/com/example/myapplication/system/json/AppJson.kt`。
  - 需要 Gson 的位置一律写 `AppJson.gson`，不要 `Gson()`。

### Compose 根与导航
- `app/src/main/java/com/example/myapplication/ui/AppRoot.kt`
  - 负责创建 `SettingsViewModel` 与 `AppUpdateViewModel`（都通过 `viewModel(factory = ...)` 注入 AppGraph 依赖）。
- `app/src/main/java/com/example/myapplication/ui/navigation/AppNavHost.kt`
  - `startDestination` 当前为 `AppRoutes.CHAT`。顶层 `composable` 有 HOME、PHONE_CHECK、MOMENTS、TRANSLATOR 等。
  - 大部分路由通过三个子图函数注册：
    - `registerRoleplayGraph(...)` （`RoleplayNavGraph.kt`）
    - `registerSettingsNavGraph(...)` （`SettingsNavGraph.kt` + `SettingsAssistantNavRoutes.kt` / `SettingsProviderNavRoutes.kt` / `SettingsDataNavRoutes.kt`）
    - `registerChatNavGraph(...)` （`ChatNavGraph.kt`）
- 路由常量集中在 `app/src/main/java/com/example/myapplication/ui/navigation/AppRoutes.kt`（34 条，覆盖 chat / settings / roleplay / phone-check / moments）。新增路由先在此登记。
- `NavigationViewModelOwners.kt` 统一托管跨目的地共享的 ViewModel owner（例如 PhoneCheck / Roleplay 在 nav backstack 上的 scope）。

### 数据职责边界
- `data/repository/ai/`：AI 能力相关。主入口 `AiGateway`（聊天/图像/流式），周边 `AiPromptExtrasService`（标题/摘要/suggestion 等、后续计划拆分）、`AiTranslationService`、`AiSettingsRepository`、`AiSettingsEditor`、`AiModelCatalogRepository`。
- `data/repository/ConversationRepository.kt`：本地会话与消息规则（默认会话、新建/删除/清空、根据首条用户消息生成标题）。
- `data/repository/context/`：世界书、记忆、摘要、Tavern 适配器、`ContextTransferCodec`、`PromptContextAssembler`。
- `data/repository/roleplay/RoleplayRepository.kt` + `RoomRoleplayRepository`：剧情场景、独立会话、日记。
- `data/repository/phone/PhoneSnapshotRepository.kt`：手机快照/观察状态。
- `data/repository/search/`：联网搜索。
- `data/local/AppSettingsStore.kt`：DataStore Preferences 持久化 `AppSettings`（含 provider 列表、assistant 列表、translation 历史、search 配置、敏感值通过 `SecureValueStore` 单独加密）。
- `data/local/chat/ChatDatabase.kt`：Room v27，11 张实体；迁移全部集中在该文件里（见 T5 拆分计划）。
- `data/remote/ApiServiceFactory.kt`：Retrofit 客户端工厂；调用方不要重复拼接 `/`，交给 `normalizeBaseUrl()`。

### ViewModel 状态流
- `viewmodel/SettingsViewModel.kt`（1100+ 行）：设置表单、模型列表加载、保存与一次性消息；辅助于 `Settings*Support.kt` / `Settings*Coordinator.kt` 系列。
- `viewmodel/ChatViewModel.kt`（1600+ 行）：汇总设置流 + 会话列表流 + 当前会话消息流为 `ChatUiState`；乐观落库、失败重试、流式回填；辅助于 `Chat*Support.kt` 系列。
- `viewmodel/RoleplayViewModel.kt` + `RoleplayRoundTripExecutor.kt` + `Roleplay*Support.kt` 系列：沉浸扮演交互。
- `viewmodel/PhoneCheckViewModel.kt`、`viewmodel/MomentsViewModel.kt`、`viewmodel/TranslationViewModel.kt`、`viewmodel/AppUpdateViewModel.kt`：各自独立领域。
- 消息状态统一使用 `MessageStatus.COMPLETED` / `LOADING` / `ERROR`。
- **订阅规范（团队约定）**：Compose 层一律用 `collectAsStateWithLifecycle()`；禁止裸 `collectAsState()`。

## 目录约定
- `app/src/main/java/com/example/myapplication/model/`：领域模型、DTO、枚举。
- `app/src/main/java/com/example/myapplication/data/local/`：DataStore、Room、Store 抽象与实现。
- `app/src/main/java/com/example/myapplication/data/remote/`：Retrofit API 定义与客户端工厂。
- `app/src/main/java/com/example/myapplication/data/repository/`：业务仓库（细分 `ai/` `context/` `phone/` `roleplay/` `search/`）。
- `app/src/main/java/com/example/myapplication/viewmodel/`：页面状态与交互编排。
- `app/src/main/java/com/example/myapplication/ui/navigation/`：路由和导航图。
- `app/src/main/java/com/example/myapplication/ui/screen/<feature>/`：页面级 Compose 组件。
- `app/src/main/java/com/example/myapplication/ui/component/`：可复用 Compose 组件（含 `ui/component/roleplay/` 专属组件）。
- `app/src/main/java/com/example/myapplication/system/`：系统服务（translation、update、logging）与全局单例（`system/json/AppJson.kt`）。
- `app/src/main/java/com/example/myapplication/conversation/`、`context/`、`roleplay/`、`di/`：按领域组织的纯逻辑/协调器。

## 测试约定
- 单测位于 `app/src/test/java/com/example/myapplication/`，当前 660+ 个用例。
- 网络类验证：`AiRepositoryTest` / 对应 Gateway 测试 使用 MockWebServer。
- 状态流/协程：Fake Store + `kotlinx-coroutines-test`（`ChatViewModelTest`、`ConversationRepositoryTest` 为模板）。
- Room：引入 `androidx.room:room-testing`；`ChatDatabaseMigrationRegistryTest` 断言 `ALL_MIGRATIONS` 覆盖每个版本且连续。
- 设备相关：`app:installDebug` + `app:connectedDebugAndroidTest`。

## 项目内高价值约定
- `gradle.properties` 指定 `kotlin.code.style=official`。
- 用户可见文案与错误提示全部使用简体中文；新增 UI 文案保持一致。
- `AppSettings.hasBaseCredentials()` / `AppSettings.hasRequiredConfig()` 是配置门禁统一入口。
- `ApiServiceFactory.normalizeBaseUrl()` 负责 Base URL 规范化，调用方不要重复拼接 `/`。
- Room 版本号一次只能 +1 提交，避免跳版导致 schema 漏导（背景见 `app/schemas/.../README.md`）。
- 任何 Gson 使用点写 `AppJson.gson`，禁止 `Gson()` 自建。
- Compose 订阅写 `collectAsStateWithLifecycle()`。
