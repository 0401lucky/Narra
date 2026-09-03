# 跨层思考指南

## 先画真实数据流

跨层功能开始前，写出本次数据实际经过的节点，而不是只列文件：

```text
用户动作
  → Compose 回调
  → ViewModel UiState / Job
  → Repository / Coordinator
  → Store / DAO / Retrofit / Android Service
  → 持久化或远端响应
  → 领域模型 / Flow
  → ViewModel 状态
  → Snackbar、页面或消息气泡
```

对每个箭头确认输入格式、输出格式、线程/协程、失败方式、取消方式、所有者和
测试位置。

## 主要边界

### UI ↔ ViewModel

- Screen 接收不可变状态和 `onXxx` 回调。
- Compose 使用 `collectAsStateWithLifecycle()`。
- 一次性消息显示后消费；长任务有取消和状态恢复。
- 高频流式状态是否需要从主 `UiState` 拆分。

### ViewModel ↔ Repository / Domain

- ViewModel 不接触 Entity、DAO、Retrofit Response 或原始 SSE。
- 输入在用例边界 trim/coerce/normalized。
- 可预期业务失败用 sealed result；异常失败进入统一安全映射。
- 系统时间、dispatcher、文件写入等是否可注入测试。

### Repository ↔ Room / DataStore

- Entity 与领域模型的映射是否唯一。
- 多表操作是否需要 `@Transaction` 或 `withTransaction`。
- 新字段的默认值、旧值兼容、索引、FK/CASCADE 是否明确。
- schema、迁移注册、JVM 连续性测试和真实旧库设备测试是否同步。
- 密钥是否从普通 JSON 分离到 `SecureValueStore`。

### Repository ↔ 网络协议

- Base URL 是否只由 `ApiServiceFactory` 规范化。
- Provider 协议和 API mode 是否显式分支。
- 非流式 Response、流式 SSE、取消和资源关闭是否分别处理。
- 错误体是否脱敏，兼容重试是否有精确触发条件和幂等性评估。
- MockWebServer 是否验证 path、header、body 和敏感信息不泄漏。

### Android 系统能力

- Service/Activity 是否需要 manifest 权限、exported、enabled 或 foreground
  service type 配置。
- Compose 性能测试的 `testTag` 是否真的通过当前窗口的 `testTagsAsResourceId` 暴露给
  UIAutomator；Sheet、Dialog、Popup 等独立窗口是否安装了自己的资源 ID host。
- UIAutomator 动作是否统一经过强 helper，点击前是否重新获取稳定节点、避开上下系统栏，
  查找、滚动和重试是否共享同一 deadline。
- Baseline Profile 多轮采样是否使用场景匹配的 phase / TTL；模拟器 smoke 与固定物理设备
  性能阈值是否明确分开记录。
- MediaProjection、无障碍、悬浮窗、文件和播放器资源是否正确释放。
- system 包是否错误反向依赖 ViewModel/UI 常量；共享契约应下沉 model/domain。
- 后台失败如何反馈当前用户，且不把内部异常直接显示到悬浮窗。

## 典型跨层流程

### 聊天发送

```text
ChatScreen → ChatViewModel → PromptContextAssembler / ConversationRepository
→ AiGateway → Flow<ChatStreamEvent> → ChatStreamingState / Room 消息状态
```

需要同时检查：配置门禁、乐观消息、取消、流式资源关闭、`MessageStatus`
转换、失败重试、切换会话后的旧任务写入和持久化一致性。

### 设置与密钥

```text
SettingsScreen → SettingsViewModel → AiSettingsEditor / AppSettingsStore
→ DataStore（非敏感字段）+ SecureValueStore（API Key）
→ settingsFlow → 全局 ViewModel / AppRoot
```

需要同时检查：旧明文迁移、JSON 中清空 key、解密失败默认、备份排除和 UI
不显示秘密。

### Room schema 变更

```text
Entity/DAO → CURRENT_VERSION → MIGRATION_N_N+1 → ALL
→ AppGraph 注册 → schema JSON → JVM 连续性测试 → 设备真实升级测试
```

任何一项遗漏都不算完成。

### 应用更新

```text
BuildConfig 元数据地址 → AppUpdateRepository → URL 白名单 / SHA-256
→ 下载控制器 → 安装权限与 Intent → AppUpdateViewModel → DialogHost
```

需要检查渠道、可信 host、路径、哈希、缓存回退、下载状态和用户可选更新。

## 常见跨层缺陷

- 数据库字段已加但迁移、schema 或映射未同步。
- 路由写入未编码、读取未解码，特殊字符导致目的地参数错误。
- Repository 返回安全结果，但 ViewModel 又直接使用原始 Throwable message。
- 取消被当失败，旧请求在新会话/新场景写入状态。
- 多个消费者各自解析同一 JSON/SSE/字符串枚举，契约逐渐分叉。
- UI 直接依赖数据实现类，或 system Service 反向依赖 ViewModel。
- Provider 配置同时存在 DataStore 和安全存储，更新一边遗漏另一边。

## 实施后检查

- 完整数据流是否实际跑通，而不只是每层单测通过。
- 空、非法、旧版本、取消、超时、重复点击、进程重建是否有定义。
- 错误和日志是否都经过脱敏。
- 状态是否能从失败/取消恢复，资源是否关闭。
- 所有共享契约是否只有一个解析/转换所有者。
- 是否运行与改动匹配的 JVM、Lint、构建、设备或迁移测试。
