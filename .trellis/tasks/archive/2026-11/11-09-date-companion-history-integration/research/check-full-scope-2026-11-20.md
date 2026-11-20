# D6 最终全范围检查

最终结论：D6（2.4.1）工程门禁通过，未发现遗留工程问题。独立核对原始 XML、instrumentation、文件字节和冻结清单：最新 JVM **2956/2956**，最新独立原生方法 **9/9**，52 行工程账本 **52/52**，Lint 与 main/JVM/AndroidTest/baseline 编译通过。**52 项截图差异仍未接纳，真实模型/MiMo/物理音效未测；本结论不代表发布或全部主观效果验收。**

2026-11-20，原生 `trellis-check /root/d6_full_scope_check`。恢复现有已批准任务，读取完整 hook 保存文件、AGENTS、check 技能、当前 check.jsonl 引用、PRD→design→implement、父设计 §9–10/52 项矩阵、D4/D5 交接及三块实施/2026-11-20 检查记录。检查覆盖整个 D6，相邻迁移、S3、生产 AppGraph/Nav/文档端口与测试均在范围内。

主会话唯一运行 Gradle、IDE formatter、截图和设备。本检查在构建期间只读；收到逐文件内部所有权交接后才修复，随即释放。最终收口只读取证据和查看既有图片，唯一写入本文；没有重跑 Gradle/设备、改工程源码、record/替换 PNG、提交、归档或发布。以下通过结论均绑定实际原始证据，测试声明数单列。

## Findings (fixed)

- File: `app/src/main/java/com/example/myapplication/viewmodel/CompanionHistoryViewModel.kt`
  - Issue: 初次打开、加载更早和已展开窗口刷新，在成功读取返回后仅检查本地 generation/角色存在，不能检测 IO 期间 origin run/persona/空间代次失效；原失败复核测试只覆盖仓储抛异常。
  - Fix: 三处最终发布前统一 `ensureOwner → requireScope → ensureOwner`，使用再确认后的 scope。失效沿现有代次撤销路径清原文、分组、草稿、选择和 SAF；普通 IO 且 scope 有效仍保窗口。`CompanionHistoryViewModelTest` 新增三条成功迟到返回回归，Fake 在合法快照生成后用 `afterPage/afterRead` 和 NonCancellable 闸模拟撤权，分别覆盖 open、older、已展开 100 条刷新。保留全部原失败/IO/CAS 断言。
- File: `app/src/main/java/com/example/myapplication/data/repository/roleplay/CompanionTextExport.kt`
  - Issue: 全量读取成功后直接写文档，授权只在整个导出结束后由 VM 复核；读取成功与外部 IO 之间撤权时仍可能写出旧原文。
  - Fix: 完整 raw/BOM 快照编码后、writer 前执行 `ensureActive → refreshScope 非空确认 → ensureActive`。新增 `CompanionTextExportTest.successfulReadThenRevokedScope_doesNotWriteItsFrozenOriginals`，成功快照后撤权时 1 次全量读、0 writer、Failed、原文保留；取消传播/关流失败/普通 IO/BOM/完整 raw 原断言保留。不宣称 SAF 与 Room 原子提交。
- File: `app/src/main/java/com/example/myapplication/viewmodel/CompanionHistoryViewModel.kt`（H3）
  - Issue: 中央 requireScope 在 suspend 授权检查迟到返回 null 时，会先 invalidateScope；调用者之后的 owner 检查不能保护已排队的新 open/query。
  - Fix: 与 Reader 一致，helper 显式接收冻结 owner，入口和 refreshScope 返回后先检查 owner，再决定返回或清窗口。全部 10 个调用传原操作 owner。Fake 在可空 scope 快照生成后添加闸，新增旧 older→新 open、旧 refresh→新 setQuery 两个 NonCancellable null 迟到回归，验证新操作完成/新窗口保留、无旧错误和 busy。原 failForScope 守卫、普通 IO 保窗不变。
- File: `app/src/test/java/com/example/myapplication/viewmodel/CompanionAppearanceTransferViewModelTest.kt`
  - Issue: 导入了不存在的 `testutil.MainDispatcherRule`，05 JVM 编译失败。
  - Fix: 删除错误 import，直接复用同 `viewmodel` 包 `ChatViewModelTest.kt` 中既有 Rule，没有另建 helper。
