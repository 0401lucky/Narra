# D3 独立只读预审

2026-11-11，状态：**PRE_REVIEW_COMPLETE_PENDING_AUTHOR_HANDOFF**。

本检查者已读取完整SubagentStart保存输出，以及未内联的D3 design/implement，核对当前生产接线和新测试源码。作者仍持有全部源码、测试、formatter和Gradle；本检查者只写本报告，没有修改产品/测试、运行Gradle、设备、付费模型或发布操作。以下是当前迭代的预审，不能替代最终全范围自修与工程门禁。

## 新发现

### R1：旧变量残留清理会让新正文守卫拒绝合法短台词

- 位置：`roleplay/companion/CompanionReplyParser.kt:16,66`；`roleplay/RoleplayOutputParser.kt:517,543`；`conversation/CompanionTurnCoordinator.kt:233`；`data/repository/roleplay/RoomCompanionRepository.kt:615`。
- 当前路径：Companion正文分段仍调用旧parser，旧 `stripMarkup → stripVariableUpdateArtifacts` 将单独的new/old/test/add/remove等英文行当作变量残留。
- 具体输入：合法 `<dialogue>new</dialogue>` 派生空beats，新增 `hasReadableBody` 因而返回false，collector/Room把完整模型结果标为INCOMPLETE或拒绝canonical提交。多行有效正文包含此类独立行时，其后的正文也可能被截掉。
- 影响：问题由旧展示清理进入新提交资格判断而扩大；只保留raw不足以保证用户能接受或阅读有效回复。它不同于作者正在处理的“只有observe而没有正文”。
- 建议验证：真实collector与Room对普通短英文台词成功保存；多行正文中部包含new/old/test时后续段落保留；仅observe依然拒绝。采用D3受控正文解析模式，保留旧parser默认兼容。
- 状态：已交主会话，主会话已转作者执行真实红测后修复；检查者尚未运行该回归。

### R2：正文的XML实体解码不完整

- 位置：`roleplay/companion/CompanionReplyParser.kt`的正文委托与 `roleplay/RoleplayOutputParser.kt:153–167`。
- 具体输入：`<dialogue>Tom&apos;s &#x1F642;</dialogue>` 的beat/displayText会保留实体字面量；旧stripMarkup仅解lt/gt/quot/amp。observe字段却已有较完整实体解码，正文与观测行为不一致。
- 影响：新协议中的合法文本无法按本来字符展示，后续D5也会拿到实体控制字面量。raw/span本身保留并不能修复派生正文。
- 建议验证：十进制/十六进制、apos及有效Unicode正常显示；`&amp;#65;`只解一次；编码的尖括号文本不被二次当标签删掉；raw和UTF-16 span保持原始字符串。
- 状态：已交主会话并转作者，与R1受控正文模式一起做回归。检查者没有改代码或运行测试。

### R3：阅读宿主没有消费当前观测显示开关

- 位置：`ui/screen/roleplay/RoleplayReadingMode.kt:236–237`，直接调用 `CompanionObservationPanel(reply)`，默认enabled=true。
- 对照：准备页使用state.observationEnabled，当前场景操作栏使用当前角色配置；阅读页关闭“显示回复观测”后仍可展示旧快照入口。
- 主会话已明确合同：当前角色总开关对准备、会话和阅读均生效；关闭后入口与已展开HUD隐藏。历史ID/label依旧读原快照；重新开启不补造没有观测的旧回复。
- 建议验证：实际ReadingMode宿主消费当前角色开关，关闭时旧入口/弹窗消失；再次开启仍显示旧标签，缺观测的记录保持无入口。仅测Panel的enabled=false不足以证明导航/宿主接线。
- 状态：主会话已交作者。原生测试预计新增真实阅读宿主用例，最终数量以冻结源码为准。

## 已核对的实现边界

- `RoomConversationStore → CompanionSourceStorage.read`核对真实turn/encounter/selected COMMITTED attempt、冻结身份与replyRevision，再附加独立Transient metadata。它与机器来源proof分开；metadata.matches绑定原文hash、身份/关联、revision与完成状态。
- raw accessor对linked消息直接读取content；新TEXT保留原字符串，`textMessagePart`和TEXT规范化当前不会trim/改CRLF。JSON导入依赖Transient排除；新Room往返测试同时核对content/TEXT及不写回的投影。
- RETRY与REGENERATE有独立枚举；旧表现/hint与本次live revision分开，最终HTTP继续走D2来源/模型/配置守卫。
- 新请求测试使用实际DefaultAiGateway、ApiServiceFactory和MockWebServer，三协议不是仅Fake请求对象断言；测试最终结果仍需作者/检查者报告确认。
- 角色复制专用入口已放在同一次DataStore edit内读当前源和当前角色列表，复制配置，保留同期角色/当前选择；源高版本或目标存在时在写入前拒绝。旧默认接口兼容分支与实际Store分开验证。
- 记忆/摘要/同媒介请求已开始消费不含observe的派生正文。最终检查须验证实际来源捕获/提取和HTTP中的语义，不能只以formatter返回值通过替代完整链。
- 高版本整数范围与规范化Saved回执的修复已在当前源码出现，但本预审没有运行其复验，也不以源码存在计通过。
- 长表单固定错误/冲突操作区、SavedState草稿和真实Screen/Panel测试已存在；实际小屏/大字体/IME结果仍待设备。

## 已知作者工作与验证状态

以下不是本检查者新增发现：整数溢出、规范化保存回执、only-observe发布、observe进入语义原料、角色复制原子性。这些已由作者处理或编写回归，最终需逐项核对红/绿证据。

作者最初生产编译曾通过。主会话最新交接指出后续全组在2GiB生产编译阶段OOM，尚未进入该组测试，进程已退出；作者将仅按命令参数使用3GiB，不改项目配置。本报告不把该次OOM算成测试失败或通过。

初始独立原生源码为设置9项+观测6项；增加阅读宿主测试后预计16项，再加受影响D2的21项约37项。最终check须从实际源码和测试APK核对数量，不沿用计划估值。设备由主会话在最终冻结后执行。

## 最终检查待办

1. 作者完成并释放源码/Gradle后，主会话明确转交；重新核对最终文件范围、源码/保护hash和全部handoff，不把本预审当final pass。
2. 验证R1/R2/R3和已知作者红测均已修复，检查真实原始XML、最终三协议HTTP、Room/DataStore状态及全部必要后续断言。
3. 核对同媒介/跨媒介历史、来源捕获、提取/摘要里的正文与kind，不让显示observe被重新消费，也不把派生偏移写成raw span。
4. 核对旧线上JSON、长文、群私有、typed control和D1/D2恢复资格；仅运行受影响回归，不重复无变化的整库测试。
5. 执行所需Lint/type-check/定向测试、实际受影响截图verify和双APK；不record、不改版本或旧schema。
6. 输出最终check报告、准确唯一用例统计、源码/APK hash和执行权释放，供主会话做隔离设备验收。

只读检查时，D3基线1396项source，**274项受保护schema/截图/参考文件逐字节无漂移**。未验证最终生产源码冻结，未执行Lint/TypeCheck/测试；没有本检查者创建的构建或设备进程。
