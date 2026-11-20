# D4 本地 BeatPlayer 交接

2026-11-18，`d4_player` 实施者完成生产源码与必要测试并冻结释放。版本归属
v2.4.1。主会话首次实际执行 26 方法：25 通过、1 个 Indic 字符边界失败；
GB9c 修复与第 27 个回归方法已补齐，**修复后尚待主会话复验，不表示 D4 验收通过**。

## 文件与所有权

- `app/src/main/java/com/example/myapplication/roleplay/companion/CompanionBeatPlayer.kt`
- `app/src/test/java/com/example/myapplication/roleplay/companion/CompanionBeatPlayerTest.kt`
- 本交接文件。

未修改共享 model、codec、DAO、UI、ViewModel、旧 schema、版本或更新清单；没有
提交、推送、发布。主会话继续唯一 Gradle、设备和 formatter 执行者。

## 公共 API

```kotlin
class CompanionBeatPlayer(
    scope: CoroutineScope,
    characterDelayMillis: Long = 35L,
    delayCharacter: suspend (Long) -> Unit = { delay(it) },
) : AutoCloseable {
    val state: StateFlow<CompanionBeatPlayerState>
    fun setSource(reply: ParsedCompanionReply?)
    fun advanceOrReveal()
    fun replay()
    fun stop()
    override fun close()
}
```

间隔必须大于零；无动画测试可注入立即返回的时钟。公开类型均在
`roleplay.companion`，不反向依赖 `ui`。

`CompanionBeatPlayerState` 含 `phase`、`source`、`beat`、`beatIndex`、`beatCount`、
`visibleText`、`revealedGraphemes`、`graphemeCount`、`generation`。无拍的 index 为
-1；index/count 是当前投影内的拍位置，不是消息或请求顺序。

| 输入/动作 | 实际行为 |
| --- | --- |
| 初始 / `setSource(null)` | IDLE，无源、拍或可见正文 |
| 新非空投影 | 固定源/拍集合，从首拍 TYPING 开始；第一段完整 grapheme 在首个 tick 后出现 |
| 空 beats 投影 | 保留 source 的 IDLE，不安装 timer，不形成正式播放队列 |
| 空文本拍 | 本拍立即 COMPLETE，不跳过拍身份 |
| TYPING 点击 | 取消旧 timer 并增加代次，仅补全本拍到 COMPLETE |
| COMPLETE 点击 | 下一拍；末拍之后进入 END，保留末拍全文 |
| END 点击 / 显式 `replay()` | 只重播本地固定投影，从首拍重新打字 |
| `stop()` | 取消并清除源回 IDLE；之后可对同一源再次 `setSource` |
| `close()` / 父 scope 取消 | 永久释放并清除；晚到设置/点击不能复活，也不取消传入的宿主 scope |

切列表/阅读等临时离开用 `stop`；owner 销毁用 `close`。调用方提供已完成的
D3 投影，并保留原生成/存储资格守卫；播放器不创建来源证明、请求或消息。

## 固定来源、计时与文字

- 比较整个 `CompanionReplySource`，包含消息/会话/回合/角色、revision、
  protocol/parser、rawHash、attempt 与 configurationRevision。同 source、raw、beats
  的重复设置为 no-op，避免重组重启；raw 或 beats 实际变化也重置，不信任陈旧标识。
- 拍集合复制为本次快照；播放器不改 raw、span、source 或拍内容。HUD/观测由宿主
  独立消费当前 D3 投影，不进入播放器权威。
- 单一 `typingJob` 与单调 generation 在同一锁内变更。安装 LAZY job 后再启动，
  发布前同时核对协程取消、代次、完整源、拍和 TYPING。同步 StateFlow 观察者若
  重入换源，也不能把旧时钟安装到新代次。
- Java `BreakIterator(Locale.ROOT)` 提供文字/组合字基础边界，显式补齐 emoji
  modifier、variation/tag、ZWJ pictograph、RI 成对、Prepend、CRLF/control 与
  Unicode 15.1 GB9c Indic 连写规则。
  每次可见前缀都按 UTF-16 grapheme 末端截取，不逐 Char 切代理对。