- File: `app/src/androidTest/java/com/example/myapplication/ui/navigation/CompanionIntegrationStoryTest.kt`、`ui/screen/roleplay/CompanionHistoryScreenTest.kt`
  - Issue: 两处不存在的 `assertDoesNotExist` 顶层 import，以及可空 registry owner 传给非空 CompositionLocal，05 AndroidTest 编译失败。
  - Fix: 只删除错误 import，保留 member 断言；fallback 使用 `checkNotNull(LocalActivityResultRegistryOwner.current)` 的真实宿主约束。受控 Registry、生产导航与所有故事断言不放宽。
- File: `app/src/test/java/com/example/myapplication/data/repository/roleplay/CompanionHistoryManagementRepositoryTest.kt`
  - Issue: 10 原 XML 中跨午夜预期 18/19，实际仅 18。D1 新 canonical 时间冻结为 `max(turn.createdAt,input.createdAt+1)`，不取迟到补存时间；原 fixture 的输入 23:59:00、turn 23:50，并没有次日原文。
  - Fix: 把 fixture clock/input 对齐 23:59:59.999，保第一场 23:50、第二场 00:02 和迟到 commit；明确核对 reply 冻结时间已经跨零点。原混合 129 条、日期 3 条、本场 2 条、18/19 两天及原场归属、全 raw/授权集合/运行不变等断言均保留。没有修改生产时间或分组。
- File: `app/src/test/java/com/example/myapplication/viewmodel/CompanionHistoryViewModelTest.kt`（10 普通 IO 回归）
  - Issue: 10 原 XML 的 303 行路径断言失败，窗口/scope 断言已通过；fixture `/data/private` 不属于既有公共脱敏器识别的真实 Android 私有路径前缀。
  - Fix: fixture 使用实际 Android `/data/user/0/...`，保有效窗口/原 scope/不 busy，禁止错误中出现任何 `/data/`，再核对确切安全中文 fallback。未改公共脱敏策略或生产窗口逻辑。
- File: `app/src/test/java/com/example/myapplication/data/repository/context/MemoryArchiveTransferCodecTest.kt`
  - Issue: 13 完整 JVM 的唯一失败来自原坏包列表首项，把已合法的 v3 当 future，旧方法在第 0 项即退出，后续坏图条件未验。
  - Fix: 仅在该既有方法把 future 3→4；明确原完整 graph 的 v3 无 companions 字段仍成功解码、保 graph、companions 为 null。原另外 14 条坏图、重复字段/trailing JSON 全保留，无新增 @Test 或生产校验放宽。此旧文件作为本轮新增修改路径纳入最终范围。

截至 H3 实际自修 8 个 Kotlin 文件；相对 05-after 逐文件 SHA 核对没有第 9 个源码变化。H3 相对 07-after 仅重新修改已交接的 VM/Fake/VMTest 三文件。10 后另修两个现有历史测试方法，新增一个独占文件；13 后修改一个既有 codec 方法，累计 10 个自修 Kotlin 路径。这两阶段生产变化为 0；13-after 仅新增 codec 测试该一个变化。定向 `git diff --check` exit 0、尾空白扫描 0。源码都已交还主会话统一 formatter/编译。

以下由原检查者或主会话完成修复，本代理核对最终实现与实际回归，不计作本代理自修路径：

- File: `data/repository/context/CompanionAppearanceTransferCodec.kt`、`ContextTransferRecoveryRepository.kt`、`viewmodel/CompanionAppearanceTransferViewModel.kt`
  - Issue: 合法 CRC 不能证明完整 PNG 像素流；源偏好/图片重复冻结会提前超限，预览与恢复的操作身份也可能串用。
  - Fix: 有界验证实际 deflate、行数/过滤字节/Adam7；源只保留单份冻结 bundle、本机目标只存 CAS，兼容旧 v1 冗余字段；预览/恢复使用原操作身份。合法图、畸形像素流、有界大图、关闭重开与旧计划回归均保留。
- File: `data/repository/context/CompanionAppearanceTransferCodec.kt` 及 transfer JVM fixtures/tests
  - Issue: 10 的七个实际失败含错误期待 DTO 来源指针为 null、未等待真实 eager Flow、非法名称/无效字符串替换负例，不能证明实际数据库或 JSON 边界。
  - Fix: 私人导出 DTO 指针为空 String，实际 Room 列为 SQL NULL；源 id 明确非空，逐 source.id 查真实 NULL/空串/唯一行。等待实际目标字段，使用合法素材名称但保私有路径/秘密哨兵，用 JSON AST 构造坏字段。仅解析边界把 IOException 包装为安全异常、保 cause；没有放宽白名单、图结构、版本或 PNG 校验。12 原 XML 25/25，最终有关方法在26再次通过。
