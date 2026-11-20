# D1 三协议结束证据实施交接

日期：2026-11-09。实施子范围完成代码与测试准备；**未运行Gradle、Kotlin编译、Lint、formatter或产品测试**，按主会话分工交D1存储实施者统一验证。不能据本文勾选D1整块通过。

## 接口

新增 ChatStreamEvent.CompletionEvidence：

- status: PromptCompletionStatus。
- rawFinishReason: String? = null。
- rawStatus: String? = null。
- 原 Completed object 保留，只代表传输结束。
- AssistantReply追加completionEvidence字段，默认UNKNOWN，用于非流式、JSON回退与工具最终回复转事件时保留证据。
- 新陪伴collector须消费CompletionEvidence，并另外验证可见正文/结构；协议COMPLETED不代表纯推理或空正文可作为场景。旧ChatStreamingSupport显式忽略新元事件，保留原显示/停止行为。

## 实际路径

| 路径 | 接入方式 |
| --- | --- |
| Chat Completions SSE | 每个HTTP独立tracker，读取finish_reason及refusal/tool调用迹象；DONE/EOF不猜成功，坏data块使完整性保守降级 |
| Responses SSE | 区分response.completed/incomplete/cancelled与output_text.done；读取最终response状态、未完成原因、refusal及客户端工具；输出项临时in_progress可完成，明确incomplete done不被空最终output抹去 |
| Anthropic SSE | stop_reason与message_stop分离，refusal/tool_use拒绝成功判定；消息delta/错误保持旧内容与异常行为 |
| 三协议非流式/JSON回退 | AssistantReply携带同一证据；Anthropic补齐非SSE JSON和误标MIME的SSE回退 |
| 工具路线 | ToolEngine只给最终AssistantReply附证据；有工具轮时实际最终stream使用新tracker，不继承前置轮结论 |
| 既有Gemini原生兼容 | 原内容解析路径保留，candidate finishReason按相同状态模型映射 |

PromptExtrasCore原有结束归一化提炼到CompletionEvidenceSupport，辅助任务与主回复共用规则。保留正常raw字段的大小写/空白；raw只接受有界协议代码，不复制错误句、错误正文或凭据。Responses终止事件可证明完成，但没有实际status字段时rawStatus保持null，不伪造原始字段。

硬网络/API错误仍抛出原有安全异常；可识别的流式远端错误先发FAILED元证据但不会发Completed。协程取消仍抛出CancellationException并走原有资源finally，不改取消为成功。

## 变更文件

路径相对app/src/main/java/com/example/myapplication：

1. model/AssistantReply.kt：事件与非流式证据字段。
2. data/repository/ai/CompletionEvidenceSupport.kt（新增）：统一状态、DTO与安全raw代码。
3. data/repository/ai/GatewayCompletionTracker.kt（新增）：单HTTP流状态累计。
4. data/repository/ai/AiGateway.kt：三协议与所有流/回退/非流式接线。
5. data/repository/ai/PromptExtrasCore.kt：改为复用统一归一化。
6. data/repository/ai/AnthropicProtocolSupport.kt：非流式证据。
7. data/repository/ai/ResponseApiSupport.kt：不完整/取消终止标记。
8. data/repository/ai/tooling/ToolEngine.kt：最终回复证据，工具上限本地兜底为INCOMPLETE。
9. viewmodel/ChatStreamingSupport.kt：原显示消费者最小适配。

测试：

- app/src/test/java/com/example/myapplication/data/repository/ai/AiGatewayCompletionEvidenceTest.kt（新增18个JUnit方法，部分包含协议/原因矩阵）。
- 同目录AiGatewayTest.kt：旧内容/推理/图片/citations测试收集时仅过滤新增CompletionEvidence元事件；原有断言保留，新元事件由专门测试完整检查。

完整11个源码/测试文件及当前SHA-256见 [completion-files.json](completion-files.json)。没有改Room、ChatMessage/MessageEntity、来源资格、AppGraph或RP生命周期；那些由存储方负责。

## 已做与待做验证

已做：

- git diff --check通过（只有仓库既有LF/CRLF提示）。
- 三个新增Kotlin文件的字符编码和词法括号检查通过；这不是Kotlin编译。
- 静态搜索确认旧when消费者有元事件分支，PromptExtrasCore已无重复结束规则。

待统一执行：

```powershell
./gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.data.repository.ai.AiGatewayCompletionEvidenceTest" --tests "com.example.myapplication.data.repository.ai.AiGatewayTest" --tests "com.example.myapplication.data.repository.ai.PromptExtrasCompletionResultTest" --tests "com.example.myapplication.data.repository.ai.AnthropicProtocolSupportTest" --tests "com.example.myapplication.data.repository.ai.ResponseApiSupportTest" --tests "com.example.myapplication.data.repository.ai.tooling.ToolEngineTest" --console=plain
./gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin lintDebug --console=plain
```

新测试覆盖实际MockWebServer请求/认证/路由、三协议状态、JSON/误标MIME/非流式回退、工具前置与实际最后续写、refusal/tool待处理、坏终止体/坏data、纯推理不当正文、远端错误脱敏、消费者取消无成功终止及既有Gemini兼容。

消费者取消用例不声称已验证同步阻塞读中的取消时延；既有同步HTTP/read循环的取消与finally策略未另行改写。D1整体collector的超时/存储/取消与来源资格仍应在整合回归中验证。

## 交接

协议字段形状已通知主会话与D1存储实施者。源码现已释放，可由统一执行者进行formatter、编译/测试和D1集成；本子任务不提交、推送或发包。真实模型/服务效果未测试，当前只提供合成回归。
