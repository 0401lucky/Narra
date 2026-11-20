# D3 上游接口预核对

日期：2026-11-11。下文是D2实施期间的只读接口预核对；D2现已完成独立与设备验收，实际接线与最终证据见 [D2交接](../../11-09-date-companion-entry-context/research/handoff.md)。D3已按该前置激活，下文历史研究不代替D3实现与验收。

## 已有接口与扩展落点

以下源码均相对 `app/src/main/java/com/example/myapplication/`。

| 领域 | 当前事实 | D3责任 |
| --- | --- | --- |
| 配置模型 | `model/CompanionContext.kt` 已有 `CompanionCharacterSettings(schemaVersion, revision, zoneId)`、`CompanionSettingsPatch`、带Conflict/UnsupportedVersion/CharacterMissing的结果 | 在同一模型追加文风、人称、观测、模板等字段，保留D2时区；不另建第二份角色映射 |
| 设置存储 | `data/local/CompanionSettingsCodec.kt` 保留未知JSON字段；`AppSettingsStore.updateCompanionSettings` 在DataStore edit内按角色存在性和revision更新 | 保持逐字段兼容及CAS，补合法字段的独立容错、未知schema拒绝降级、草稿保存失败与冲突回归 |
| 旧角色保存 | `AppSettingsStore.saveAssistants` 在同一edit中保留有效角色的新映射并清理被删除角色 | 旧整列表保存不能覆盖其它角色偏好；角色删除/复制与导入按各自明确语义处理 |
| 请求描述 | `model/CompanionTurn.kt` 的 `CompanionRequestDescriptor` 已有protocolVersion/settingsRevision/promptVersion、授权来源、historyScope、entryOrigin、replyMessageId和timePolicy | 增加不含凭据的实际生成配置/观测字段定义快照；只有revision不足以解释改名后的历史 |
| 持久位置 | `data/local/companion/CompanionEntities.kt` 已有 `requestDescriptorJson` | 新的可选快照可沿此兼容JSON承载；不为同一信息再建平行库，不改写既有schema字节 |
| 请求链 | `conversation/CompanionRequestCoordinator.kt` 创建descriptor并独立准备每请求的history/recall，`GatewayCompanionSession` 在最后发送点校验授权；resolvePromptSettings的陪伴调用以includeScenarioPersona=true保留有效身份槽 | 风格/观测只装饰生成，不能成为检索关键词、角色事实或记忆权限；保留D2三协议最后发送校验与单次persona注入 |
| 提示词 | `roleplay/companion/CompanionPromptSupport.kt` 提供INPUT/CONTINUE/OPENING语义、基本XML和请求时的adaptBuiltInPreset；`RoleplayPromptDecorator` 有显式companionMode分支 | 在此接表现模板和版本，不复制整套网关或改旧线上/长文协议；保留内置陪伴适配的模式边界，重试/补存使用冻结语义 |
| 原文保存 | `CompanionTurnCoordinator` 使用text.toString()生成rawReply，`RoomCompanionRepository` 将artifact.rawReply原样写content | 保留此权威原文；校验空白可用isBlank，不把trim/去标签结果写回canonical |
| 原文读取 | `RoleplayMessageFormatSupport.resolveAssistantRawContent` 当前仍先parts.toPlainText再content并trim | 对有陪伴元数据的新消息明确原文优先级与字节保真，旧消息默认兼容；覆盖存储往返和展示/导出的不同投影 |
| 既有正文解析 | `RoleplayOutputParser` 归一化/清理后输出RoleplayParsedSegment，并合并相邻同类段落；当前无raw span/sprite/voiceEmotion | 陪伴adapter保留原字符串坐标和版本化拍ID；不能从归一化后的位置假装推回原文span，也不能吞掉相邻不同表情的节拍 |

## 必须在实现中明确的合同

1. 生成快照包含实际五文风/人称/附加提示/可复现hint/观测开关及字段ID、label。历史读取依赖对应attempt的快照，不能把当前设置当旧定义；D2的发送前设置冲突行为由最终handoff说明。
2. `ChatMessage` 当前只带companionTurnId与瞬时来源proof，proof不是协议/观测配置容器。投影读取服务须通过合法turn/selected attempt取得版本快照；缺失快照时保留可阅读原文及诊断，不伪造观测或触发模型。
3. 可选observe块先与正文分离，坏块/未知ID/关闭/无块令本轮observation为空；未闭合块也不能吞掉后续有效dialogue/narration。保持原始raw供阅读副本与诊断，不执行外部实体或HTML。
4. 修改共享raw accessor前复核全部调用：RoleplayConversationSupport、RoleplayTranscriptFormatter、RoleplayOnlineReferenceSupport、RoleplayMessageUiMapper及格式推断。新陪伴的保真不得改变旧线上卡片/长文的既有投影语义。
5. 列表/开场预览/阅读使用同源投影；解析和配置变化不写messages、不增加memoryRevision、不触发提取。角色表现设置不会改人设、空间或世界书深度。
6. D3设置入口复用D2真实导航与角色ID，保存失败保留草稿；200%字体和窄屏要验证保存/取消/错误可操作。已有截图只verify，不提前record或删除门禁。

## 启动时补充

- 读取D2最终接口清单、验证报告和独立检查，而非以本预核对代替前置验收。
- 将D2实际handoff和新规范加入D3 implement/check上下文，再执行task.py validate。
- 记录当时基线；D2释放共享源码与Gradle后，再串行交接D3实施者。

本记录未运行模型、测试、Gradle或设备，也未修改产品源码。
