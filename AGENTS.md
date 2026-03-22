# Repository Guidelines

## 项目结构与模块组织
`app` 是主应用模块。业务代码位于 `app/src/main/java/com/example/myapplication`：`ui/` 放 Compose 界面与导航，`viewmodel/` 负责状态协调，`data/` 包含 Room、Retrofit 和仓储实现，`context/`、`roleplay/`、`system/translation/` 承载核心领域逻辑。资源文件位于 `app/src/main/res`，生成的基线配置在 `app/src/main/generated/baselineProfiles`。本地单元测试在 `app/src/test/java`，设备与 UI 测试在 `app/src/androidTest/java`。`baselineprofile` 模块维护性能基线脚本，`docs` 保存版本与发版约定。

## 构建、测试与开发命令
- `.\gradlew.bat assembleDebug`：构建开发包。
- `.\gradlew.bat assembleBaseline`：构建性能验证包。
- `.\gradlew.bat testDebugUnitTest`：运行 JVM 单元测试。
- `.\gradlew.bat connectedDebugAndroidTest`：运行真机或模拟器测试。
- `.\gradlew.bat lintDebug`：执行 Android Lint。
- `.\gradlew.bat app:generateBaselineProfile`：生成并回写 baseline profile。

## 编码风格与命名
Kotlin 统一使用 4 空格缩进，遵循 Android Studio 默认格式。类、Composable、ViewModel 使用 `UpperCamelCase`，函数和属性使用 `lowerCamelCase`，常量使用 `UPPER_SNAKE_CASE`。测试命名延续现有风格，如 `sendMessage_failureMarksAssistantMessageAsError`。注释只写关键流程，优先使用中文。仓库当前未发现 ktlint 或 detekt 配置，提交前至少执行 IDE 格式化和 `lintDebug`。

## 测试要求
单测以 JUnit4、`kotlinx-coroutines-test` 和 `MockWebServer` 为主；设备测试使用 AndroidX Test 与 Compose UI Test；性能验证放在 `baselineprofile`。新增 `viewmodel/`、`data/repository/`、`context/` 下逻辑时，应补充成功路径、异常路径和状态切换测试。测试文件统一以被测类名加 `Test` 结尾，例如 `ChatViewModelTest.kt`。

## 提交与 PR 约定
当前目录未包含 `.git` 元数据，无法从历史归纳提交规范；建议临时使用 `type(scope): summary`，例如 `feat(chat): 支持消息记忆切换`。PR 应说明目的、影响模块、已执行命令；涉及 Compose 页面、悬浮翻译或性能基线时附截图或录屏。发版相关改动需同步更新 `CHANGELOG.md`、`docs/版本策略.md` 和 `docs/发布与更新约定.md`。

## 配置与安全
不要提交真实 API Key，也不要依赖已修改的 `local.properties` 作为共享配置。版本号、渠道名和 APK 命名规则以 `app/build.gradle` 为准；发布前确认 `release`、`dev`、`baseline` 三个渠道未混用。
