# D1 独立检查与设备交接

主会话补充（2026-11-10）：fixture修复后的4项设备复验已全部通过；连同首轮29项未受影响用例，33项唯一设备用例完成验收。冻结源码与APK已核对，详见 [设备最终结果](device-final-validation.json) 与 [D2交接](handoff.md)。下文保留检查者当时的阶段记录。

2026-11-10，检查者 `d1_resume_check`。**D1 代码、JVM、编译与 Lint 检查通过；设备首轮 29/33 通过，4 项 fixture 构造错误已修，等待主会话复验。** 本文不代表 D2–D6 或整个 2.4.1 完成。

## 范围与基线

- 以父任务 `research/execution-baseline.json` 和作者 `implementation-files.json` 界定 D1 的 51 个文件，不以整个工作区相对 HEAD 的差异代替本次范围。
- 接手时 51 个 D1 文件、1367 项最终源码哈希与作者证据一致。逐项复核场次/回合事务、来源与摘要消费者、三协议实际调用、模型/JSON 兼容、迁移与 S3 配套。
- 独立检查修复 6 个源码/测试文件，设备反馈另修 1 个 androidTest fixture，合计 7 个，清单见 [check-final-files.json](check-final-files.json)。273 项受保护旧 schema、截图和参考资料均保持原始字节；未提交、推送、发布或操作真实用户库。
- 已读取并核对主会话新增的 `companion-foundation-guidelines.md`。规范、版本文档和任务协调记录由主会话维护。

## 已修复发现

1. **并发请求没有预留恢复容量。** `CompanionTurnCoordinator.generate` 原来只检查已收到的缓存，9 个同时在途请求均可开始并在落盘失败后突破 8 份上限。现在在 claim 前原子预留槽位，在途与待补存合计最多 8 个；claim 失败/取消释放占位，成功补存或明确丢弃释放结果槽。真实 Room 回归同时验证第 9 个请求零模型调用、首次写失败仍有 8 份结果，以及补存后可继续请求。
2. **续租失败只停止心跳。** 原 collector 继续运行。现在取消整个收集作用域、传播取消并保存可用的取消证据；旧 canonical 不受影响。`lostLease_cancelsCollectorAndDoesNotPublish` 验证流 finally 已执行、没有产物/来源发布。
3. **旧 attempt 返回了后来版本的回执。** `RoomCompanionRepository.commitAttempt` 对 COMMITTED 的短路原来直接读取当前 turn/答复。现在校验 selected attempt 和原提交 mutation revision；重生成、编辑或删除后的旧请求返回 TURN_CHANGED。未变更的重复提交仍返回相同回执、`alreadyCommitted = true`，不重复 posthook；不额外保留已删除正文。
4. **selected attempt 只有非空标识，没有实际关联证明。** `CompanionSourceStorage` 现在核对记录存在、turn 归属、COMMITTED 和冻结 conversation/scenario/run/space/character/persona/generation。`MemoryRepository` 的摘要读取接入同一校验，CONTROL 不得靠 ready 标签变成普通输入。缺失、跨 turn、未提交或错 run 的图在原文机器资格、语义投影、手动来源、持久来源段和摘要提交均被拒绝；用户原文保留。
5. **编辑未提交 INPUT 后无法沿原 turn 重试。** `onExternalChange` 原来清空唯一恢复指针。现在只为未提交 turn 保留已失效 attempt 的指针，旧 state/claim/CAS 仍被作废；明确创建新 attempt 时重冻最新输入/来源修订。文件型 Room 验证未请求/已请求两种情况、关闭重开、旧 owner/产物拒绝、原输入 ID/顺序不变，以及删除输入后不能恢复。
6. **固定旧库 fixture 假设 notNull 总是存在。** 首轮设备运行的 4 个 `CompanionFoundationMigrationTest` 均在 `legacyRow` 构造旧库数据时 NPE，尚未到迁移断言。沿用其它四个已有迁移 helper 的 `field.get("notNull")?.asBoolean != true`，使省略 false 的可空列仍写入 null。只改一个条件，没有改旧 schema、跳过列或弱化断言。616 个字段的元数据与真实 SQLite DDL 全部一致，其中 19 个省略 notNull 的字段均可空；编译/Lint 通过，4 项设备复验待主会话。

另补充 `claimWriteFailure_neverCallsModelOrConsumesRecoveryCapacity`：用 SQLite 触发器使 REQUESTED 写入连续失败，验证零模型调用、事务回滚和恢复容量不泄漏。

## 验证证据

