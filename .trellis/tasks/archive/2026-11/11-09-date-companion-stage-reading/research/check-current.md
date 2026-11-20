# D4 独立检查：08/09 检查点

2026-11-18，`/root/d4_check_short`。本轮为主会话授权的只读检查；仅写本记录，没有修改产品、JVM、AndroidTest、父任务、spec 或版本文件，也没有运行 Gradle、formatter、设备、收费模型或 TTS。

**结论：本次只读审查完成，D4 尚未通过最终验收。** 三项确认问题和一项原生证据缺口已发给主会话；实施者正在整组修复，解冻后的源码不能沿用本检查点的通过结论。

## 审查来源与范围

- 已先读取截断 hook 的完整保存文件，以及 `trellis-check/SKILL.md`。PRD/design/implement 和完整 14 路径清单均按 D4 实际符号、相关区段加载；清单见 [check-full-context-continue-2026-11-18.jsonl](check-full-context-continue-2026-11-18.jsonl)。没有把旧 v2.4、D1–D3 或整个 Git 工作区差异当作 D4 作者范围。
- 审查基准为 [continue baseline](continue-2026-11-18/baseline/source-hashes.json) 的 1425 项；产品检查点为 [08 source-after](resume-2026-11-18/d4-native-engineering-08/source-after.json) 的 1449 项。两者差异为 **24 个新增文件、10 个改动文件**。
- 08 清单文件 SHA-256：`1f52204d3ca196fafda1061788e94ec7124a2975a16548f0eac15f36f6a6a632`。06 与 08 的差异仅为 `CompanionReadingScreenTest.kt` 的 AndroidTest 修复，产品和 JVM 源码一致。
- 独立扫描 281 个保护项：280 项字节哈希未变，无缺失；唯一差异为已授权的 `RoleplayScreen.kt`。其三个必要适配逆移除后的原始 SHA 一致证明由主会话持有。本检查没有改 schema55、旧 PNG、inventory、版本或更新 JSON。
- 07:58:34 UTC 的读取检查中，D4 源码相对 08 仅 `CompanionD4UiFixture.kt` 已被原作者更新；后续修复可能继续变化。以下源代码定位主要绑定 08，而不是把变化中的工作树称为最终已验版本。

关键审查文件的 08 SHA-256，路径相对 `app/src/main/java/com/example/myapplication/`：

| 文件 | SHA-256 |
| --- | --- |
| `roleplay/companion/CompanionBeatPlayer.kt` | `0b2747382a6c79ba6245ad623138a9d328383c296b8364d02026a8d0898f7171` |
| `data/repository/roleplay/RoomCompanionReadingRepository.kt` | `a8e507554b689de85f7e04f9eeab9afb487ef175229bbe0974eb695758f5e4f2` |
| `data/repository/roleplay/AndroidCompanionResourcesRepository.kt` | `3403c839793b0b7b5e4954dadb1cc984569025849953b50a8ded7e02c516c38f` |
| `viewmodel/CompanionDisplayViewModel.kt` | `77aa9377345f4071670d1903e9041f5643536402575185d6d7a0cdb92673d858` |
| `ui/screen/roleplay/CompanionStage.kt` | `e21ec6e057bb7c7d529a632298768a70aecbc5c85ed7a1c704a10a02a2d26851` |
| `ui/screen/roleplay/CompanionReadingScreen.kt` | `d1866a7125b5a53fe8735dad332b4ca0f11ffa676cab10829f75174bae4aaf01` |
| `ui/navigation/CompanionPresentationRoutes.kt` | `60d68721103e880db822da9db7dbf6adb0bdd41cd1f44a9d2c37769a155c7c97` |

## Findings (fixed)

本检查点没有自修源码：主会话保持独占构建/设备，产品、JVM 和设备测试分别由已指定的原作者串行收口。没有把已报告建议记为已修复。

## Findings (not fixed)

### F1：连续阅读的候选绑定缺少用途过滤

- File：`data/repository/roleplay/RoomCompanionReadingRepository.kt:89`–95，`data/local/chat/ConversationDao.kt:32`–55。
- Issue：`allowed()` 只读取当前绑定的 `purpose` 判断是否连续；当前为 CONTINUOUS 时，候选绑定只核对同空间、角色和 persona，未核对候选自身用途。后续 SQL 也没有 purpose 条件。
- 可复现条件：连续入口的空间中存在同角色、同 persona 的 INDEPENDENT 或 LEGACY_COMPAT 绑定及合法可读来源时，该会话会被纳入跨会话阅读。独立/legacy 入口限制本 conversation/run 的现有分支本身正确。
- 建议修复与验证：连续用途只纳入同为连续用途的候选，在真实 Room 用同空间混入不同用途的记录核对授权和分页结果；不得靠 UI 隐藏或扩大模型 history scope 解决。已报告 root，未在冻结/交叉写入期间改源。

