# Research: 陪伴配置作用域与协议细化

- Query: 为已确定“升级现有线下扮演、完整规划”的 2.4.1 推荐最小兼容代价的配置、协议和演出投影契约。
- Scope: internal；只研究现有 Kotlin/Room/DataStore 与指定手册，不改产品或父设计，不运行测试。
- Date: 2026-11-09。
- 以下新类型/字段名为技术建议，尚未在代码中实现；具体 schema 与其余场次/可靠回合设计由主会话统一。

## Findings

### 1. 已有契约

| 领域 | 源码事实与锚点 | 影响 |
| --- | --- | --- |
| 角色主档案 | `app/src/main/java/com/example/myapplication/model/Assistant.kt:5` 含人设、greeting、头像、世界书、记忆、采样参数和预设；没有陪伴 style/pov/图集字段 | 不再建立一份“陪伴角色”副本 |
| 角色存储 | `data/local/AppSettingsStore.kt:363` 解码 assistantsJson；`:709` 在 DataStore edit 内整列表保存；`viewmodel/SettingsAssistantCoordinator.kt:32` 用传入 AppSettings 快照构造整列表 | 把新偏好无保护塞入旧整角色更新，可能被旧编辑页快照覆盖；需字段级写入边界 |
| 场景与全局 | `model/RoleplayScenario.kt:5` 持有背景/单图/模式/身份；`model/AppSettings.kt:19` 持有全局帮写、行高、沉浸/高对比度、13种气泡皮肤 | 角色偏好、场景覆盖和全局可访问性分开 |
| 三种输出 | `roleplay/RoleplayMessageFormatSupport.kt:47`：线下对白为 PROTOCOL/PLAIN、长文为 LONGFORM、线上为 PROTOCOL；PROTOCOL 并不特指一种语法 | 新陪伴必须保留消息自己的媒介与格式，不能只看当前场景模式 |
| 线下 XML | `roleplay/RoleplayOutputParser.kt:6`、`:19`、`:122`：narration/thought/dialogue → RoleplayParsedSegment；dialogue 有 emotion/引用属性 | 可扩展有默认值的表现元数据，无需重写正文格式 |
| 长文/线上 | `roleplay/RoleplayLongformMarkupParser.kt:59`：char/thought 及有限段落标记；`roleplay/OnlineActionProtocolParser.kt:35`：线上 JSON/动作解析成 parts/directives | 长文仍走原 parser；陪伴不能误触发线上工具或把 JSON 动作当 VN 拍 |
| typed parts | `model/ChatMessagePart.kt:6`、`:124`：TEXT/IMAGE/FILE/ACTION/SPECIAL/STATUS，没有逐拍内容种类 | parts 是消息/媒体/动作表示，不应为每拍造新消息或滥用 ACTION |
| 原文优先级 | `roleplay/RoleplayMessageFormatSupport.kt:118`：当前先取 parts.toPlainText()，为空才取 content | 只在 content 保存 raw、同时在 TEXT part 写去标签投影会破坏后续原文读取，必须明确统一 accessor |
| 持久消息 | `data/local/chat/MessageEntity.kt:24` 存 content/partsJson、输出格式/媒介、memoryRevision；`data/local/RoomConversationStore.kt:263`、`:295` 做映射 | 可复用消息 ID/修订作为投影源，不用另造原文数据库 |
| 修订含义 | `data/local/memory/MemorySourceStorage.kt:174` 的 sameSource 比较正文、parts、状态、格式等 | 把派生 beat/HUD 写回正文或 parts 会额外触发来源修订，必须避免 |

除第一行完整路径外，本文件缩短源码路径均相对 `app/src/main/java/com/example/myapplication/`。

### 2. 配置推荐：现有设置服务中的角色扩展，场景只保留明确覆盖

推荐在现有 `AppSettingsStore` 增加版本化的小型 `companionCharacterSettingsJson` 映射，以真实 `assistantId` 为键；通过已有 settingsFlow/AiSettingsEditor 装配消费。它只保存陪伴偏好与资源引用，不保存人设、记忆内容或第二份角色列表，也不另建全局状态单例。

