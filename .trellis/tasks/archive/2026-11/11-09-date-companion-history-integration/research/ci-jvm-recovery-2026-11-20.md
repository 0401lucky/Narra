# 2.4.1-dev CI：陪伴入口与待存恢复的空闲竞争

2026-11-20。单条确定性回归已证明：旧 ViewModel 发布 `isBusy=false` 时仍持有活跃的
旧操作，通知中的下一次真实开场选择被挡掉。现已只修正操作登记、重入 guard 和
所有权/空闲发布顺序，生产与测试源码冻结，等待主会话执行 42 轮全部 22 方法。

## 失败与对照证据

- [Dev Release 44734464478](https://github.com/0401lucky/Narra/actions/runs/44734464478)
  的签名 assemble 通过，完整 2956 JVM 有 12 失败：`CompanionEntryViewModelTest`
  7 项、`CompanionPendingRecoveryTest` 5 项。原始失败行在
  `.tmp/narra-release-241/ci-44734464478/failed-jobs.txt`，不是编译失败或测试零执行。
- 两类测试的 `settle()` 仅等待 `uiState.first { !isBusy }`。待存测试中 4 项停在
  `openDirect()` 的 ACTIVE 断言；来源变更项缺少后续 attempt；开场取消项未收到
  预期 HTTP。失败位置与真实动作被 operation guard 拦截相符。
- `RoleplayCompanionViewModel`、两类测试和 `CompanionEntryTestFixture` 的原字节
  均与 delivery manifest 一致；13 完整轮中这两类合计 21 方法曾全部通过。
- [40 本地复现](validation/40-release-jvm-reproduction/validation.json)：2.4.1 参数，
  21/21 pass，305.034 秒，`sourceStable=true`。Windows 本轮通过，不能用这个结果
  覆盖 Ubuntu 的实际失败，也没有版本参数导致开场判断变化的证据。
- [41 确定性 red](validation/41-idle-race-red/validation.json)：1/1 failed，121.708 秒，
  `sourceStable=true`，旧生产仍保持原 SHA。原
  [XML](validation/41-idle-race-red/jvm-xml/dea0963a824e60cb.xml)
  明确为 `expected:<ACTIVE> but was:<PREPARE>`，不是超时、初始化错误或任意异常。

## Findings (fixed)

### RoleplayCompanionViewModel.kt

Issue：旧 `runOperation` 在 `finally` 中先发出 `isBusy=false`，再清 `operation`。
StateFlow 收集者此时可以调用下一动作，但旧 Job 仍活跃，`operation?.isActive`
使真实动作直接返回。原 `operation = launch { ... }` 也把赋值放在协程执行之后；
同步完成或收集者立即启动下一操作时，旧 launch 返回值可能覆盖新操作句柄。
`generation` 只代表导航代次，不能单独证明该 Job 仍拥有同路由的操作槽。

Fix：

- 使用 `CoroutineStart.LAZY` 创建 Job，在启动前先登记 `operation`，再发布
  `isBusy=true` 并 `start()`；启动返回后不再写 operation。
- `runOperation` 与 `refresh()` 都以 `operation != null` 防重入，已登记但尚未
  start 的 Job 同样占有操作槽，不能通过 refresh 取消并替换准备中的操作。
- catch/finally 同时比较导航 `generation` 和实际当前 Job 身份。
- finally 先清自己的 `operation`，再发出 `isBusy=false`。空闲通知中可以立即
  登记新操作；旧 finally 与旧 start 返回之后均没有再清理/回写新槽的动作。
- 保留 `cancel()` 的原导航代次提升、取消、状态恢复语义以及来源/HTTP/持久恢复
  流程；未新增模型请求、清理旧原文或改动开场/记忆归属。

### CompanionEntryViewModelTest.kt

Issue：原有测试可受宿主线程调度影响，Windows 的正常通过不能确定性覆盖该空闲窗口。

Fix：只新增
`idleNotification_allowsImmediateOpeningSelection`。先 UNDISPATCHED 安装 StateFlow
订阅，测试观察者 dispatcher 直接执行收到的通知，在真实 PREPARE/非忙态通知内
调用 `chooseOpening(DIRECT)`，随后核对 ACTIVE、实际 Room 场次 ACTIVE、无 turn、
无消息与零 HTTP。41 在旧代码上已经取得对应确定性红灯。

原 21 方法、`settle`、请求计数、旧/新来源、补存、取消和原场/新场恢复断言全部
保留；没有私有 Job/反射、额外 sleep、删除断言、假业务动作或更换模型 API。
`CompanionPendingRecoveryTest` 和 `CompanionEntryTestFixture` 未改。

## 写入范围与冻结绑定

本代理仅写以下两个源码文件与本报告。主会话独占 Gradle、formatter、Git、设备、
截图和外部发布；各次写入均在明确解除源码冻结/交接所有权后进行，完成后重新冻结。
未改旧 `delivery-source.json`、旧轮次 XML 或既有验收结果。

| 文件 | 修正后 SHA-256 |
| --- | --- |
| RoleplayCompanionViewModel.kt | b0184b5028b2230fa5eccea47955b533e90bcb617d045e447baebdd8201c49b8 |
| CompanionEntryViewModelTest.kt | 2f5f067ceb1f65c53e3a53dbf0853e5ba3ef15b930a5655175f5ea8a29e65d48 |
| CompanionPendingRecoveryTest.kt（未改） | f7f863b7cbeb1aa63f0c19f2031948a0c2f530b7832b85b854bc9f0a3ab0b249 |
| CompanionEntryTestFixture.kt（未改） | 172a1bc9f7bee0cf439a3ecfe44fc6d9672c3f1fa6c76de9f204464d5cbb6fd2 |

将新测试/4 个 import 逆向移除，原 Entry 源 SHA 精确恢复为
`734cc9be039f2264ebd951b2ee032ff637cab39b1387c396dc9d96747358231f`；
将获准的生产 imports/runOperation/refresh guard 逆向恢复，生产 SHA 精确恢复为
`f5fb7e1b582cdd7ef540299ff463ffc1862363bdb8dd440b528057df7ec0909a`。
两份逆向核对均成功，证明没有覆盖其它原方法、取消/导航代码或无关修改。

## Findings (not fixed)

暂无新增源码问题。修后运行门禁仍待主会话实际执行，不能把静态检查或 41 红灯
表述成修后通过。建议主会话将“先释放操作槽再发布空闲、先登记再启动、按实际 Job
和导航代次保护收尾”同步到 ViewModel 规范，保留本次确定性 red/green 的证据索引。

## Verification

- Red：41 已验证 1/1 failed，ACTIVE/PREPARE 对照及旧生产 SHA 均明确。
- 原两类：40 已验证 21/21 pass，2.4.1 参数、sourceStable。
- 修后 Tests：待主会话 42 轮两类全部 22 方法；本代理未运行 Gradle。
- 修后 TypeCheck / Lint：待主会话结果，未沿用旧源码的通过状态。
- 静态范围：逆向恢复 SHA、原 21 方法/settle 和其它两文件保持检查通过。
- 本报告只处理这 12 项 JVM 失败；其它 CI 包装层/截图诊断由主会话负责，其结果
  继续单列，不推断 OOM 或提前接纳视觉基准。