| 检查 | 真实结果与记录 |
| --- | --- |
| 首轮缺陷复现 | `check-regression-before-01.log` 与同名 results：4 项全部在原实现失败。 |
| pending 编辑复现 | `check-regression-before-02.log`：2 项中 1 项失败；claim 写入失败用例通过，pending 恢复断言失败。 |
| 联合 JVM | `check-jvm-01.log` / `check-jvm-01-results.json`：133 类、1182 项，零失败/错误/跳过，测试期间源码未变。 |
| 最后定向 JVM | `check-jvm-02.log` / `check-jvm-02-results.json`：6 类、86 项，零失败/错误/跳过，测试期间源码未变。 |
| 去重覆盖 | 两轮按 class + testcase name 合并为 **1184 项、133 类**，不是一次全量运行，也不累加重复用例。三协议证据类实际执行 21 个 JUnit 方法，内部循环次数不计作额外测试。 |
| IDE 格式化 | `check-format-02.log` 6 文件、`check-format-03.log` 3 文件均成功；设备 fixture 补修由 `check-format-04.log` 确认 1 文件成功。 |
| 编译/Lint/双 APK | `check-build-lint-01.log`：exit 0，BUILD SUCCESSFUL in 5m 11s；Debug、UnitTest、AndroidTest、baseline Kotlin 编译及两份 APK 通过。 |
| Android Lint | `check-lint-results-debug.xml`：0 Error/Fatal，54 Warning、3 Hint，与作者报告计数相同。 |
| 设备 fixture 补修 | `check-device-fixture-build-01.log`：AndroidTest 编译、测试 APK、Lint exit 0，4m 21s；`check-device-fixture-lint-results-debug.xml` 仍为 54 Warning、3 Hint。`device-fixture-static-check.json` 记录 616 字段/19 项可省布尔字段的 DDL 校验，不代替设备复验。 |
| 静态/冻结 | `check-static-validation.json`：定向 diff --check 与 Kotlin/SQL 空白检查通过；构建期间 1367 项源码哈希无变化，273 保护项无变化。 |

联合回归选择了 `*Memory*Test`、`*Roleplay*Test`、`*ConversationSummary*Test`、`*Companion*Test`、网关/完成证据/工具、迁移注册与 S3 fixture、旧回合 Runner。最后一轮只复验 `*Companion*Test`、`MemoryFoundationRepositoryTest`、`RoleplayDurableMemoryFlowTest`、`ConversationMemoryExtractionCoordinatorTest`。最后改动相对联合回归仅为 `CompanionSourceStorage.onExternalChange` 的未提交恢复指针及两个测试文件，详见冻结 JSON。

最终构建命令：

```powershell
.\gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin :app:compileBaselineKotlin :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest --offline --console=plain
```

## D1 验收对应

- AC1：`twoOwners_claimExactlyOnce`、`sameText_canBeSeparateTurns`、并发容量/claim 失败用例，验证持久请求唯一性与不同逻辑输入。
- AC2：`canonicalFailure_reopensAndSavesWithoutCallingModel`、`artifactFailure_keepsMemoryCopyAndRetriesStorageOnly`、`artifactLostOnRestart_remainsUncertainNotFirstRequest`、事务失败保旧答及回执幂等用例。
- AC3：pending 的存储/手动/语义/摘要拒绝、`continue_hasNoopCoverageButNoUserFacts` 与缺图联合拒绝；复读兼容提取和 CONTROL_ONLY 摘要链路并执行对应旧回归。
- AC4：原身份资格提升、外部编辑/删除、身份变化、跨角色引用、原预审 R1–R3 及新增 pending 编辑恢复。旧预审指出的新回合淘汰旧 reroll、已接受开场离场补存、回线上历史 CRUD 均有通过的真实 Room 用例。
- AC5：三协议 HTTP/回退/工具完成证据及旧网关、RP 回归通过；设备首轮其它 29 项通过，固定 54 的 4 项在 fixture 构造阶段失败。该 fixture 已修并重新编译，**4 项迁移断言的实际设备复验仍待主会话**。

## 冻结产物与交接

权威机器证据为 [check-final-validation.json](check-final-validation.json)，源码冻结为 [check-final-source-hashes.json](check-final-source-hashes.json)。

| 产物 | SHA-256 |
| --- | --- |
| `app/build/outputs/apk/debug/Narra-v2.3.0-dev-20300-dev.apk` | `3d818deb173a8499ccb99894ba3e8e68a4560d04d31b917e3def64f364ed4c2f` |
| `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | `6dd5fe0b0ec7670523fd37eb5770791ebd97cb7542eea0c0bc212ee071527167` |
| `app/schemas/com.example.myapplication.data.local.chat.ChatDatabase/55.json` | `5b33ea781a198962487b9324f054d2a243e0903f49c65ce00bf93b853b696033` |

应用 ID 为 `com.narra.app.dev`，测试包为 `com.narra.app.dev.test`。版本参数保持现有 `2.3.0-dev / 20300`，上述仅为 2.4.1 开发验证产物，未发布。

独立检查的 Gradle daemon 46164、fixture 补修的 daemon 48896 均由日志确认当前项目；各自最终 session exit 0 后执行 `gradlew --stop`，均返回 `1 Daemon stopped`。IDE formatter 已结束，没有停止用户 Android Studio 或其它项目。**本检查者释放全部 D1 源码、测试及 Gradle/formatter 所有权。**

主会话可核对上述新测试 APK 哈希，在专用合成 AVD 复验固定 54 的迁移用例；主 APK 与首轮逐字节相同。原始 `device-01.log` 及首轮 29 过/4 失败证据保留，旧冻结 JSON 另存为 `*-before-device-fixture.json`。仅改 androidTest fixture，因此复用先前 1184 项 JVM 结果，没有重新运行或累加计数。未运行真实模型/TTS、物理设备体验或 D2 入口，不能把本检查当作这些效果已验收。代码范围未留下已确认未修复问题。

D2 接线须结合 attempt.state 判断 `activeAttemptId`：未提交输入编辑后，非空指针可能只是 CANCELLED/UNCERTAIN 的恢复入口，不代表仍在发网络请求。明确新 attempt 重冻最新输入；补保存仍不接受模型 lambda。
