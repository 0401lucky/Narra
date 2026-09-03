# 代码复用思考指南

## 先搜索再新增

Narra 已有大量按前缀组织的组件、Support、Coordinator、Repository 和测试
Fake。新增代码前先搜索名称、行为和依赖：

```powershell
rg -n "相近函数名|状态字段|错误文案" app/src/main app/src/test app/src/androidTest
rg --files app/src/main/java/com/example/myapplication | rg "Support|Coordinator|Parser|Codec|Factory"
```

优先检查同一功能前缀：`Chat*`、`Roleplay*`、`Settings*`、`Phone*`、
`Moments*`。

## 项目内单一入口

以下能力已有统一所有者，不要重新实现：

- 依赖装配：`di/AppGraph.kt`
- Gson：`system/json/AppJson.kt`
- 日志与脱敏：`AppLogger`、`SensitiveTextRedactor`
- ViewModel 工厂：`typedViewModelFactory`
- 路由：`ui/navigation/AppRoutes.kt`
- Base URL：`ApiServiceFactory.normalizeBaseUrl()`
- Provider 配置门禁：`AppSettings.hasBaseCredentials()`、
  `hasRequiredConfig()`
- Room 迁移注册：`ChatDatabase.ALL_MIGRATIONS`
- 会话消息变换：`conversation/ConversationMessageTransforms.kt`
- 通用反馈组件：`AppSnackbarHost`、`NarraAlertDialog`

若现有入口缺能力，优先扩展它并补测试，而不是在调用点创建第二套规则。

## 如何选择抽象位置

- 纯、无 IO、可复用的变换：`*Support`、`*Parser`、`*Codec` 或领域包函数。
- 含多个依赖并编排一个用例：`*Coordinator`、Repository 或 Service。
- 页面局部组合：同 feature 的 `XxxScreen*` 文件。
- 跨页面 UI：`ui/component/`；领域专用组件放其子目录。
- 持久化映射：Room Store/Repository，不放 ViewModel。
- 外部协议适配：`data/repository/<domain>/` 的独立 Support。

## 何时抽象

应该抽象：

- 同一逻辑出现 3 次以上。
- 逻辑涉及密钥、URL、序列化、迁移、状态机或错误恢复。
- 多个消费者必须共享同一契约。
- 主类继续膨胀，且新逻辑能形成清晰输入/输出边界。

暂不抽象：

- 只出现一次且非常简单的页面布局。
- 抽象需要大量参数，反而隐藏 feature 语义。
- 只是两个名称相似但生命周期、错误策略不同的流程。

## 真实复用范例

- `TypedViewModelFactory.kt` 收敛重复 Factory 强转。
- `AppJson.kt` 收敛各 Store/Repository 的 Gson 实例。
- `ChatScreenCallbacks.kt` 将大量回调按职责分组。
- `ChatStateSupport.kt` 集中状态变换，避免 ViewModel 分支重复 copy。
- `GatewayNetworkSupport.kt`、`ResponseApiSupport.kt` 分离网络错误和协议适配。
- `testutil/FakeConversationStore.kt` 等手写 Fake 复用测试契约。

## 常见误区

- 复制旧路由的字符串拼接，继续遗漏 `Uri.encode/decode`。
- 为一个新 ViewModel 再写一套匿名 `ViewModelProvider.Factory`。
- 在新 Store 中 `Gson()`，导致序列化配置分叉。
- 在每个 ViewModel 重写 Throwable → 中文文案与脱敏。
- 把新协议继续堆进 `AiGateway.kt`，或把新页面动作继续堆进大型 ViewModel。
- 为了“通用”把 feature 专属规则放进无关全局工具类。

## 完成前检查

- 是否搜索了相近符号、错误文案、状态字段和测试。
- 是否复用了项目级单一入口。
- 是否保持依赖方向，没有让 UI/领域层依赖 Entity、DAO 或系统 Service。
- 抽象名称是否表达真实职责，而不是 `Utils`、`Helper` 之类模糊容器。
- 是否为共享契约补充成功、失败和边界测试。
- 批量修改后是否再次 `rg`，确认没有遗漏旧入口。
