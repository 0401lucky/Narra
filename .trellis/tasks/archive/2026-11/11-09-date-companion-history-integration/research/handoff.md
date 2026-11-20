# D6 交接：历史导出与集成

2026-11-20。D6工程与原生集成门禁通过；视觉基准接纳、真实模型和物理音效单列待验。
续接会话 `01a1f40d-b58c-71b2-9578-d1f93fbd6a65` 的已批准 v2.4.1 范围。
独立 [全范围检查](check-full-scope-2026-11-20.md) 已完成，无遗留工程问题；
开发日志已记入`.trellis/workspace/lucky/journal-1.md` Session17，未自动提交或归档。

## 已实现接口

- `CompanionHistoryManagementRepository` / History VM/Screen：场次、日期、年月与
  只读旧档，授权仓储全量集合、50条窗口和所见revision批次CAS。合法pending原文
  可管理/导出；Reader、声音与机器来源仍独立校验，损坏双向link不降级legacy。
- `CompanionTextExporter` / SAF：完整raw、时间、说话人与typed控制，UTF-8 BOM；
  取消/旧requestId零IO，读取后及写前复核scope，写入和关流成功后才提示Saved。
- `CompanionAppearancePackage` v1：本地公开白名单、PNG块/CRC/有界像素验证和
  内容hash映射，不带私人原文、记忆、Key、私有路径或音色样本。
- 私人`ContextDataBundle` v3只扩陪伴偏好，保已有授权记忆/来源；旧包缺域保值，
  v4+拒绝，不扩成全聊天/音频备份。冻结来源只保存一份图片内容。
- `ContextTransferOperation`按PLANNED→ROOM_COMMITTED→FILES_COMMITTED→COMPLETE
  与逐设置目标回执/CAS恢复；免费补确认保用户后改、不复活删除角色。
- Android文件先完整写入/sync/hash私有临时目录再改名发布非空目录；并发不可覆盖
  非空目标。保旧`assets/{hash}.png`/`plans/{id}.json`的校验复用/取消；不依赖硬链接。

## 当前验证状态

| 门禁 | 实际证据 |
| --- | --- |
| JVM | 13唯一完整轮2955项；14修复唯一旧future负例；26定向50/50，含新增脱敏1方法。最新唯一2956/2956，无失败/跳过。 |
| 编译/包/Lint | 26双新APK、Lint成功，0 errors/61 warnings/3 hints；27当前main的baseline编译成功；36最终测试APK/Lint通过，main未变。 |
| 原生 | 29的6项历史/迁移/真实文件方法、34的2项SAF/空筛选、38的完整HTTP/返回故事通过；最新唯一9/9，无失败/跳过。 |
| 截图verify | 15共53项：1匹配、52差异；其中34 actual与D4逐字节一致，另外18 E05无D4 before。基准未接纳。 |
| 52行账本 | 52/52工程verified；每行关联实际class+method/最新轮次及适配边界，没有缺失方法。 |

本轮原始命令、XML、instrumentation、逐方法最新去重及前后源码SHA见
[validation/rounds.json](validation/rounds.json)。最终以该索引和
[结构化52行账本](acceptance-evidence.json)为准，不累加重复轮次数。

完整故事保留4次实际MockWebServer请求、接受/输入/typed继续/同ID重生成、原文编辑/
真实SAF取消/显式删除、Reader/声库/装扮及回原线上run的全部原断言。真实文档故事
导出123条而UI仅50条，旧回调零IO、新IO失败清旧成功、原文/设置保留及特殊ID定位通过。
11张原生候选已审阅，实际TXT22119字节保BOM、120个原线上哨兵（含窗口外原文）、24次原CRLF、
两条定位原文，另一角色被排除；见 [视觉/TXT记录](validation/native-visual-txt-review.json)。

## 本地构建与安装绑定

- dev：`app/build/outputs/apk/debug/Narra-v2.3.0-dev-20300-dev.apk`，26生成；
  SHA-256 `f3d1698f05afa2dcb160047902993deeaee9b8f5c71e97f968ec29f24b410331`。
- AndroidTest：`app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`，36生成；
  SHA-256 `3f58a3309ef9cd97bfd53a837f52a61f7a4266174aae20f4a0b5f94b90e4ff7e`。
- 37安装后拉回两APK逐字节hash一致；26到36只有整合测试源变化，main源没有变化。
  29/34/38各自安装回执与对应测试/源码绑定保留，不能把旧测试APK算作最终新构建。
  汇总见 [最终门禁/绑定](validation/final-validation-summary.json)。

失败轮完整保留：21的5/9和22的IME失败、24的48/50、29的7/9、34的2/3。
修复Android硬链接限制、私有路径别名脱敏、真实旧/新计划路径夹具、组合标题选择器、
IME重排及编辑后按钮时序；对应方法定向复验，不删用例、不重录基准、不改生产权限。
命令级4GiB/in-process/单worker解决原编译OOM，不改全局配置或gradle.properties。

## 范围与数据保护

相对D6起点1491源码，当前1531项：40新增、35修改，共75路径，0删除。
新增两处共享安全路径来自真实Android异常：私有`/data/data`及任意user/user_de
别名转安全UI文案；一条JVM回归覆盖真实AccessDenied格式。其余共享源字节证明见
[shared-security-scope-proof.json](validation/shared-security-scope-proof.json)。

296保护项295保持；`app/build.gradle`仅增加AndroidTest MockWebServer依赖，
逆变换与D5原SHA一致。PhoneShell仅原NewChatSheet三个适配hunk；固定56旧58表
与57逐项相同，只新增`context_transfer_operations`。S3 v56保持174条DDL。
完整清单/原字节证据见`validation/final-source.json`、`final-scope.json`、
`final-protection.json`与`adapter-scope-proof.json`。

上述final-source保留工程验证冻结值。随后同步`版本功能添加/优化.md`产生唯一文档
差异，不影响APK，也不属于75个工程路径。当前完整1531项SHA另存`delivery-source.json`；
`delivery-scope.json`相对起点共76路径=75工程+1版本记录。final-validation-summary与
delivery-audit明确该bookkeeping差异，不宣称当前全部字典仍与冻结字典完全相等。

功能归属2.4.1；版本参数仍2.3.0-dev/20300，更新JSON保持。仅私有合成AVD、
隔离Room/DataStore、MockWebServer及本地文件；未访问真实用户库/凭据或付费模型。
不提交、推送、发布或自动归档。真实模型人格/执笔权、MiMo质量、物理设备音效
和视觉基准接纳单列待验；G12不新增定位，U14不宣称PCM增量/音频时钟字幕。
