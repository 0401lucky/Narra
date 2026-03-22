# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概览
- 当前仓库是单模块 Android 应用，唯一业务模块为 `:app`。
- 技术栈：Kotlin、Jetpack Compose、Material 3、Navigation Compose、Lifecycle ViewModel、StateFlow、DataStore Preferences、Room、Retrofit、OkHttp、Gson、KSP。
- 构建脚本使用 Groovy DSL，依赖版本集中在 `gradle/libs.versions.toml`。
- 当前仓库没有根级 `README.md`、`.cursorrules`、`.cursor/rules/*` 或 `.github/copilot-instructions.md`，因此仓库级协作说明以本文件为准。

## 常用命令
所有命令都在仓库根目录执行，Windows 环境使用 `./gradlew.bat`。

```bash
./gradlew.bat assembleDebug
./gradlew.bat app:lint
./gradlew.bat test
./gradlew.bat app:testDebugUnitTest
./gradlew.bat app:installDebug
./gradlew.bat app:connectedDebugAndroidTest
```

### 运行单个测试
```bash
./gradlew.bat app:testDebugUnitTest --tests "com.example.myapplication.viewmodel.ChatViewModelTest"
./gradlew.bat app:testDebugUnitTest --tests "com.example.myapplication.viewmodel.ChatViewModelTest.retryMessage_retriesFailedMessageAndReplacesErrorContent"
```

## 架构总览

### 应用入口与装配
- `app/src/main/java/com/example/myapplication/MainActivity.kt`
  - 应用组合根。
  - 这里手工创建 `AppSettingsStore`、`RoomConversationStore`、`ConversationRepository`、`AiRepository`，再通过 `setContent {}` 进入 Compose。
  - 当前没有 DI 框架；新增仓库、Store 或数据库依赖时，优先沿用这里的手工装配方式。

### Compose 根与导航
- `app/src/main/java/com/example/myapplication/ui/AppRoot.kt`
  - 负责创建 `SettingsViewModel` 和 `ChatViewModel`，两者都通过 `viewModel(factory = ...)` 注入依赖。
- `app/src/main/java/com/example/myapplication/ui/navigation/AppNavHost.kt`
  - 同时收集 `settingsViewModel.uiState` 与 `chatViewModel.uiState`，然后把状态和回调桥接给页面。
  - 当前路由为 `HOME`、`SETTINGS`、`CHAT`，`startDestination` 是 `CHAT`。

### 数据职责边界
- `app/src/main/java/com/example/myapplication/data/repository/AiRepository.kt`
  - 只负责远程能力与设置持久化：读取/保存 `AppSettings`、拉取模型、发送聊天请求。
  - 网络客户端由 `data/remote/ApiServiceFactory.kt` 创建，接口定义在 `data/remote/OpenAiCompatibleApi.kt`。
- `app/src/main/java/com/example/myapplication/data/repository/ConversationRepository.kt`
  - 只负责本地会话与消息规则：确保默认会话、新建/删除/清空会话、保存消息、切换会话后的读取、根据首条用户消息生成标题。
  - 删除最后一个会话后会自动补建默认会话。
- `app/src/main/java/com/example/myapplication/data/local/AppSettingsStore.kt`
  - 使用 DataStore Preferences 持久化 `baseUrl`、`apiKey`、`selectedModel`。
- `app/src/main/java/com/example/myapplication/data/local/ConversationStore.kt` + `RoomConversationStore.kt`
  - 定义本地会话抽象并用 Room 实现。
  - Room 实体、DAO、数据库位于 `app/src/main/java/com/example/myapplication/data/local/chat/`。

### ViewModel 状态流
- `app/src/main/java/com/example/myapplication/viewmodel/SettingsViewModel.kt`
  - 管理设置表单、模型列表加载、保存状态与一次性消息。
- `app/src/main/java/com/example/myapplication/viewmodel/ChatViewModel.kt`
  - 同时观察设置流、会话列表流和当前会话消息流，汇总为 `ChatUiState`。
  - 发送消息时先乐观落库用户消息与 assistant loading 消息，再调用 `AiRepository.sendMessage()` 回填完成或失败状态。
  - 失败 assistant 消息支持重试，消息状态使用 `COMPLETED`、`LOADING`、`ERROR`。

## 目录约定
- `app/src/main/java/com/example/myapplication/model/`：领域模型、DTO、枚举。
- `app/src/main/java/com/example/myapplication/data/local/`：DataStore、Room、Store 抽象与实现。
- `app/src/main/java/com/example/myapplication/data/remote/`：Retrofit API 定义与客户端工厂。
- `app/src/main/java/com/example/myapplication/data/repository/`：业务仓库。
- `app/src/main/java/com/example/myapplication/viewmodel/`：页面状态与交互编排。
- `app/src/main/java/com/example/myapplication/ui/navigation/`：路由和导航图。
- `app/src/main/java/com/example/myapplication/ui/screen/<feature>/`：页面级 Compose 组件。
- `app/src/main/java/com/example/myapplication/ui/component/`：可复用 Compose 组件。

## 测试约定
- 本地单测位于 `app/src/test/java/com/example/myapplication/`。
- `AiRepositoryTest` 使用 MockWebServer 验证网络请求与错误映射。
- `ConversationRepositoryTest`、`ChatViewModelTest` 使用 Fake Store + `kotlinx-coroutines-test` 验证业务规则和状态流转。
- Room 已接入 `androidx.room:room-testing`，需要数据库测试时优先沿用现有依赖。
- 设备相关验证使用 `app:installDebug` 与 `app:connectedDebugAndroidTest`；执行前确认模拟器或真机已连接。

## 项目内高价值约定
- `gradle.properties` 指定 `kotlin.code.style=official`。
- 用户可见文案与错误提示已使用简体中文；新增 UI 文案请保持一致。
- `AppSettings.hasBaseCredentials()` 与 `AppSettings.hasRequiredConfig()` 是配置门禁的统一入口。
- `ApiServiceFactory.normalizeBaseUrl()` 负责 Base URL 规范化；不要在调用方重复拼接 `/`。
- 当前没有 DI 框架，也没有额外的 module 划分；新增依赖时优先保持现有简单结构，而不是引入新的装配体系。
