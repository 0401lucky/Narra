# 性能基准与可观测性规范

## Scenario：baseline-only 基准、Ready 与 Profile

### 1. Scope / Trigger

以下改动必须遵守本规范：Macrobenchmark、Baseline Profile、benchmark fixture / session、
Compose ready / action 标签、UIAutomator 交互、Trace 消费和性能结果归档。

模拟器只用于 fixture、ready 和交互 smoke。启动、帧分位数、Profile 安装与回归阈值必须来自
固定物理设备；缺少设备或温控条件时只能记录“未取得结果”。

### 2. Signatures

```kotlin
data class BenchmarkSessionDescriptor(
    val schemaVersion: Int,
    val sessionId: String,
    val scenarioId: BenchmarkScenarioId,
    val fixtureVersion: String,
    val phase: BenchmarkPhase,
    val createdAtEpochMillis: Long,
    val ttlMillis: Long,
)

enum class BenchmarkPhase { PREPARE, MEASURE, PROFILE }

@Composable
fun Modifier.benchmarkReadyTag(readyId: String, ready: Boolean): Modifier

@Composable
fun Modifier.benchmarkResourceIdHost(): Modifier

internal class ReadyAssertions {
    fun waitForReadyOrThrow(readyId: String, timeoutMillis: Long = 10_000L)
    fun findAndClickOrThrow(actionId: String, timeoutMillis: Long = 10_000L)
    fun clickAndWaitForReadyOrThrow(actionId: String, readyId: String)
}
```

### 3. Contracts

- benchmark 控制面只在 `BuildConfig.APP_CHANNEL == "baseline"` 且显式会话校验通过时启用。
- P1 必须使用 `PROFILE` phase，TTL 最多 30 分钟；其它场景必须使用 `MEASURE`，TTL 最多
  5 分钟。场景与 phase 不匹配必须拒绝。
- ready / action ID 必须来自唯一登记目录，禁止用页面文案、随机 ID、会话 ID 或用户数据拼接。
- ready 必须挂在已经布局、真实可见且属于目标页面的实体节点；不要依赖 `LazyColumn`、
  `AnimatedContent` 等容器节点恰好向 UIAutomator 暴露资源 ID。
- 列表底部出现 benchmark-only 操作条、状态条或 footer 并改变 viewport 时，允许在该实体安装后
  触发一次非动画确定性贴底；不得随每个流式 chunk 重启 effect，也不得抢回用户主动离底的
  产品路径。
- 流式列表 ready 应验证末项已可见、不能继续向下滚动且滚动已停止；不要再叠加末项像素边界
  等价判断。Compose content padding、IME、状态条和舍入误差会让像素几何长期不稳定。
- 独立 Sheet / Dialog / Popup 拥有独立语义树时，必须在该窗口根层调用
  `benchmarkResourceIdHost()`，再把 ready 挂到窗口内实体节点。
- 动作查找、滚动、750 ms 点击宽限和条件重试共享一个 action deadline。deadline 到达后只允许
  采样 `benchmark_*` 标签用于脱敏报错，不得继续点击、等待 idle 或滚动。
- ready 超时诊断只允许一次 `benchmark_*` 资源 ID 扫描，并在登记目录中过滤；禁止逐个登记 ID
  重复扫描完整 UI 树，也禁止在 `device.wait` 超时后再次对同一 selector 做等价全树查询。
- 点击前必须重新获取稳定节点，并确认其可见边界位于状态栏和导航栏安全区内；禁止直接对旧
  `UiObject2` 引用点击。
- fixture 准备、复位、环境采集和会话清理位于测量区间外；任一前置条件失败必须终止场景。
- 完整场景归档必须包含固定矩阵，缺少场景、Trace 或 F3 接线时明确失败，不能产出伪成功结果。

### 4. Validation & Error Matrix

