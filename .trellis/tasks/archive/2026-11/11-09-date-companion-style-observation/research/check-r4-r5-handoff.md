# D3 R4/R5 定向修复交接

2026-11-17，状态：`R4_R5_IMPLEMENTED_STATIC_CHECKED_PENDING_JVM`。
按用户本轮收口要求，只修改两处已确认逻辑及必要回归；D3 保持 in_progress，
不计为 D3/2.4.1 验收完成。**源码、测试与执行权现已释放给主会话。**

## 已修改

- `ConversationSummaryCoverageSupport.completeInput`：陪伴补充使用已有
  CompanionReplyProjector 的 displayText，避免正文 formatter 排除 observe 后又
  拼回 raw。sourceText、来源指纹、sourceSnapshot 及 UTF-16 覆盖长度保留原契约，
  普通旧消息继续完整原文补充。
- `CompanionReplyParser`：先识别整个 observe 开标签，再显式检查 4096 字符属性
  上限；超限块仍从正文排除并记录 OBSERVATION_LIMIT_EXCEEDED。8192 块内容、
  400 单值限额、旧 parser 默认行为和完整性守卫保留。
- 3 个既有逻辑测试文件新增 6 个方法：真实 Room → SummaryCoordinator →
  MockWebServer 最终请求与 raw 覆盖；旧源补全/拒绝；超长 observe 的 parser、
  collector、canonical 拒绝及后续有效正文/raw/span 保留。

## 实际验证与限制

- 5 个改动文件的 `git diff --check` 通过；定向尾随空白/行首 Tab 检查通过，
  6 个新方法均存在。核对了实际 RoomMemoryRepository、SummaryCoordinator 和
  DefaultAiPromptExtrasService 接口。文件 SHA-256 见
  [结构化记录](check-r4-r5-validation.json)。
- **未运行新 JVM 回归、TypeCheck、Lint 或 IDE formatter**。另一位 agent 仍在
  修改共享 UI，按用户要求未启动长或竞争构建；手工保持既有 4 空格格式。
  历史 254 项不验证这次新增修复。没有新 APK、截图或设备通过结论。
- 本检查者没有修改 UI/主题/导航/截图 inventory，没有启动 Gradle、formatter 或
  设备进程，不提交/推送/发布。版本和旧 schema 不变；D4–D6 不在本轮推进。

## 后续最小回归

共享源码与构建协调稳定后运行以下已有类；这是待执行命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.conversation.ConversationSummaryCoverageSupportTest" --tests "com.example.myapplication.roleplay.companion.CompanionReplyParserTest" --tests "com.example.myapplication.roleplay.companion.CompanionProjectionRepositoryTest" --console=plain --offline --no-daemon --max-workers=1 "-Dorg.gradle.jvmargs=-Xmx3072m -Dfile.encoding=UTF-8"
```

整合编译、Lint、双 APK、旧截图及原生设备验收等双方共享源码稳定后再处理。
本交接只声明代码与定向静态检查完成，不声明未执行项目通过。