- File: `data/repository/context/CompanionTransferFileStore.kt`、`ContextTransferRecoveryRepositoryTest.kt`、`CompanionTransferFileInstrumentedTest.kt`
  - Issue: 21 的真实 Android `Files.createLink` 被 SELinux 拒绝，冻结计划不能发布；布局变更后两条旧 JVM fixture 仍把目录当单文件。
  - Fix: 同父目录的私有临时非空目录内先完整写入、sync、hash，整体移到 `plans/{operationId}/plan.json` 或 `assets/{sha256}/image.png`，发布后复验才返回回执；竞争方不能覆盖非空固定目标。旧 `plans/{id}.json`、`assets/{hash}.png` 继续验字节后复用，坏目标不覆盖。fixture 用真实新路径/isFile，旧计划回归实际写/sync/hash旧单文件后再重开 Room/DataStore；两个 FileStore 实例竞争、阻塞目标保留、重开与旧路径均由29原生方法证明。
- File: `system/security/SensitiveTextRedactor.kt`、`SensitiveTextRedactorTest.kt`
  - Issue: 21 实际错误为 `/data/data/com.narra.app.dev/...`，原公共脱敏器只认识 `/data/user/0`，UI可能暴露实际私有位置；此前普通 IO fixture 不能替代此别名证明。
  - Fix: 同一匹配规则覆盖 `/data/data`、`/data/user/<n>`、`/data/user_de/<n>`。新增一条 JVM 回归验证四个真实别名与安全中文 fallback，原 HTTP/secret 上下文断言保留。与初始实际源码独立逐字节比较仅一条规则、两行 IDE 延续缩进；完全逆转后 SHA 与原 bytes 一致。
- File: `ui/screen/roleplay/CompanionImeWindowAssertions.kt`、`CompanionHistoryScreenTest.kt`
  - Issue: 21 的 IME 断言在真实窗口 resize/re-layout 结束前读取边界，未证明保存按钮可达。
  - Fix: 等实际 IME 布局完成，继续使用同一 Dialog owner、真实 visible area、完整触控 bounds 和48dp/200%断言。29真实320dp/200%用例通过；没有删掉 IME 或缩小触控断言。
- File: `ui/navigation/CompanionIntegrationStoryTest.kt`
  - Issue: 21/29 以 TITLE 单独点击生产列表，与受保护 ChatSummaryRow 实际“角色 / TITLE”不符；34 编辑窗关闭后，仓储窗口刷新尚未结束，导出/选择/删除仍 disabled，故事提前点击。
  - Fix: 等实际 `phone_swipe_row_$SCENE` 入口，并在真实动作前等唯一 enabled 按钮。原开场未接受不入库、输入/CONTINUE/reroll 原文和 revision、真实 SAF 取消、删除、原 run、离线页面零额外请求及最终第5次 HTTP/foreign 排除全部保留。34 Docs与筛选通过，38完整入口故事1/1通过；没有跳过后续流程。

## 全范围静态核对