### F2：取消复制时，新导入路径可能未交给清理所有者

- File：`data/repository/roleplay/AndroidCompanionResourcesRepository.kt:41`，依赖 `data/repository/LocalImageStore.kt:20`–37。
- Issue：复制调用在外层 try 之前。`LocalImageStore` 在 IO `withContext` 中拥有实际 target，但复制完成后若调用方取消抢在返回，其路径会被 prompt cancellation 拒绝交给外层；外层的取消清理和 `unpublished` 登记均无法执行。
- 可复现条件：导入复制期间取消/放弃草稿，输入流已经写完私有 target，取消发生在 IO 结果回到调用方之前。文件存在但不在 `unpublished` 或 VM imported 集合中。
- 建议修复与验证：在实际拥有 target 的复制入口处理取消清理，或提供受控的 D4 复制入口；用实际 ContentResolver 流控制写完后的取消时机，断言私有目录没有本次孤立新文件，同时保留其它已保存文件。已报告 root，待原作者处理必要共享适配。

### F3：高版本原配置中的未知图片引用没有进入物理删除保护

- File：`data/repository/roleplay/AndroidCompanionResourcesRepository.kt:183`，`data/local/CompanionVisualCodec.kt:16`–29，`di/AppGraph.kt:679`。
- Issue：完整引用扫描实际只遍历 typed `settingsFlow`；高视觉版本仍保存的原 JSON 中，当前不认识的资源 kind/结构或扩展引用被 codec 投影丢弃。已保存图的旧场景删除清理因此可能把该文件判断为无人引用。
- 可复现条件：场景 A 的旧单图指向私有文件 p；角色 B 的高版本视觉原 JSON 通过当前不识别的资源结构继续持有 p。删除 A 后，`RoomRoleplayRepository.deleteScenario()` 在删除事务完成后调用新 imageFileCleaner，typed 引用集合缺少 p，文件可能被删除。
- 建议修复与验证：读取不损失未知字段的原引用，或在未知设置/视觉版本下保守停用已发表文件的物理清理。真实文件回归须验证高版本原 JSON、文件和已知共享引用仍保留；不能只检查字符串或保留 JSON 而删除实际图。未发表、本实例新导入的垃圾清理应继续受明确所有权限制。已报告 root。

### V1：原主屏窄屏大字 Stage 与输入/IME 组合证据未覆盖

- File：08/09 的 `CompanionStageScreenTest.narrowPlatformFont200_longBeatScrollsToItsEndWithReachableControls` 与 `roleplayScreen_inputDisplayAndOldQuickActions_neverSendOrRetry`。
- Issue：前者的 320dp/200% 平台宿主只渲染 `CompanionStage`；后者实际渲染原 `RoleplayScreen`、slot、输入和 IME，但为普通窗口。不能从两个分开的场景推定窄屏大字下主屏组合可用。
- 建议验证：补真实 `RoleplayScreen` 的 320dp/200% Stage + 输入/IME 组合，量到正文至少完整一行，输入、补全和关键动作实际可达且互不遮挡。此项是 AC5 证据缺口，尚未被本检查定为产品行为失败。已报告 root。

## 已核对的实际合同