- 注入时钟取消不会误报完成或推进；正常动画时钟异常仅展示完整本拍作为本地降级。
  旧时钟异常、finally 或不可取消的迟到返回均不能写新状态。

本文件没有定义 `SpeechUnit`。D5/宿主可用 `state.source + state.beat` 绑定稳定
来源、拍、原文 span、种类、sprite 与独立 voiceEmotion；声音网络和播放使用自己的
生命周期代次。播放器 generation 仅表示显示计时代次。

## 验证状态与最小 selector

静态核对已完成：只新增上述范围文件；生产文件没有 Repository、模型、网络、UI
导入、墙钟或真实 sleep。当前测试为 **27 个 JUnit4 `@Test` 方法**，另在来源变更测试中
逐项覆盖全部 10 个 source 字段，不将这些表内案例虚报为独立方法。

必要行为包括补全后下一点才推进、同源无重复 timer、全源变更、可变输入集合冻结、
空拍/END/循环、stop/close/父生命周期、不可取消旧 tick、旧时钟异常、立即时钟、
同步观察者换源，以及中文/音标/ZWJ 家庭/肤色/旗帜/标签旗/组合脚本逐前缀检查。
全部使用 `runTest` 调度器或注入门控，不 sleep 真实时间。

由主会话执行：

```powershell
./gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.roleplay.companion.CompanionBeatPlayerTest" --console=plain
```

本实施者未运行 Gradle、设备或 formatter。27 方法的实际结果由主会话按原始 XML
核对；Java/Android 字符边界差异与真实 Stage 触控/菜单/滚动/输入隔离需对应 UI/设备
验收，纯播放器测试不替代零模型请求计数、实机字符显示或整个 D4 AC。

## 首轮真实失败与 GB9c 修复

主会话 `d4-core-02` 已通过产品/JVM 编译，1437 源码前后一致。播放器原始
[XML](resume-2026-11-18/d4-core-02/test-results/35f96354b67a8298.xml) 记录 26 方法，
25 通过、1 失败、0 错误/跳过。唯一失败为
`typing_preservesCombiningScriptsAndControlBoundaries`：首组应为 7 grapheme，实际 8；
后续 leading marks/Prepend 组因首断言失败尚未执行。

最小 JDK 21.0.9 诊断确认 `क्ष`（0915、094D、0937）的基础末端为 `[2, 3]`；
accent、CRLF、Hangul Jamo、`का`、control 与第二组分别保持预期基础边界。根因是
宿主 `BreakIterator` 尚未实施 Unicode 15.1 GB9c，不是原测试期望错误。

修复保留原 7/3 组预期和所有旧输入，按准确 InCB=Consonant/Linker/Extend 匹配
`Consonant (Extend|Linker)* Linker (Extend|Linker)* × Consonant`。使用
[Unicode 15.1 官方属性表](https://www.unicode.org/Public/15.1.0/ucd/DerivedCoreProperties.txt)
固定数据，不把所有 GCB Extend 直接当 InCB Extend；ZWNJ、CGJ、元音符、variation
selector 等会终止连写匹配。属性原始字节 SHA-256 为
`f55d0db69123431a7317868725b1fcbf1eab6b265d756d1bd7f0f6d9f9ee108b`。

新增 `typing_keepsIndicConjunctsWholeWithoutJoiningUnrelatedText`，通过真实播放器
逐前缀验证六个受支持脚本、组合符/ZWJ/多 linker/多辅音链，以及无 linker、独立元音、
Latin、leading linker、ZWNJ/CGJ/元音符/variation 与 control 的不误合并行为。
公共 API、源身份、timer 与生命周期逻辑均未改变。

原失败最小复验 selector：

```text
com.example.myapplication.roleplay.companion.CompanionBeatPlayerTest.typing_preservesCombiningScriptsAndControlBoundaries
```

新回归 selector 为同类 `.typing_keepsIndicConjunctsWholeWithoutJoiningUnrelatedText`；
字符核心改变后主会话可定向运行全类 27 方法，按方法去重，不把首轮 26 累加为 53。
修复源码与新用例在本实施者交回时尚未进行 Gradle/设备复验。