- 范围由 `validation/initial-source.json` 的1491项与 `final-source.json` 的1531项逐字典重建：**40新增、35修改、0删除，共75个D6工程路径**，与 `final-scope.json` 逐项一致。03/09/13阶段实际为72，16阶段含旧codec修复为73，随后共享安全主源/测试新增两个修改路径成为75；阶段数不是最终数。未使用HEAD中的既有用户差异推断本轮范围。
- 独立重新hash验证冻结字典，工程路径0漂移；唯一冻结后差异是主会话同步的 `版本功能添加/优化.md`，未参与75工程变化或296保护项。保留原验证冻结 `final-source.json`；当前 `delivery-source.json` 的1531项全部实际hash一致，`delivery-scope.json` 为40新增/36修改/0删除，共76路径＝75工程＋1版本记录。26/27/29/34对最终工程的唯一差异为后续完善的同一个入口故事测试文件；main完全相同，36/38工程字典完全相同，无需因文档同步重编。
- 296 个起点保护项逐文件重新计算 SHA，只有已授权 `app/build.gradle` 一行变化。删除实际 `androidTestImplementation libs.okhttp.mockwebserver` 行后与 D5 原 bytes 完全一致。ImmersivePhoneShell 的三个差异块均位于 NewChatSheet 声明/本地筛选；原全壳、皮肤与其它用户修改保留。
- 56→57 仅新增 `context_transfer_operations` 及 `(kind,stage,createdAt,id)` 索引；DAO/Entity、迁移、ALL 注册与 AppGraph 注册对应。独立读取最终 JSON：56 为 58 表、57 为 59 表，58 旧表定义逐 JSON 完全相同。56 SHA 为 `d5e2dff049818be64c7ab4b3bf2b767b99f1bc0546756f10a333ca05e903eab7`；57 为 `0f58b4d0726b81f813da2628c1de423925b1252cd7135ac79c60e7ed6f0df8cd`，匹配 01 导出证明。新 S3 v56 的 174 条 SQL 与固定 56 导出逐条完全相同，SHA 为 `57da481f5082135f40b13bdbc4ac722c5b217e02030f3f1e485d84b92e59af51`；源/目标/identity/守卫同步为 56→57，旧 S3/schema 保留。
- 历史领域：canonical 管理/TXT 与 Reader 默认资格分离，pending INPUT/CONTINUE 仍要求合法正反 turn/source/角色/persona/space/run；坏 link 不降 legacy。批次先完整预检，后续冲突用异常退出同 Room 事务回滚；媒体/未知字段保留，来源失效沿 D1/v2.4 钩子。只读旧档不 bind/enter 当前运行，独立人工版本与声音收藏不被级联清除。
- TXT/SAF：仓储全量集合、UTF-8 BOM、完整 raw/时间/说话人/typed 控制行，不读取 UI 窗口作全部；旧 requestId 不套用新查询，新 B 等旧 A 回调后可启动；writer write/flush/close 后才 Saved，content URI 与协程取消有真实端口。
- 资料：公开 DTO 是独立白名单；PNG 实际字节 hash/CRC/块结构/有界 deflate、行数/过滤字节和 Adam7 均检查。私人 v3 只扩角色偏好/装扮，旧 v1/v2/缺域保目标，未来版拒绝，记忆出处合同保留。首次准备冻结一份源 bundle 和本机 CAS，旧 v1 本机冗余 target 字段兼容。Room 与 ROOM_COMMITTED 同事务、文件只建新 hash 入口、逐目标 DataStore CAS 与回执同 edit；已成功目标只补 Room ack，不覆盖后改、不复活删除角色、不重写 selectedAssistantId。
- UI/生产接线：真实 AppGraph 装配同一恢复仓储，原角色手机主壳→陪伴→历史/Reader/声音/装扮→原线上；Settings 资料入口接恢复回调。Reader 与声音模型、页面状态按完整定位/旧档 route key 隔离，旧两参导航/factory/构造桥保留；成功 IO 返回后 `requireReadScope` 检查、失效清窗口/选区，普通同目标 IO 保窗并重启观察。
- Debug 仅精确 localhost/127.0.0.1 允许明文，不包含子域；base 禁止远端明文。main/baseline、权限、信任证书无新改动；未新增 GPS/地图/定位依赖或调用。

10 的七项 transfer 失败（含 W1）已由原 transfer 检查者完成、释放：来源明确非空、导出 DTO 空字符串，逐个 source.id 查询真实 SQLite NULL/空串/唯一行；真实 shared Flow 等待实际目标字段，合法素材名称保私有路径/秘密哨兵，JSON AST 负例不再依赖可能无效的字符串替换。公开 codec 仅在解析边界把 IOException 保留 cause 包装为安全 IllegalArgumentException，白名单/版本/PNG 拒绝语义不放宽。本代理已读取该交接与上述实际代码，并核对 W1 SQL 原始断言；未擅自写其它执行者文件。此阶段 10-after 当前仅 6 个已授权文件变化（1 个 codec 主源、5 个测试/夹具），没有额外源漂移。12 定向复验原 XML 已独立确认 25/25、sourceStable=true。

## Findings (not fixed)

**无尚未修复的已确认D6工程问题。** 21的文件发布、私有路径脱敏和IME/故事入口问题都已修复并有最终复验证据。

