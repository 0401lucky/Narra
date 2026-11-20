# D3 续接独立只读审查

2026-11-17，状态：`READONLY_REVIEW_COMPLETE_PENDING_AUTHOR_HANDOFF`。

已读取完整 native hook 保存输出及未内联的 PRD、design、implement，按 D3
开始基线核对实际改动：25 个既有源码文件变更、19 个新增文件。旧 v2.4/D1/D2
及用户工作区差异不混算为 D3。本检查者仅写本报告，没有修改产品或测试源码、
运行 Gradle/formatter/设备/付费 API，正式修复等待主会话明确移交执行权。

## 新确认问题（正式阶段复现并修复）

### R4：摘要的完整来源补充重新带入 observe

- `conversation/ConversationSummaryCoverageSupport.kt:40` 的 `sourceText` 返回
  完整消息 raw；`:269–285` 的 `completeInput` 在格式化文本不包含 raw 时追加
  整段 raw。
- `viewmodel/RoleplayContextUpdateSupport.kt:113` 先以
  `RoleplayTranscriptFormatter.formatMessageSegments` 格式化正文；新的陪伴
  投影在这里正确排除 observe，但
  `conversation/ConversationSummaryCoordinator.kt:147–157` 随后调用
  `completeInput`，将补回 observe 的字符串送给实际摘要服务。
- 触发输入：一条已提交陪伴回复包含合法 observe 哨兵值和有效 dialogue。
  正文投影不含哨兵，完整 raw 必定不在格式化输入中，导致补充段重新携带哨兵。
- 原始指纹、`sourceSnapshot` 的 raw 长度及覆盖必须保留；不能把 `sourceText`
  全局改成派生正文来绕过。修复应位于摘要消费边界，同时保持旧普通消息的
  完整来源补充与来源拒绝行为。
- 拟红测：真实 Companion Room 提交/读取 → 实际 SummaryCoordinator →
  DefaultAiPromptExtrasService/MockWebServer 最终请求；断言有有效正文、无
  observe 哨兵，保存后的消息、来源指纹、覆盖区间及修订仍与原始 raw 一致。

### R5：有界 observe 开标签识别遗漏超长块

- `roleplay/companion/CompanionReplyParser.kt:13` 用
  `<observe\\b([^>]{0,4096})>` 识别块，属性超过 4096 字符时整个块不进入
  excluded ranges。
- 剩余文本交给陪伴保正文的旧 parser；其 `<[^>]+>` 去标签规则保留 field
  内容，于是只有观测的回复被投影成有效台词。
- 确定输入形状：`<observe version="1" pad="` + 5000 个 `a` +
  `"><field id="time">OBS_BOUNDARY_SENTINEL</field></observe>`。
- `conversation/CompanionTurnCoordinator.kt:231–238` 和
  `data/repository/roleplay/RoomCompanionRepository.kt:620–623` 共用
  `hasReadableBody`，因此该遗漏同时影响 collector、canonical 资格与后续语义
  格式化。当前为源码路径确认，尚未运行新红测。
- 拟红测：只有超长 observe 时 parser 无 beats、真实 collector/Room 不提交；
  加有效后续 dialogue 时仍保留正文/raw/span，field 哨兵不进入语义来源。
  修复保留超限诊断和观测限额，不能让超限块因识别限额变成正文，也不放松
  完整性守卫或旧 parser 默认兼容行为。

## 已核对的 D3 边界

- 原 R1/R2/R3 修复已在源码，24 份 `implementation-jvm-02-results/TEST-*.xml`
  共 254 项、0 failures/errors/skipped；不是本检查者新执行的测试。
- raw accessor 对 linked 消息返回 content；canonical 与持久 TEXT 使用同一 raw。
  Room 的真实关联读取生成独立 Transient metadata，metadata 与机器来源 proof
  分离；copy 修改 ID/正文/角色/关联/版本后旧观测不可复用，导入不能恢复 metadata。
- RETRY 显式复用原 presentation/hint；旧无快照已请求回合采用无观测默认快照，
  REGENERATE 才冻结当前设置。live settingsRevision、来源 scope 和最后 HTTP
  守卫继续独立校验，表现来源修订不被拿来放宽发送权限。
- `MemorySourceStorage.contentForSource`、`getSourceSegments` 沿同一
  memorySemanticSegments 排除可识别 observe。C2 batch 的坐标相对于其
  `contentFormatVersion=2` 的冻结语义来源，而非 CompanionRawSpan；没有将
  派生 beat 的原文坐标直接冒充 batch 坐标。正式阶段仍须补实际捕获/提取请求
  证据，不能以 Formatter 单点测试当作完整链验收。
- 同媒介请求消费 Companion displayText，跨媒介从统一 transcript 转译，原文
  不写回；观察定义从历史快照而非当前 label 读取。
- 角色复制在同一 DataStore edit 内读取当前源角色/配置，保留同期角色与选择；
  目标冲突/未知源 schema 在任何写入前拒绝。独立小映射、CAS、规范化 Saved
  回执与高版本只读有对应生产路径和作者绿测。
- 设置草稿、错误/冲突固定保存区、明确丢弃/重新读取、观察 Dialog 的当前
  source keys，以及准备/场景/实际 ReadingMode 的显示 gate 已核对。
  新原生源码为设置 9 项、观察含阅读 7 项，连同 D2 21 项计划为 37 项；尚无
  设备通过结论。