| 配置层 | 推荐内容 | 保存/生效规则 |
| --- | --- | --- |
| 全局 AppSettings | 继续使用既有主题、无障碍、行高、系统栏及气泡配置 | 原字段保持；没有角色覆盖时继承，不因升级重置 |
| CompanionCharacterSettings | schemaVersion、revision；五文风、三显式人称、自定义补充/模板；观测；表情套装；默认阅读/演出偏好 | 按 assistantId 存小型扩展；不改变 Assistant.systemPrompt、默认Preset、记忆或其它入口 |
| 现有 Scenario / 可选 companion overrides | 原背景/单图与身份/记忆用途保持；需要场景专属时仅存明确选过的背景、套装或布局覆盖 | 有覆盖才优先；在陪伴布置显式选择新套装后才替换旧场景单图，不自动把各场景图片合成一个共享角色皮肤 |
| 回合快照 | 有效配置 revision、文风/人称、字段定义快照、protocolVersion、可复现 hint | 创建请求时快照至可靠回合；中途改设置只影响下一请求，失败补保存/重试不重新抽样 |
| 临时播放 | beatIndex、打字机、overlay、音频代次 | 由场次所有者维护；不写角色设置；如保书签，只存消息/修订/拍索引 |

推荐字段组：

- `style`：CINEMATIC / PLAIN / LITERARY / LIGHT_HUMOR / INTENSE；`pov`：THIRD_NAME / THIRD_YOU / FIRST_YOU。允许未配置（inherit/null），保持升级前自定义预设；显式选择后仅控制叙述方式，不复制风格模板到人物身份。
- `extraStyle`、`extraPresets[{id,name,text}]` 与 `detailHintsEnabled`；删除模板不清空已应用文本；本轮选中的 hint 写入请求快照。
- `observe={enabled=false,style,fields,customFields}`；内建 fieldId 固定为 time/place/state/detail，label 可改；自定义 fieldId 由程序生成，label 不作键，最多6个，重名在编辑端阻止；按 ID 输出/解析。
- `spriteSets[{id,name,imagesByExpressionKey}]`、`activeSetId?`、`customExpressions[{key,label}]`、布局 scale/x/y；图集只保存稳定 URI/本地文件引用，不存 base64、Bitmap 或临时 object URL。表达键在输入、prompt、parser 使用同一校验与保留字集合。
- `display={preferredView?,readingAppearance?,showAvatars?,typewriterEnabled?,typewriterSpeed?,layout?}`；阅读外观与生成文风分离。null 继承原显示偏好，不把旧线下突然切成新播放器；明确选择演出后才记住偏好。
- 语音配置只引用既有音色/服务配置 ID 与播放偏好，密钥仍由现有安全存储持有；完整声音契约见声音主题。

写入建议：为整个新配置页使用“本地草稿→保存”的明确事务边界；未保存可取消，关闭/离开有未保存选择。若保留少量即时操作，界面必须逐项标明，不出现撤销不了的统一取消。不要照搬参考仓库混合 blur/即时保存。

实现 `updateCompanionSettings(assistantId, expectedRevision, patch)`：在同一次 DataStore edit 内读当前映射，确认角色仍存在或为内建角色、校验 revision、仅修改该角色字段、递增 revision；旧角色整列表保存不碰这个独立 key。图像 IO 完成后提交引用仍使用所见 revision，失败保稿。删除角色须在同一设置编辑中移除该映射，并在提交后按引用关系清理资源，不能靠先后两个无保护 save 凑原子性。

JSON codec 应逐字段保留合法值；缺失/null/未知枚举回退对应默认，不清空整份配置。更高 schemaVersion 保留原始值且禁止无声降级覆写。角色复制/资料导入的 ID 重映射只复制偏好与合法资产引用，旧资料无该字段时保留已有目标配置，不能视为用户清空。

### 3. 输出协议推荐：扩展现有 XML，保持长文与线上独立

**推荐复用线下 XML 主结构，不新增方括号逐行 VN 主协议。** 已有 prompt、纯文本容错、历史格式推断与原生阅读覆盖 XML；新增第四套语法会扩大兼容面。已有 XML 分段足以投影成 beat，`RoleplayOutputFormat.PROTOCOL` 可以保留，在陪伴消息元数据中显式标记 `companionProtocolVersion=1`，而不是改所有旧消息枚举。

建议的受限扩展（示意，不是已实现协议）：

```xml
<observe version="1"><field id="time">傍晚</field><field id="obs_c42">蓝色外套</field></observe>
<narration sprite="normal">他把椅子拉近一点，停下等你开口。</narration>
<dialogue speaker="角色" emotion="放松" sprite="happy" voice_emotion="calm">想在这里坐一会儿吗？</dialogue>
```

