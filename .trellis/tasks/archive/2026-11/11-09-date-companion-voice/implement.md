# D5 语音与朗读控制：执行计划

2026-11-09整体方案已获实施批准；本块按下述依赖激活，当前状态见task.json。开始前复核 [PRD](prd.md)、[design](design.md)及真实JSONL上下文。

## 开始条件

- [x] [D4](../11-09-date-companion-stage-reading/prd.md) 工程前置已满足，沿用149 JVM/33设备最新去重、编译/双APK/Lint证据；旧34截图差异不阻塞D5，不重新执行D4。
- [x] 起点schema55、工作区582项既有差异；源码/旧schema/版本参数/更新JSON哈希已保留于 `research/validation/baseline-files.json`，task.py validate通过。
- [x] 2026-11-19 主会话激活现有子任务，先冻结 `model/CompanionVoice.kt` 与仓储ports，再派发 voice_runtime/voice_storage/voice_ui 三个原生trellis-implement；独占文件见 `research/interface-freeze.md`，root唯一Gradle/formatter/设备。

## 有序实施

- [x] 1. 确认D4完整SpeechUnit/显示选区/源修订与独立声音generation，自动播查真实turn.kind排除OPENING；新偏好与旧后台开关独立。领域接口冻结完成。
- [x] 2. 实现统一请求键、冻结TRANSLATION HTTP/缓存、实际MiMo参数/克隆字节版本、有限会话译文缓存；旧翻译ABI保持，新路径凭据拒绝，取消和脱敏。
- [x] 3. 实现会话唯一音频owner/完整grapheme分段、换句/模式/角色/源/离场守卫、真实start后Playing；来源观察异常后显式恢复、auto不失去监听继续。
- [x] 4. 私有文件完整WAV/MP3/hash/长度发布、PENDING/READY及免费回执补存；停止后只补存不恢复队列；取消tombstone/失败清理与引用保护、有限LRU/手清缓存。
- [x] 5. 独立收藏存实际音频/文本/语言/来源版本；已有零生成、显式生成收藏、删源失效/清缓存/取消收藏不同语义；55→56及固定55/S3相邻守卫维护，旧schema保留。
- [x] 6. Stage/列表/Reader/独立角色声库真实UI和默认静音、首bind/后台/恢复基线、翻译失败两种显式动作/CAS/用量已接线；D4正文与旧SelectionPanel入口保持。
- [x] 7. 实际JVM最新去重107、隔离设备8、编译/baseline/dev/test APK/Lint通过；真实MiMo及物理设备未测。没有为D4旧截图差异重复测试或阻塞。

## 验证命令

下列旧测试选择器来自静态研究；先通过rg确认当前实际名称。新Companion测试在实现后发现并运行，零测试不能算通过。本轮规划未运行这些命令。

```powershell
rg --files app/src/test app/src/androidTest | rg 'Companion|Roleplay|Memory|Voice|Translation'
./gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.conversation.VoiceSynthesisCoordinatorTest" --tests "com.example.myapplication.data.repository.tts.MimoTtsClientTest" --tests "com.example.myapplication.data.repository.ai.AiTranslationServiceTest" --tests "com.example.myapplication.ui.component.roleplay.RoleplayVoicePlaybackControllerTest" --tests "com.example.myapplication.data.repository.AudioFileStorageTest" --console=plain
./gradlew.bat :app:testDebugUnitTest --tests "*Companion*Test" --console=plain
./gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin lintDebug --console=plain
```

新增CompanionVoice/Cache/Favorite/ResourceRecovery测试；Fake音频时钟和MockWebServer证明实际调用数/停止/源版本。必要物理设备测试准备、取消、离场、后台与系统音频行为，未执行明确记录。

构建/截图/设备按 [父验证计划](../11-09-date-companion/implement.md) 与实际风险执行。共享数据/协议变化必须跑相应旧回归；无新失败或修改不反复全量测试。D6负责最终完整JVM/受影响截图/双APK/集成。

## 独立检查与交接

- [x] 原生trellis-check全范围复核及F1–F5定向修复，绑定原始报告/最终源码/保护；见research/check.md。
- [x] 本块AC及父U11–U15/U17/X05/X06责任关联真实工程证据；模型/物理设备/视觉接纳单列。
- [x] 主会话核验源码，新增7节声音code-spec及索引/数据库/网络合同，更新2.4.1版本记录。
- [x] research/handoff.md给出实际接口、命令/数量/失败/源码与D6接手事项；D6及整个2.4.1尚未完成。
- [x] 按用户要求保留未提交工作区、旧基准、版本参数和更新JSON；不提交/推送/发布/自动归档，journal手工记录不调用自动提交脚本。

## 实际轮次

01/06：28新Kotlin与4修复文件定向IDE格式化，无越界。02新schema并行KSP读EOF、03三处UI类型编译失败均保留；拆串行/定向两hunk修复，04编译/双APK通过。
05实际105/105；07只复验Controller22+Room7=29/29，最新107；08baseline/Lint通过（57 warnings/3 hints/0errors）；09只跑两现有凭据方法2/2并完成最终编译/双APK/baseline/Lint，源码稳定；10隔离设备8/8，runner完成、无skip。所有命令/原XML/raw/SHA在research/validation，不累计重跑。

## 风险与回退

额外费用、旧请求抢播、缓存串音和文件/Room不一致为主要风险。先关闭陪伴播音可回退文字，不删收藏/历史音频；新迁移前向修复。