- Player：单一 typingJob 和 generation；源/原文/beat 集合变化失效，补全先取消旧 timer，下一动作才推进；stop 可重新进入，close/父 scope 取消清空且拒绝迟到结果。JVM 覆盖 NonCancellable、同步观察者重入、立即时钟、中文/组合字符/emoji/RI/GB9c。09 原生方法也实际核对中文和 emoji 前缀，不是仅 JVM 文件存在。
- 资源：角色默认与本场覆盖分开；目标表情→套默认→旧本地/远程→头像→姓名，PORTRAIT/BACKGROUND/DECOR 分用途；运行时加载失败继续候选。导入及 save 有实际图片 bounds/采样解码和 expectedRevision CAS；移除已保存素材只是解除引用。上述 F2/F3 是该链路仍需修复的边界。
- Reader：独立 `CompanionReadingScope` 不继承模型授权；新目的地不 `enterScenario`。SQL 在 keyset 前筛角色/persona/空间/绑定和实际 selected COMMITTED turn，Room domain proof 再核完整关联资格。UTF-8 字节比较与默认 SQLite BINARY 排序一致；before/from 保存值，不查游标行；刷新保留已展开窗口。独立/legacy 的本会话/运行分支已核对，连续候选用途问题见 F1。
- 选区：来自同源 D3 reply/beat，保存原 UTF-16 span、rawText、角色与修订；旁白和跨回复可进入稳定 SpeechUnit 范围，改源撤销失效选区；没有引入声音网络或 TTS。
- 原 UI：Stage 只替换消息视口；原输入、附件、建议、取消、旧快捷和消息回调继续在原面板。默认-null 路径保留旧列表及群聊/长文入口，Stage 不使用性能回放夹具。低层气泡/阅读段落消费既有皮肤、主题和用户几何，没有另写主题或改 13 个皮肤枚举。
- 生命周期/覆盖层：Stage timer 挂 RESUMED owner；源变更/离场取消。HUD 按历史 snapshot label，gate 取来源角色当前偏好；Dropdown/Dialog 各自处理返回，Reader 的起点/选区/宿主按层消费。真实同指针 longpress 后拖动取消在 09 的 Stage/Reader 方法中已运行通过。

## Verification

本代理没有重复运行构建。已独立读取原始 Gradle 日志、XML、JSON 清单和真实候选，而不是只引用交接口述。

| 验证 | 检查点结果与限制 |
| --- | --- |
| Lint | **08 pass**：原始 `gradle.log` 有实际 `:app:lintDebug` 和 BUILD SUCCESSFUL，exit=0、1449 源前后一致。解冻后的新源码未复验。 |
| TypeCheck | **产品/JVM 06 pass，AndroidTest 08 pass**。06 整条命令曾因 Reader AndroidTest import/派生解析错误 exit=1；这不是整个工程通过。08 修复后 AndroidTest、Debug 和 AndroidTest 双 APK 成功。 |
| Player JVM | **27/27 pass**：独立解析 `d4-player-03/test-results` 原 XML，0 failure/error/skipped。 |
| 组合 JVM | **48/48 pass**：06 原日志实际执行 `testDebugUnitTest`，XML 为 Player27 + VisualStore10 + 旧 Writing11。与 Player03 重叠，不累计为75。 |
| 09 原生 | **21/27 pass，6 fail**，3 类27个唯一方法全部结束、无 skip、runner完成；不是0结果或编译通过代替交互。 |
| 完整 Room/资源文件/VM JVM | **本检查点尚无完整实际结果**；原作者正在补本组。121+ keyset、用途混入、删除游标、共享/取消实际文件、异步 owner 回填必须绑定之后真实报告。 |
| 视觉 | 已目视09的 Stage/Reader/素材末项及真实丢弃 Dialog 四张候选，并核对相关平台 density/TextLayout 元数据；候选并未人工接纳。旧 E04/E05/手机入口受影响截图 verify 尚需主会话按实际改动执行，旧 PNG 不更新。 |

09 的六项失败原始记录见 [device-summary](resume-2026-11-18/d4-device-09/device-summary.json)：

- Stage、Reader、Visuals 三个 narrow 方法在共享 fixture 复原 softInput 时为 expected 0 / actual 16。
- Reader 删除首可见记录的像素偏移为 expected 0 / actual 65。
- Stage 坏表情→实际默认图回退超时。
- 原 RoleplayScreen 输入/显示/旧快捷方法发生文本项不可见断言失败；后续动作须在修复后完整跑完。

主会话已交原作者修复这些失败，以及 08 的 Reader 背景消费、Display 角色删除撤销 localMode、Stage 不支持版本守卫。本只读记录不推定它们已修复或复验通过。

## 最终复核与规范同步

原作者释放后，对本整组修复的新源码清单复核 F1–F3/V1 和已知失败，相关 lint/type-check/JVM/原生结果须与新 SHA 对应；不得把08/09的结果继续覆盖新源码。主会话拥有父设计、spec 和版本记录写入：需同步独立阅读用途/UTF-8 keyset/锚点、视觉稳定引用与 CAS/清理、正式 Stage/source 生命周期和 D5 纯 SpeechUnit 接口。版本仍归2.4.1，schema55、原图与基准接纳规则保持。本检查不代表父任务、D4 AC 或整轮2.4.1交付完成。
