# 目录与架构规范

## 模块边界

- `app/`：唯一业务应用模块，包含主代码、资源、JVM 测试、设备测试和
  Room schema。
- `baselineprofile/`：`com.android.test` 模块，只保存基线生成器和宏基准。
- `docs/`：版本、发布、更新元数据与流程约定。
- `app/schemas/`：Room 通过 KSP 导出的版本化 schema，不是临时构建产物。

应用入口保持轻量：

- `ChatApplication.kt` 创建并暴露 `AppGraph`，启动任务走
  `AppGraph.launchStartupTasks()`。
- `MainActivity.kt` 只负责 edge-to-edge 和 `setContent { AppRoot(...) }`。
- `ui/AppRoot.kt` 安装主题、应用级 ViewModel、导航与全局弹窗。

## 主源码布局

```text
app/src/main/java/com/example/myapplication/
├── di/                 # AppGraph 手写依赖装配
├── model/              # 领域模型、DTO、枚举、存储值转换
├── data/
│   ├── local/          # Room、DataStore、Keystore、Store 实现
│   ├── remote/         # Retrofit 接口、OkHttp 客户端工厂
│   └── repository/     # 领域仓储与外部能力适配
├── context/            # 上下文组装、记忆/世界书选择等纯逻辑
├── conversation/       # 会话流程协调与消息变换
├── roleplay/           # 角色扮演协议、解析器与领域规则
├── viewmodel/          # 页面状态、交互编排、Support/Coordinator
├── ui/
│   ├── navigation/     # AppRoutes、NavGraph、图级 ViewModel owner
│   ├── screen/         # 按 feature 分组的完整页面
│   ├── component/      # 可复用组件及领域专用组件
│   └── theme/          # Compose 主题
└── system/             # 更新、翻译、日志、安全、JSON 等系统能力
```

测试源集镜像业务包：

- `app/src/test/java/...`：JUnit4、协程、MockWebServer、纯逻辑与配置守卫。
- `app/src/androidTest/java/...`：Compose UI、Room 真实迁移和设备能力测试。
- `baselineprofile/src/main/...`：`BaselineProfileGenerator`、启动宏基准。

## 依赖与职责

- `ui/screen` 以不可变状态和 `onXxx` 回调为主，不直接处理 DAO、Retrofit
  或数据库事务。标准示例为 `ChatScreen.kt`、`PhoneCheckScreen.kt`。
- `ui/navigation` 是页面装配边界：从 `AppGraph` 取依赖、创建 ViewModel、
  用 `collectAsStateWithLifecycle()` 收集状态并绑定回调。
- `viewmodel` 持有 UI 状态、启动 `viewModelScope` 任务并协调 Repository；
  不反向依赖 `ui` 包。
- `repository` 负责用例、输入规范化、领域模型和存储模型转换；DAO/Entity
  不向 ViewModel 或 UI 泄漏。
- `context`、`conversation`、`roleplay` 优先保存无 Android 依赖的纯规则；
  Android 实现使用明确前缀，例如 `AndroidRoleplayScriptEngine`。
- `system` 承载 Android 服务或全局基础设施。跨层常量和业务规则不要放进
  Service 或 ViewModel。

## 新代码放置规则

1. 新页面放入 `ui/screen/<feature>/`，跨页面组件放 `ui/component/`。
2. 新路由先登记到 `AppRoutes.kt`，再在对应 `registerXxxGraph` 中注册。
3. 页面状态放 `XxxUiState`；纯状态变换放 `XxxStateSupport`，含依赖和 IO
   编排的流程使用 `XxxCoordinator` 或领域服务。
4. 新存储能力先定义 Store/Repository 边界，再在 `AppGraph` 中以 `by lazy`
   装配；不要在调用点 `new Repository`。
5. Entity、DAO 放 `data/local/<domain>/`；网络端点放 `data/remote/`；复杂协议
   适配放 `data/repository/<domain>/` 或独立 `*Support`。
6. 当主类继续增长时，沿用现有拆分方式，不把新协议和解析逻辑继续堆入
   `ChatViewModel`、`RoleplayViewModel`、`AiGateway` 或 `AppSettingsStore`。

## 命名规则

- 类、Composable、ViewModel：`UpperCamelCase`。
- 函数、属性、回调：`lowerCamelCase`，UI 回调以 `onXxx` 命名。
- 编译期常量：`UPPER_SNAKE_CASE`；Compose 尺寸等文件级稳定值可沿用
  `PascalCase`，例如 `AppNoticeHorizontalPadding`。
- 页面：`XxxScreen`；状态：`XxxUiState`；DAO：`XxxDao`；Room 模型：
  `XxxEntity`；实现：`RoomXxxRepository`、`DefaultXxx`、`EmptyXxx`。
- 导航注册函数：`registerXxxNavGraph`；共享 ViewModel 创建函数：
  `rememberXxxViewModel`；测试文件：`被测类型 + Test`。

## 现有兼容边界

- ViewModel 当前平铺在 `viewmodel/`，依靠 `Chat*`、`Roleplay*`、`Settings*`
  前缀聚类。不要为单个小文件另建空洞层级。
- 少量 Screen 仍直接接收 ViewModel，少量 UI 文件直接调用图片/音频存储；
  这是现存边界，不应扩散到普通页面。
- 路由仍是字符串协议，且部分旧路由编码不一致。新增带参数路由必须同时
  使用 `Uri.encode` 和 `Uri.decode`，不能复制未编码的旧写法。

## 参考实现

- `di/AppGraph.kt`
- `ui/navigation/AppNavHost.kt`
- `ui/navigation/ChatNavGraph.kt`
- `ui/navigation/NavigationViewModelOwners.kt`
- `data/local/RoomConversationStore.kt`
- `data/repository/ConversationRepository.kt`
- `viewmodel/TypedViewModelFactory.kt`
