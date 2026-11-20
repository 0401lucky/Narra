# D2 三种开场与上下文衔接：技术设计

以 [本块PRD](prd.md) 与 [父设计](../11-09-date-companion/design.md) 为权威；本文件描述待实现合同，当前没有产品改动。

## 依赖输入

[D1](../11-09-date-companion-foundation/prd.md) 已通过独立验收并提供对应接口/验证证据。接口差异先回到父设计协调，不在本块建立平行数据或备用状态单例。

## 数据流与合同

依赖D1已通过的场次/turn/attempt/资格。新导航意图只消费一次，入口状态为prepare/preview/generating/accepting/active；draft产物不算经历。AllowedCompanionHistoryScope保存允许来源ID/修订、角色/persona/space/run与截止，当前pending INPUT仅供本turn读取；先处理真实输入再装饰。复用PromptContextAssembler/GatewayRequestMessageBuilder/RoleplayOnlineReferenceSupport，增加局部陪伴模式策略；D3后续扩展表现协议，不复制另一套网关。D2可先建立陪伴设置映射中的时区/基础版本接口，D3串行补完配置UI。

## 写入与协调范围

以下源码路径相对app/src/main/java/com/example/myapplication；新类型/目录是计划位置，已有类的详细行号见父research。

- RoleplayNavGraph/AppRoutes、ImmersivePhoneShell现有单角色线下回调与准备/预览页面。
- RoleplayScenarioActionSupport/RoleplaySendActionSupport及新RoleplayCompanion入口协调器。
- PromptContextAssembler、RoleplayPromptDecorator、RoleplayOnlineReferenceSupport及受控历史聚合器。
- RoleplayTimeAwarenessSupport、陪伴时区/有效配置基础接口；AppGraph与请求回执接线。

你不是唯一在代码库工作的执行者。实施时核对最新差异、保留用户与其它任务改动，所有跨块文件在明确交接后串行写入；不回滚或覆盖他人文件。

## 兼容与失败策略

新场不调用restartCurrentSession，不改变run或memoryPurpose；旧greeting与旧“继续会话”保持。群/线上动作/剧情长文不走新单角色模式；已存新control切回线上仍按语义投影，不变用户台词。

共享上下文和入口容易扩大范围，必须从ROLEPLAY_GRAPH真实路径验证；空scope不fallback。可停用新场入口回退，但保留已存场次/原文及阅读。

## 验证设计

新增CompanionEntry/HistoryScope/Prompt/TimePolicy测试；MockWebServer抓最终请求，不仅检查中间prompt。实际Compose入口、开场保存失败、返回原会话/重组与输入法。角色表达真实模型情境单列，不以Fake冒充效果。

每个本块AC都需绑定行为证据；请求计数、源ID/修订、仓储结果、实际UI回调和错误/取消路径优先于实现镜像断言。遵循父计划的合成数据、隔离库及真实效果单独标注规则。

## 上下游交付

输出本块实际接口/文件范围、错误/恢复约定和验证结果；更新本块research中的implementation/check证据供下一块引用。公共contract发生实质变化时同步父/相关子设计，不只通过代理消息约定。
