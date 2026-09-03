# 2.2.6-dev 应用性能与稳定性专项：技术设计

## 1. 任务边界与拆分

本任务作为父专项，拥有统一需求、指标、跨子任务回归和最终集成复测。实现阶段建议拆为
五个可独立验收的子任务：

```text
应用性能与稳定性专项（父任务）
├── P0 基准、Trace、Profile 与崩溃可观测性
├── P0 启动、设置共享与导航性能
├── P0 RP Compose 与流式帧性能
├── P0 运行时异常、取消与资源边界
└── P1 会话 mutation 与跨表一致性
```

对应任务目录：

- `08-11-performance-observability-baseline`
- `08-11-startup-settings-navigation-performance`
- `08-11-roleplay-compose-performance`
- `08-11-runtime-stability-resource-guards`
- `08-11-data-mutation-consistency`

父任务不直接承载大面积产品代码修改；每个子任务拥有自己的 PRD、设计、实施清单、定向
测试和回滚点。基准基础设施先完成；启动 / 设置子任务随后完成
`SettingsViewModel` 的订阅区域，运行时子任务再修改同文件中互不重叠的声音样本区域。
RP 与数据一致性子任务可在无文件冲突时并行，最后由父任务做同设备集成复测。

## 2. 测量与证据架构

```text
baseline-only fixture / 诊断开关（测量区间外）
  → 强断言 ready 合约
  → Startup / FrameTiming / Perfetto / Compose reports
  → 保存优化前原始结果
  → 修复代码级稳定性缺陷或 Trace 证实热点
  → 同设备、同构建、同动作复测
  → 父任务集成回归与结论分级
```

### 2.1 Fixture 与隔离

- 仅 `BuildConfig.APP_CHANNEL == "baseline"` 且收到显式 benchmark 指令时允许准备 fixture
  或关闭基准污染源。
- fixture 幂等准备合规状态、固定助手 / 场景 / 会话、普通聊天和 RP 各 80～120 条消息；
  不包含真实密钥、附件和远端请求。
- 自动更新检查、Moments 周期 Worker 和其它远端任务只在该 benchmark 会话内隔离；dev /
  release 产品行为不改变。
- fixture、数据库准备和滚动位置恢复都在 `measureBlock` 外执行。

### 2.2 Ready 与场景矩阵

- S0：干净安装只到合规门禁 ready。
- S1：已确认合规、空 / 小数据库，RP 核心数据 ready。
- S2：普通聊天和 RP 各 80～120 条固定消息的 populated 启动。
- S3：受支持旧设置 / schema 快照升级启动，单列迁移耗时。
- F1：普通聊天底部 → 顶部 → 底部固定滚动。
- F2：RP 相同轨迹固定滚动。
- F3：本地固定 chunk 按 48 ms 重放 RP streaming。
- N1：设置页和 RP 阅读页重页面导航转场。
- P1：启动、RP、聊天历史、设置、角色音色 Sheet 的 Profile 路径。

所有等待返回 false 时立即失败；UI 文案不是长期 ready 合约，优先使用稳定语义标记或
明确状态节点。

### 2.3 指标

- 冷启动：`StartupTimingMetric`，None 与 Require Baseline，各 10 次；必要时 Full 仅用于
  诊断，不混入主结论。
- 滚动 / 流式 / 导航：`FrameTimingMetric`，各至少 5 次，保存 P50 / P90 / P95 和原始
  Perfetto。
- Trace section：设置完整解析、SecureValue 解密、Room 首次打开、AppGraph 任务、首个
  消息映射、RP mapMessages。
- Profile：源码和合并 profile 必须出现应用命名空间规则，并验证设备安装状态。
- Compose：每个变体使用独立报告目录或固定最后编译目标，立即归档完整 stability /
  skippability 输出。

## 3. 崩溃与 ANR 诊断设计

### 3.1 推荐默认方案：本地脱敏诊断

- Android 11+ 在下一次启动读取 `ApplicationExitInfo`，只保留 reason、时间、版本、
  PSS / RSS 和脱敏摘要。
- 诊断文件写入 `noBackupFilesDir`，限制条数和总大小，轮转覆盖旧记录。
- 复用 `AppLogger.Sink` 的脱敏能力，但诊断写入失败不能成为新的崩溃源。
- 用户主动触发导出；不自动上传聊天正文、Prompt、API Key、完整 DataStore / Room 行或
  ANR trace。
- debug / 独立诊断变体可启用 StrictMode `penaltyLog`；正式基准不启用会改变时序的
  `penaltyDeath`。

### 3.2 远程方案边界

