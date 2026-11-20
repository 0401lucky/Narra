# D3 正文协议、文风与观测：技术设计

以 [本块PRD](prd.md) 与 [父设计](../11-09-date-companion/design.md) 为权威；本文件描述待实现合同，当前没有产品改动。

## 依赖输入

[D2](../11-09-date-companion-entry-context/prd.md) 已通过独立验收并提供对应接口/验证证据。接口差异先回到父设计协调，不在本块建立平行数据或备用状态单例。

## 数据流与合同

父设计§6固定XML扩展方向，避免第四套VN主语法。observe受限解析先分离，未知/坏块返回null+diagnostics而保raw。ParsedCompanionReply按源ID/memoryRevision/protocolVersion/parserVersion建键；Beat是typed projection不是消息。设置updateCompanionSettings在DataStore edit内CAS，旧角色整列表key独立；优先级场景显式覆盖>角色>原全局。D2的时间基础接口在此统一纳入角色配置，不另造第二份映射。配置IO不是Room原子事务。

## 请求表现快照与发送守卫

2026-11-11基于D2已验收接口细化：descriptor的`settingsRevision`记录新attempt创建时的live revision，继续参与最后发送校验。新增`presentation`记录实际文风/人称/补充/单次detailHint/观测ID与label，并用`sourceSettingsRevision`保留这些值来自哪个设置版本。

显式失败恢复复用原presentation；用户主动重新生成才建立新presentation。产品动作须明确传递，不能将`allowUncertainRetry`误当重试/重生成分类。两种操作均沿用D2授权来源重冻和时间策略，准备与实际HTTP之间的设置/来源变化仍可拒绝请求。测试须抓最终HTTP，证明改设置后的重试保留原表现、新重生成使用新表现、随后再变设置仍被最后守卫阻止。

投影从真实turn/selected attempt读取历史快照。旧descriptor缺presentation时保留可读raw并按默认无观测解释，不以当前设置补填旧字段名；解析结果同时绑定当前原文ID/修订，不能将旧快照里的旧正文当作当前原文。

观测总开关同时约束生成和当前显示：关闭时准备页、会话、阅读均隐藏观测入口并关闭展开层；历史字段定义仍从原快照读取。重新打开只恢复原本存在的观测，不补造历史。跨场阅读按来源角色的当前显示偏好处理，不能遗漏阅读宿主或以当前标签覆盖旧标签。

### 读取时元数据与纯投影

`RoomConversationStore`在既有消息读取时验证turn、encounter与selected COMMITTED attempt，附加独立的`@Transient CompanionReplyMetadata`，保存实际协议、presentation及绑定信息。它不属于机器来源proof，也不进入持久消息JSON或外部导入。有效性核对当前消息ID、revision、raw hash、conversation/turn及角色/格式；copy改变关联或正文后不能沿用旧观测。

纯`CompanionReplyProjector`接受任意经仓储读取的消息或列表，供预览、列表、阅读及后续D4/D5使用，不绑定当前场次或窗口。元数据缺失/失效保留raw与诊断并停用观测，不能读取当前角色配置补造历史。新正文提交的content/TEXT保留同一raw；投影不写库、不增加源修订、不请求模型。

记忆/摘要等语义消费者应排除已识别的observe展示内容，同时保留canonical与v2.4原料快照、hash/修订及原始span。去块后的索引不能冒充原文索引；该适配在消费格式化边界完成，不将裁剪文本回写来源。

### 角色复制的原子边界

现有SettingsStore/AiSettingsEditor增加复制专用`saveAssistantsWithCompanionCopy(assistants, selectedAssistantId, sourceAssistantId, targetAssistantId)`，旧实现默认委托原saveAssistants保持兼容。生产AppSettingsStore在同一次DataStore edit读取当前源角色、现有角色列表与companion JSON，只合入本次目标角色及复制配置，目标revision从0开始。陈旧assistants列表不能覆盖或删除同期角色；不切换角色的原动作保留当前选择。

源缺失/不支持配置版本或目标已存在时整次拒绝，不能先创建角色再尝试复制偏好。SettingsAssistantCoordinator的复制动作接此入口，真实DataStore回归验证源资料/配置一致、同期变更保留与无半份角色。

## 写入与协调范围

以下源码路径相对app/src/main/java/com/example/myapplication；新类型/目录是计划位置，已有类的详细行号见父research。

- RoleplayOutputParser、RoleplayMessageFormatSupport及新的Companion parser/raw/beat协议类型。
- RoleplayPromptDecorator中受控陪伴表现注入与文风/人称/观测模板。
- AppSettings/AppSettingsStore/AiSettingsEditor的陪伴小map/codec/CAS及新设置面板。
- 角色删除/复制的伴随配置边界（与现有所有者协调）；解析/配置/投影测试。

你不是唯一在代码库工作的执行者。实施时核对最新差异、保留用户与其它任务改动，所有跨块文件在明确交接后串行写入；不回滚或覆盖他人文件。

## 兼容与失败策略

PROTOCOL枚举不批量改旧行，协议版本从新turn快照识别；旧JSON动作和长文内容不改写。不把STATUS parts或Flow Observation当新HUD，不用去方括号正则清理所有文字。

raw与parts优先级、旧格式误判、DataStore整列表覆盖、OBSERVE混入事实为重点。回退保留新配置/原文，暂停新版本生成即可；不能降级覆盖高版本配置。

## 验证设计

新增CompanionReplyParser/RawContent/Settings/Observation测试；Settings相关真实Compose回调、200%字体、保存失败保稿。用删除parser或改变raw优先级会失败的往返测试，不只校验字符串常量。

每个本块AC都需绑定行为证据；请求计数、源ID/修订、仓储结果、实际UI回调和错误/取消路径优先于实现镜像断言。遵循父计划的合成数据、隔离库及真实效果单独标注规则。

## 上下游交付

输出本块实际接口/文件范围、错误/恢复约定和验证结果；更新本块research中的implementation/check证据供下一块引用。公共contract发生实质变化时同步父/相关子设计，不只通过代理消息约定。
