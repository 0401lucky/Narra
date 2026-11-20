# D4 Reader 删除锚点恢复修复（恢复会话 01a1ee8a）

本实现者为 `/root/d4_reader_repair`，只修改 Reader Screen、对应 AndroidTest 和本记录。ViewModel、仓储、共享夹具、其它 UI、父任务、spec、版本、schema 与 PNG 均未修改。主会话继续独占 Gradle、设备和 IDE formatter。

## 原始失败与代码原因

`d4-recovered-device-14` 实际执行 30 项，26 通过、4 失败；本块负责 `deletedFirstVisibleRecord_continuesAtItsSurvivingNeighbourWithoutJumpingToBottom` 的 5000ms 等待失败，原记录保留。

原恢复链路存在两个状态传播问题：`restoring` 是普通 Boolean，恢复期间跳过的 snapshot 捕获不能订阅恢复结束；items/字体/IME effect 在 `restore()` 因正在滚动或其它恢复提前返回后仍清除 `reconcilePending`。独立 layout observer 也可在窗口协调期间竞争调用恢复。

另一个边界是 LazyColumn 的 `visibleItemsInfo` 包含 contentPadding 区预布置项或仅触边项。阅读锚点与实际探针原先都无条件取第一条已知记录；正文起点为零偏移时，这些项不应成为新的阅读来源。

以上为本轮源码核对确定的边界；删除方法是否已全部修复仍须绑定修改后真实设备报告，不能沿用第 14 轮结果或宣称已验收。

## 当前修复

- `CompanionReadingScreen.kt`：恢复状态改为可观察状态，并纳入 snapshotFlow 的捕获门。恢复完成重新开放捕获；窗口协调先等实际滚动与恢复结束，再恢复并清门。layout observer 在窗口协调期间不启动另一恢复。
- 首条锚点只从正文视口中具有正面积的 item 选择，排除 padding 区/仅触边项；保留原 source/item/cursor/UTF-16 起点和像素 offset 合同。
- `CompanionReadingScreenTest.kt`：实际探针采用相同的正文视口范围，仍同时核对真实 layout 与回调身份/像素值。删除后的下一幸存来源、对应 item、offset=0、非跟底与 39 条窗口强断言全部保留。
- 删除用例继续实际触控滚动，验证恢复后仍捕获用户移动，再插入一条新记录验证保留该新锚点。方法数仍为 10，没有删除或放宽原场景。
- 原 5000ms 超时保持；若仍失败，错误增加实际锚点、viewportStart/End 与 visible item key/offset/size，便于从真实布局定位。

## 验证与交接

本实现者只作定向源码核对，未运行 Gradle、formatter 或设备，因此不记编译/测试通过。以下跟进修复完成后，Reader 两个源文件再次冻结，交主会话统一格式化、编译和设备复验。

最小行为复验为现有 Reader 10 项（或与 Stage/Visuals 合并跑原 30 项）：删除邻居/恢复后滚动、前插/新答、字体/行高/IME、实际图片解码重排与跟底。源码若再次修改，必须重绑定最终 source-after 与设备结果。若此次修改后的失败诊断显示其它实际偏移/所有权问题，应继续修复，不能增大超时或降低 offset 断言。

## 独立检查后的跟进：拖动中更新窗口

独立 check 确认首版修复仍有问题：窗口更新发生在用户持续拖动/惯性期间，先关捕获并等滚动停止会保留更新瞬间的锚点；之后恢复会撤销用户后续移动。主会话解除同一文件范围冻结，要求修复该确定边界。

当前实现记录真实 `NestedScrollSource.UserInput` 的滚动代次。窗口协调先等待实际 layout 的 item 总数和各 visible key/index 对应当前新窗口，旧布局不进入新来源锚点。更新时正在用户滚动，或等待新布局期间发生新的用户移动时，保留实际位置，直接结束窗口协调并重新开放当前窗口捕获；只对静止且无新用户输入的窗口变化恢复旧锚点。layout observer 不在滚动刚结束时重做旧位置恢复。协调 effect 同时按 viewport owner 键控，防止不同范围复用相同 items 时遗留旧协调门。

新增 `onePointerDrag_windowUpdateAndContinuedDrag_keepTheLastUserPositionAfterRelease`：真实同一 pointer 的 down→move→前插20条→继续move→停住/up；中途明确核对 `isScrollInProgress`，没有第二次 down。松手前后核对真实 layout 与回调 source/item/offset，并断言没有回到更新瞬间的旧锚点；80条窗口和零读取/刷新/离场回调保持。测试 finally 负责释放仍按住的指针。

Reader 当前 **11 方法**，与原 Stage/Visuals 合计至少31项（若其它作者增加方法，以主会话 APK 枚举为准）。原删除 offset=0、前插/新答、字体/IME/图片与跟底断言均保留。跟进尚未编译或实测，不能记为通过；最小复验改为 Reader11 或最终合并设备集。