| 条件 | 结果 |
| --- | --- |
| dev / release 或缺少显式会话 | 不启用 fixture、ready 和后台隔离策略 |
| P1 使用非 `PROFILE`，或其它场景使用非 `MEASURE` | `INVALID_PHASE` |
| Profile TTL 超过 30 分钟，或 Measure TTL 超过 5 分钟 | `INVALID_TTL` |
| ready / action 未登记或超时 | 抛出安全断言并终止当前场景 |
| 状态条改变列表 viewport | 状态条安装后至多一次非动画贴底，再由末项可见 / 不可下滚 / 停止条件判定 |
| 目标节点落入顶部或底部系统栏 | 不点击；在剩余 deadline 内滚动到安全区，否则失败 |
| deadline 已到 | 只采样可见 `benchmark_*` 标签，不再产生 UI 动作 |
| Sheet / Dialog 未安装资源 ID host | 修复窗口语义宿主，不得放宽 ready 断言 |
| 缺少必需 Trace 或完整场景 | 归档门禁失败或标记不可判定，不以 0 ms 冒充已测量 |
| 无固定物理设备 | 只记录 smoke，AC4 / AC8 保持未验收 |

### 5. Good / Base / Bad Cases

- Good：P1 使用 S2 fixture、`PROFILE` phase 和 30 分钟以内 TTL，多轮 Profile 收集均通过
  强 ready；角色音色 Sheet 在独立窗口暴露资源 ID，点击始终位于系统栏安全区外。
- Base：动作在首轮查找时不可见，helper 在同一 deadline 内固定滚动、重新获取稳定节点并点击；
  ready 未出现时最多进行一次有条件重试。
- Base：F3 初始状态条出现后列表 viewport 缩小，应用只做一次非动画贴底；ready 挂在状态条可见
  文案上，并要求末项可见、`canScrollForward == false`、滚动停止。
- Bad：把 ready 挂在 `LazyColumn` 或未安装 host 的 Sheet 根语义树，然后通过增加 sleep、忽略
  wait 返回值或直接点击旧节点让测试“通过”。
- Bad：对长列表逐个执行全部 benchmark ID 查询来生成超时诊断，导致 10 秒真实超时被放大为
  数十秒甚至触发外层 JUnit 超时。

### 6. Tests Required

- JVM：渠道、schema、session ID、场景 / phase、TTL、过期和 session mismatch 拒绝矩阵。
- 静态合约：所有场景共用 `ReadyAssertions`；禁止忽略 `device.wait`、nullable click、直接二次
  `UiObject2.click()` 和 deadline 后继续动作。
- JVM：流式 ready viewport 纯判断覆盖末项不可见、仍可下滚、仍在滚动和稳定到底四种状态。
- UI / 设备 smoke：错误 ready、缺失 action、独立 Sheet / Dialog、快捷切换页和返回路径必须
  产生明确成功或失败结果。
- Profile：至少执行一次真实多轮 P1 收集，核对源码与当前合并 Profile 都包含
  `Lcom/example/myapplication/` 规则。
- 正式性能验收：固定物理设备记录刷新率、动画缩放、电量和温控，按规定迭代保存 JSON、
  Perfetto、环境清单与 SHA-256。

### 7. Wrong vs Correct

#### Wrong

```kotlin
// 容器或独立窗口不一定向 UIAutomator 暴露该 testTag。
LazyColumn(Modifier.testTag(BenchmarkReadyIds.ROLEPLAY_QUICK_SETTINGS_READY)) { /* ... */ }

val target = device.findObject(selector)
target?.click() // 旧节点、无系统栏检查、无统一 deadline
```

#### Correct

```kotlin
ModalBottomSheet(
    modifier = Modifier.benchmarkResourceIdHost(),
    onDismissRequest = onDismiss,
) {
    Text(
        text = title,
        modifier = Modifier.benchmarkReadyTag(
            readyId = BenchmarkReadyIds.VOICE_PROFILE_READY,
            ready = true,
        ),
    )
}

readyAssertions.clickAndWaitForReadyOrThrow(
    actionId = BenchmarkActionIds.OPEN_VOICE_PROFILE,
    readyId = BenchmarkReadyIds.VOICE_PROFILE_READY,
)
```