- Screenshot：15 verify的52项像素差异保持未接纳。旧34个actual与D4字节完全一致，18个E05没有D4 before；不得据此称verify通过、自动record/替换基准或推定18项是D6回归。需要独立视觉接纳决定，本检查未代替用户作决定。
- 真实模型表达/MiMo、物理设备声音/蓝牙/耳机路由未测。Mock HTTP、本地WAV与合成AVD只能证明工程合同；没有凭据/收费请求或真实用户数据库操作，未发布。

混合 SQL arrayOf 的推断、tmpdir 可空及四条 UseKtx 等未构成当前失败，不扩大风格修复。

## Verification

- Lint: **pass**。26封存原XML与当前实际Lint XML SHA相同：0 errors/fatal、61 warnings、3 hints；36最终AndroidTest+Lint原日志成功，149.781秒、sourceStable=true。四条新增UseKtx是样式建议，没有扩大修改。
- TypeCheck: **main/JVM/AndroidTest/baseline pass**。26最终有关JVM/main与Debug构建成功，633.703秒；27当前main的compileBaselineKotlin原日志成功，172.346秒；36最终compileDebugAndroidTestKotlin/assembleDebugAndroidTest成功。各轮sourceStable=true；独立源码绑定确认main相同，36/38与最终工程相同。旧baseline/release APK不作当前证据。
- Tests: **最新独立JVM 2956/2956，0 failed/0 skipped**。逐个解析13+14+24+26原suite XML，全部class+method+outcome+report与对应validation.json一致，按方法取最新。13是唯一一次实际无--tests完整套（2955）；14完整纠正旧codec方法1/1；26受影响50/50并含新增安全回归，因此最新总数2956。六条本代理授权/迟到null回归在10原XML已独立6/6确认，最终有关方法没有漏验。未重跑完整套或累加重复轮次。
- Native: **最新独立方法9/9，0 failed/0 skipped**，来自29六条非故事、34两条Docs/筛选、38一条完整入口故事。逐行解析原instrumentation class/test/statusCode，与validation和native-latest-methods一致。29为9项7/2，34为3项2/1；38终端明确`OK (1 test)`、status0，47.556秒/sourceStable。保留先前失败，不把adb exit0当成功，也不把三轮相加为13条或声称最终单次9条全跑。
- Ledger: **52/52**。ID集合与父acceptance-matrix完全相同，52唯一ID、missing空。124个JVM引用逐项解析其指定原XML并核对该方法最新pass；本次原生引用核原runner。U09明确引用D4实际触控方法，原D4该方法status0，而所在33条轮次曾有其它1失败，不能倒填成D6执行或整个D4轮次全绿。G12核最终静态manifest保护；每行效果/权限/恢复限制保留。
- APK/install: 当前Debug26与Test36实际SHA分别为 `f3d1698f05afa2dcb160047902993deeaee9b8f5c71e97f968ec29f24b410331` / `3f58a3309ef9cd97bfd53a837f52a61f7a4266174aae20f4a0b5f94b90e4ff7e`。Debug `app/build/outputs/apk/debug/Narra-v2.3.0-dev-20300-dev.apk` 85268608字节；test 2824910字节。37两次真实安装Success/二进制readback，独立重hash `app/build/tmp/d6-installed-37-install-final-test/` 两份实际拉回APK，与当前/构建/安装proof全部一致。包沿本地既有2.3.0-dev版本名验证，本次工作记录归2.4.1；不是2.4.1发包或更新推送。
- Native候选/TXT: 34封存7 PNG+7元数据，其中4张来自该轮仍失败的早期入口故事，不能作完整故事成功；38封存8 PNG+8元数据，全部来自最终完整故事。30个文件实际SHA/尺寸、两个tar SHA和route/720×1280元数据均匹配，排除旧AVD候选。11张最终选用候选逐SHA绑定并查看contact sheet，未见阻塞主动作的重叠；不接纳Roborazzi基准。34真实TXT22119字节/SHA `f6fb83bff84052b430909a853a49e0866a91430af337de0bb5a866c935d63f90`，BOM、123记录头、哨兵1..120、替换第0条的完整未载入raw/标签/实体/空白/24 CRLF及两个特殊ID定位原文逐字节核实；真实FOREIGN缺席。迟到文件21字节仍为“旧请求不得覆盖”，没有使用29的旧候选代替。
- Static/spec: **pass**。1531冻结项的工程0漂移、75工程路径、296保护/295原bytes、build.gradle唯一授权行与D5逆转bytes、56/57旧58表及S3共174条SQL合同均核对。共享安全主源仅一规则+两缩进，逆转SHA `19ef485969c3878202889b4f11d8a8f461244998210b451af4f1a19b2f97766c` 与初始实际文件相同。当前delivery1531字典0漂移；唯一新增版本文档另计76总路径。companion-history-transfer规范已同步H1–H3/TXT授权、目录发布/旧单文件兼容、fixture重开、真实Dialog与动作enabled等待；公共错误规范已同步Android别名。
- Screenshot: **verify未通过/接纳pending**。15原XML为53项、1匹配/52像素差异/0skip，66.945秒/sourceStable；全部failure均为Roborazzi像素变化，没有额外行为/捕获失败。104 fresh PNG、34旧actual与D4 byte相同、18 E05缺D4 before，未record/替换基准。