## 冻结源码漂移（主会话正在协调）

初次只读复核时，1415 项源码与作者 resume manifest 一致；后续出现同期 UI
变更。274 项旧 schema/截图/参考保护项一直一致。本检查者未写任何这些文件，
主会话已要求保留而非恢复/覆盖。以下是 2026-11-17T15:01:04.790379+00:00
相对 `implementation-resume-source-hashes.json` 的实际观测；期间从 7 项增至
11 项，最终工程检查必须重新冻结并说明来源。

| 文件（相对 app/src） | 原冻结 SHA-256 | 当前 SHA-256 | mtime UTC |
| --- | --- | --- | --- |
| main/java/com/example/myapplication/ui/AppRoot.kt | 9517b512cb5f8b67e821e3f5afd55bae0aa154e7529bcfe83a333772f5bf380e | 2ab401d3be9243e9b3a5bfcb518eadfab8726a126568706fef1ad57425ad8e9a | 2026-11-17T14:49:08.477712+00:00 |
| main/java/com/example/myapplication/ui/component/roleplay/ImmersiveGlass.kt | fed054b3cb972e148bffb4f61733a84e2518d5623a3498a2705a0b1b0b43def6 | a9bdd564a6f12f2e743bd180bac02511cc0ca4c3d028d305044334c710651657 | 2026-11-17T14:51:29.808649+00:00 |
| main/java/com/example/myapplication/ui/component/roleplay/RoleplayDialogueBubbles.kt | cdf5e55bbf4308fb03b3009b5bbae978d56c297b38301c728be1eacf3c20c7f2 | bbdec74d875131714ca77bd991dcc8f021e874a09f01125726ca8881fa8c18ad | 2026-11-17T15:01:03.795378+00:00 |
| main/java/com/example/myapplication/ui/component/roleplay/RoleplayNoBackgroundSkin.kt | 05913a0d52b59f790fe2cee85bb087fd9a49e96227ef510e51745109b48506f7 | db8977af8bff4e00a5227037603e0890e1a12c9d6f484c08d37eeec7b2ec29d6 | 2026-11-17T15:01:02.380116+00:00 |
| main/java/com/example/myapplication/ui/component/roleplay/RoleplayOnlinePhoneBubbles.kt | 7bb4bbcb4d3199f7838a3aac3ff55d9317e48c6c34a4003caa422d8b0be75d13 | 13a33bc66a4ea70aa17b636305a83f7f4a52e8a4f87195ea6fa00b662d314c9f | 2026-11-17T15:00:15.911855+00:00 |
| main/java/com/example/myapplication/ui/component/roleplay/RoleplayStreamingAutoScroll.kt | d6c0693c397fc560e368746eceaff837895a6ec97eff92768637348d2c3f1e0a | 3cf2a2e161761060e4ea0a815fbbf99c0404b2c79393996d294404408f370f9a | 2026-11-17T15:00:12.295774+00:00 |
| main/java/com/example/myapplication/ui/navigation/AppNavHost.kt | 54eb5d1ddb1bcba5b823f579ab32ee101e70de61f0774c5047cc6bb7cba439f4 | 0b68e780b2882a9082312d788c3fe98d92c4f81fed3ab5516492a4d60619623b | 2026-11-17T14:47:54.167391+00:00 |
| main/java/com/example/myapplication/ui/screen/immersive/ImmersivePhoneShell.kt | 71cfb628e8e29d62b37c203cd7726eba762469211145f2d0244c72bdc86f8473 | b0039dc9c78408acf58a3f066f2df5364c527f432ccabc2743464cd7f22048cc | 2026-11-17T14:58:28.067945+00:00 |
| main/java/com/example/myapplication/ui/screen/roleplay/RoleplayScreen.kt | 28321e0d5c5ee54ee76b015b4ddfb36ec721bb726398cae4e27b972952c44ee0 | 7f2239f41f33c7d12be4ae77787b0e00924f24c0de3fc06317533649195d2b7c | 2026-11-17T14:52:04.482446+00:00 |
| main/java/com/example/myapplication/ui/theme/ChatAppTheme.kt | d80651d3a849159a56693f72617eb1f8cc184343672ff22ccacfec4288a896b1 | db3bdb275258d4b14e0b0ec4952c4a6466a44b39247c36d17d8f34f97c0cb00f | 2026-11-17T14:53:40.073513+00:00 |
| test/java/com/example/myapplication/ui/theme/UiTokenSourceInventory.kt | 7eb4807c4ff8522c42dbe811865ee5b094157b1347429729070f4712981c3e5f | c42b3f044ab8a5aa374a2e8f1daa9bb608597a75e7c3dff718ce205112b61d70 | 2026-11-17T14:53:41.882290+00:00 |

## 正式检查待办

1. 作者释放后由主会话明确转交；核对最终 manifest/原始报告和同期 UI 改动。
2. R4/R5 真实红测、最小产品修复与定向绿测；补实际 capture/增量提取/摘要/
   同跨媒介请求证据，保留 raw、版本、hash 和对应来源坐标。
3. 当前源码稳定后执行所需旧回归、受影响截图 verify、Lint/全源集编译与
   双 APK；不 record/接纳基准，不更改版本或旧 schema。
4. 输出最终准确去重计数、source/APK hashes、未执行项与权限释放，再由主会话
   做隔离设备验证。本预审不是 D3 最终通过。