- narration/thought/dialogue 保留原语义；在现有 parser 的有版本上下文中扩展可选 sprite/voiceEmotions 与 raw span，默认空值保证旧调用方不变。视觉键、现有自由文本 emotion、受限 voice_emotion 分通道，不能互相猜填。
- 首先在陪伴 adapter 中提取受限 observe 块，再调用已有正文 parser。不要先交当前状态块清洗器，也不能把新观测塞进当前会被 UI 丢弃的 STATUS parts。
- observe 只接受已声明的固定/自定义 ID；来源使用请求配置快照。缺失、坏格式、关总开关时本轮 observation=null，并保留原文及诊断。旧值不能冒充本轮值；改名或删项后的历史显示使用旧字段定义快照。
- 允许无标签/未知属性/旧 XML/纯文本兜底；坏观测只使面板失效，不能抹掉有效正文。所有输出都保留原始字符串，不能用正则“清理”覆盖权威原文。
- ONLINE_PHONE 仍走原 JSON/action 协议；OFFLINE_LONGFORM 仍走 char/thought/有限段落标记。普通阅读投影可统一，但不得为了演出把旧原文批量重写成陪伴 XML。
- 新属性取值验证、内容和属性转义、可选块长度限制在客户端执行；这是本地解析规则，不引入 WebView、HTML 执行或 XML 外部实体解析。
- 参考方括号线格式如确需接受导入，可作为兼容解析器输入，不能仅因正文出现“[happy]”就改变已存旧消息的格式判定。

### 4. 原文、解析投影、beat 与配置的版本关系

1. **权威原文**：已提交消息 ID + `memoryRevision` + 未损失的正文/附件。陪伴普通文字可令 content 与唯一 TEXT part 均保留同一完整协议原文；更稳的专用 raw accessor 根据 companion 元数据读取已存 raw，绝不优先取展示去标签文本。用一条完整 assistant 消息保存一轮，而非按拍落消息。
2. **生成配置快照**：保存本轮 protocolVersion、有效角色/场景配置 revision、观测字段 ID/label 定义与可复现参数；重播不调模型，重生成建立新候选，提交后才递增源修订。
3. **解析投影**：`(sourceMessageId, sourceRevision, protocolVersion, parserVersion)` 唯一键；产物为 typed segments、observation 与 diagnostics，必要时缓存。缓存过期可免费重算，不修改原文或触发记忆提取。
4. **Beat**：由统一 segments 生成稳定 beatId（源ID/修订/parserVersion/segmentIndex及必要raw span），包含文字、种类、spriteKey、voiceEmotion、sourceSpan。UI 状态引用 beatId/索引，不当作独立记忆来源。
5. **视觉布局版本**：更换套装、背景、阅读配色或缩放只改变资源解析/呈现，不增加 sourceRevision、消息数或文本调用；台词文本改变才需要使旧音频缓存和播放书签失效。
6. **编辑/删除**：原文修订后令旧投影/观测/书签失效，收到旧版本播放/异步解析结果时拒绝发布；当前修订不存在的 beat 不落回索引0去播放旧文本。
7. **旧单图/设置**：未选择套装时按当前 scenario 单图→角色头像→名称兜底显示；已有图片不迁移/复制二进制。选择新套装后按目标 expression→该套默认→旧图/头像的确定规则兜底，不沿用上轮其它表情作为“匹配成功”。
8. **跨显示模式**：同一消息原文只有一套权威解析版本，列表/阅读/演出/语音从同源投影派生；UI 窗口和 beat 状态不决定本轮模型上下文范围。

## Caveats / Not Found

- 新配置映射、陪伴 XML 元数据和 beat 类型是推荐契约，当前代码均未具备，不应写成“已支持”。
- 本研究选择技术方向，无需再就字段或格式问用户；最终字段落点与可靠回合/导入资料设计由主会话统一，尤其不能声称 DataStore 与 Room 可共享同一数据库事务。
- 相关规范：`.trellis/spec/backend/database-guidelines.md`（小配置/大资源分离、枚举兼容）、`ui-state-guidelines.md`（状态所有者/异步资源）、`roleplay-theme-settings.md`（缺字段保留原值与明确保存），以及指定手册 §6–11。
- 只阅读静态代码，没有运行样例、迁移、模型或 UI 测试。
