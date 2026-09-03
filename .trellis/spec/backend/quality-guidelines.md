# 质量规范

## 编码基线

- Kotlin 使用官方代码风格和 4 空格缩进，证据为
  `gradle.properties:kotlin.code.style=official`。
- 类、Composable、ViewModel 用 `UpperCamelCase`；函数和属性用
  `lowerCamelCase`；编译期常量用 `UPPER_SNAKE_CASE`。
- 注释只解释关键流程、边界和原因，使用简体中文；不要复述代码。
- 用户可见文案和错误提示使用简体中文。
- 优先 SOLID、DRY、关注点分离和 YAGNI；发现大型主类继续增长时，沿用
  `Support`、`Coordinator`、`Parser`、`Codec` 的现有拆分方式。

当前没有 ktlint、detekt、Jacoco 或覆盖率阈值，禁止把不存在的工具写成
强制门禁。提交前至少使用 IDE 格式化并运行 Android Lint。

## 项目级必用模式

- 依赖装配使用 `AppGraph`，不要在 Activity/ViewModel/Screen 中创建
  Repository、数据库或网络客户端。
- Gson 使用 `AppJson.gson`。
- Base URL 使用 `ApiServiceFactory.normalizeBaseUrl()`。
- ViewModel 工厂使用 `typedViewModelFactory`。
- Compose 收集 `StateFlow` 使用 `collectAsStateWithLifecycle()`。
- 路由常量和构造集中在 `AppRoutes`；新参数统一编码/解码。
- Room schema 只加一版，并同步迁移、注册、schema JSON 和测试。
- 用户错误与日志都必须脱敏；API Key 使用 `SecureValueStore`。

## 测试策略

### JVM 单元测试

- 使用 JUnit4；项目没有 Mockito，优先手写 Fake/Test API。
- 测试名采用 `被测行为_条件_预期结果`，例如
  `sendMessage_failureMarksAssistantMessageAsError`。
- Repository/Coordinator 使用 `runTest` 或现有 `runBlocking` 风格；新增
  协程代码优先 `runTest`。
- ViewModel 使用 `MainDispatcherRule`、
  `runTest(mainDispatcherRule.dispatcher.scheduler)` 和
  `advanceUntilIdle()`。
- 时间、dispatcher、API provider、文件写入通过构造参数/lambda 注入。
- Fake 使用 `MutableStateFlow`、调用计数、最后参数和未预期调用
  `error("不应调用 ...")`，参考 `testutil/`。

### 网络测试

- MockWebServer 在 `@Before` 启动、`@After` 关闭。
- 校验 path、header、JSON body、状态码、协议分支、取消和脱敏。
- 仅 loopback 测试地址允许 HTTP，远端非 HTTPS 必须拒绝。

### Room 测试

- JVM 测试验证 `ALL_MIGRATIONS` 连续且包含最新迁移。
- `androidTest` 使用真实 Room/SQLite 从旧 schema 升级并检查表、列、索引、
  默认值和历史数据。
- 当前迁移测试没有采用 `MigrationTestHelper`；新用例应先沿用现有真实旧库
  构造方式，除非统一重构测试基础设施。

### Compose 与设备测试

- Compose 交互测试放 `androidTest`，使用
  `createAndroidComposeRule<ComponentActivity>()`。
- 直接 `setContent` 渲染目标 Composable，优先 test tag、资源字符串和语义
  断言；回调最终值在 `runOnIdle` 中检查。
- `ExampleInstrumentedTest.kt` 是生成样例，不是新测试范例。

### 性能

- `baselineprofile` 覆盖启动、历史和设置主路径。
- `targetAppId` 从 instrumentation 参数读取，不硬编码包名。
- 涉及启动或关键 Compose 页面时，考虑更新基线路径并执行 profile/宏基准。

## 验证命令

CI 固定门禁：

```powershell
.\gradlew.bat assembleDebug --console=plain
.\gradlew.bat testDebugUnitTest --console=plain
.\gradlew.bat lintDebug --console=plain
```

按改动范围补充：

```powershell
# 单个 JVM 测试
.\gradlew.bat :app:testDebugUnitTest `
  --tests "com.example.myapplication.SomeTest.some_case" `
  --console=plain

# 设备测试源码编译
.\gradlew.bat :app:compileDebugAndroidTestKotlin --console=plain

# 真机或模拟器测试
.\gradlew.bat connectedDebugAndroidTest --console=plain

# baseline
.\gradlew.bat assembleBaseline --console=plain
.\gradlew.bat app:generateBaselineProfile --console=plain
```

后台单元测试最大等待 60 秒；超时只能报告“未取得结果”，不能写成通过。

## 审查清单

- 依赖方向是否仍是 UI → ViewModel → Repository/Domain → Store/API。
- 是否复用了 AppGraph、AppJson、AppRoutes、typedViewModelFactory、
  SensitiveTextRedactor 等现有入口。
- 异步流程是否处理成功、失败、取消、重复点击和状态恢复。
- StateFlow 是否生命周期安全，是否避免高频状态导致大范围重组。
- 数据库改动是否具备连续迁移和真实旧库验证。
- 网络改动是否覆盖协议、URL 安全、错误体、取消和密钥脱敏。
- 新逻辑是否有成功、异常、边界和状态切换测试。
- 是否只修改任务范围文件，未夹带临时截图、日志或无关工作区改动。

## 禁止模式

- 裸 `collectAsState()` 订阅 ViewModel 状态。
- 在业务代码中直接 `Gson()`、直接 `android.util.Log` 或 `println`。
- 在 UI/ViewModel 直接操作 DAO/Entity、拼 URL 或解析 SSE。
- 捕获 `CancellationException` 后转成普通错误。
- 记录密钥、完整请求/响应、Base64、聊天内容、本地路径或 SQL。
- 使用 destructive migration、跳版本或不提交 Room schema。
- 为单次需求复制大型代码块，而不先搜索现有 Support/Coordinator/组件。