## 旧失败与修复轮次

原始失败日志/JSON/XML全部留存，下面是历史轮次，不覆盖最新结论：

| 轮次 | 原始结果与后续处理 |
| --- | --- |
| 01/04/05/07 | 编译/依赖/import问题与07 Java heap OOM保留。07为701.77秒、零测试；09命令级4GB/in-process/单worker解决，319.738秒联合编译通过，未改全局或gradle.properties。 |
| 10→12 | 10为146项137通过/9失败、58.208秒；历史午夜/IO fixture及7 transfer fixture/边界修复后12原XML25/25、445.004秒，最新相关146/146。 |
| 13→14 | 13无过滤完整2955项2954通过/1旧future-v3失败、368.093秒；第0坏包失败之后的14条件不能视作当轮已验。14改future4并验合法v3/其余全坏图，完整方法1/1、120.169秒。 |
| 16/17/20→21 | 旧双APK/Lint及baseline成功并安装核SHA；21原runner9项5通过/4失败、26.071秒：Android发布、IME、两故事失败。旧成功包不代替最终修复后的构建/安装。 |
| 24→26 | 新目录布局后24为50项48通过/2旧路径fixture失败；完整旧单文件兼容与新路径fixture修复，26原XML50/50、633.703秒。共享安全新增一条使最新JVM从2955成为2956。 |
| 29→34→38 | 29实际9项7通过/2入口故事失败；34实际3项2通过/1编辑后动作尚disabled失败；最终只补真实入口/动作等待，38完整故事1/1，所有原断言保留。 |

## 旧截图对比索引

已独立读取 D4 `research/resume-2026-11-18/d4-final-lint-screenshots-46/compare-with-d3.json`，但它只是汇总。实际索引为该轮 `candidate-hashes.json`，形状是 `{filename: sha256}` 字典 68 项；图片为同目录 `candidates/{filename}`。68 个实际 PNG 存在且逐文件 SHA 匹配索引。`*_actual.png` 精确 34 个，与其 `reference` 指向的 D3 `11-09-date-companion-style-observation/research/resume-2026-11-18/d3-screenshots-03/candidate-hashes.json` 及实际 PNG 逐字节全部一致，缺图/哈希漂移/新字节差异均为 0。

D4 原始 `test-results/*.xml` 重建 35 个实际方法：H01 18/18 差异、H02 3/3 差异、E04 13/14 差异，唯一匹配 `OfflineRoleplayScreenshotE04Test.e04_reading_mixed_background`。因此旧 34 项是尚未接纳的历史差异，不能说 verify 通过；D6 可从当前 fresh actual 与这 34 个封存 before 做同名 SHA/必要像素对比。

当前 E05 登记/原 PNG 均为 18；D4 没有其候选 before。D6 计划 E04/H01/H02 原 35 + E05 18 = 53 项。E05 可比较既有基准，但首次差异不能直接被标为本轮新增回归；必须单列历史来源不足，不能 record、替换基准或倒填 before。

D6 15 的封存目录为 `validation/15-screenshot-verify/candidates/roborazzi/`。独立核对 34 个同名 actual，D4→D6 字节差异 0、缺图 0；另外 18 个 E05 actual 单列候选。该轮唯一匹配仍为 E04 阅读混排，旧/新全部 52 差异保持未接纳。旧 APK 的 `updatedDuringCommand=false` 不被用作本轮构建证据。

最终新增测试文件独立扫描为72个JVM声明、9个原生声明；Reader VM原文件另增6个方法，公共SensitiveTextRedactorTest原文件另增1个方法，D6新增JVM声明合计**79**。这些声明数只用于核对漏方法，不是实际通过数量；实际结果是最新JVM2956与本次独立原生9。