2.2.6-dev 不接入 Crashlytics、Sentry 或其它远程崩溃 SDK。未来如需跨设备聚合，必须
单独明确用户同意、采集开关、保留期、隐私条款、mapping / native symbols 上传和
baseline 禁用规则，不能从本地诊断实现中隐式扩展。

## 4. 启动、设置与导航设计

### 4.1 启动任务

- AppGraph 每项启动任务拥有独立错误契约：可降级任务记录并继续，必需任务转换为可见的
  安全状态；取消原样传播。
- DataStore `IOException` 使用官方 catch / fallback 模式；其它异常继续抛出，以便暴露
  编程错误。
- 启动任务增加 trace section，不以全局异常处理器替代局部恢复策略。

### 4.2 设置共享

- 在应用图范围建立唯一 `StateFlow<AppSettings>`，上游完整解析只执行一次。
- DataStore / Keystore / Gson 转换在 repository 内明确后台上下文；共享层不持久化额外明文。
- AppRoot 只收集 `ThemeState` 等窄投影并 `distinctUntilChanged()`。
- SettingsViewModel 的持久设置与草稿同步复用同一共享流，消除 eager stateIn + 原始 collect
  双轨解析。

### 4.3 导航

- 页面切换基准覆盖 RP 起点、设置和阅读页；先定位首次组合、设置解析或消息映射成本，
  不把 240～400 ms 动画时长本身直接视为根因。
- 不通过简单删除动画来掩盖重页面初始化；若动画确实超出帧预算，再单独调整时长 / spec。

## 5. RP Compose 性能设计

### 5.1 稳定 identity

- mapper 为每个 UI 气泡提供稳定唯一 `uiItemId`：底层 message ID + 消息内稳定分段身份；
  同一底层 assistant 消息拆成多个气泡时仍必须唯一。
- 三个 Lazy 列表 key 只使用 `uiItemId`，不包含正文、随机值或全局 index。
- 按文本、图片、动作、语音、系统等有限类别提供稳定 `contentType`。

### 5.2 流式状态闸门

- 首先用稳定 key 和基准复测。
- 只有 Trace 仍证明 `RoleplayMessageUiMapper.mapMessages` / 完整 UiState 发布是主要成本，
  才参考普通聊天拆出高频 streaming StateFlow。
- 持久消息映射由低频实体 / 设置输入触发；流式气泡按稳定 identity 在展示层合并。
- 完成、失败、取消、切换会话仍回到唯一状态所有者。

### 5.3 滚动与动画

- RP 两种模式复用普通聊天的“用户在底部附近才跟随 + 单一长生命周期协程 + 节流信号”
  策略，避免正文长度作为 `LaunchedEffect` 高频重启 key。
- 流式正文 `AnnotatedString` 只依赖正文缓存，光标动画使用独立轻量节点 / 绘制层。
- 语音卡只在播放态创建无限动画，静止态使用常量；播放器使用异步 prepare 并保证释放。

## 6. 运行时异常、IO 与资源设计

- 所有宽泛捕获先重抛 `CancellationException`；翻译等替换型任务使用 run id / Job identity，
  只有当前任务可写共享状态。
- `DownloadManager.query()`、文件读取、Base64、导出序列化 / 写入移出 Main，并保留 UI
  生命周期取消语义。
- 声音样本在完整读取和编码前检查文件长度；TTS HTTP body 与 Base64 音频在大分配前设置
  协议级最大字节数。
- Bitmap / Image / MediaProjection 资源使用 `try/finally`；当前禁用的翻译组件不在本任务
  中启用，但其启用前风险可在独立子任务内修复和测试。

## 7. 数据 mutation 与事务设计

- 编辑、删除、重试、发送使用会话级串行化所有权，禁止旧快照在新发送后整体覆盖。
- 数据库范围的剧情删除 / 重启和关联清理进入单一 Room 事务；文件删除在事务提交后执行，
  失败记录为可重试清理，不伪装成 Room 回滚。
- 通过可控 dispatcher 和故障注入覆盖两种交错顺序及每个事务失败点。
- 不修改 Room schema；若现有 DAO / Store 无法提供事务边界，再回到规划阶段评估，而不是
  隐式扩大范围。

## 8. 兼容、风险与回滚

- 共享设置流影响范围广：先补行为测试和计数 / Trace，再逐消费者迁移；保留单次回滚点。
- RP identity 改动可能暴露重复分段：先证明稳定且唯一，再接入 Lazy 列表。
- 状态拆分、mutation 串行化和事务修改分别独立提交 / 回滚，不与基准基础设施混在一起。
- 指标无改善时回退对应优化，保留有效基准、诊断和回归测试。
- 没有固定物理设备时可以完成功能 smoke 和静态门禁，但不能宣布性能阈值已通过。
