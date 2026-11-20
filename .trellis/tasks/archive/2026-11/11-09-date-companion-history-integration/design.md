# D6 历史导出与集成交付：技术设计

以 [本块PRD](prd.md) 与 [父设计](../11-09-date-companion/design.md) 为权威。
本块已经进入实施与检查；实际接口和错误矩阵见
[历史与资料规范](../../../../spec/backend/companion-history-transfer-guidelines.md)，
实施/修复范围见 research 各交接，运行结果以 validation 原始证据为准。

2026-11-20工程/原生集成门禁已通过，见 [最终交接](research/handoff.md)：
JVM最新2956/2956、原生9/9、52项工程证据齐全。公开白名单/私人v3、实际Android
目录发布及legacy文件恢复、Source owner/SAF/IME均有回归；视觉接纳和真实效果单列。

## 依赖输入

[D1](../11-09-date-companion-foundation/prd.md)、[D2](../11-09-date-companion-entry-context/prd.md)、[D3](../11-09-date-companion-style-observation/prd.md)、[D4](../11-09-date-companion-stage-reading/prd.md)、[D5](../11-09-date-companion-voice/prd.md) 已通过独立验收并提供对应接口/验证证据。接口差异先回到父设计协调，不在本块建立平行数据或备用状态单例。

## 数据流与合同

父设计§9–10为权威。D6消费D1稳定事务、D2AllowedHistory、D3raw/配置、D4reader/素材、D5资源/收藏，不能重新造历史或记忆库。历史可读范围与模型scope分离，普通回看不重绑旧运行。TXT是阅读副本；装扮包仅本地白名单，私人ContextDataBundle只扩其已有承载域，不暗中升级为全聊天/音频备份。导入计划冻结目标设置revision及素材hash，Room成功后的设置失败用回执恢复，失败不重做已提交阶段。

## 写入与协调范围

以下源码路径相对app/src/main/java/com/example/myapplication；新类型/目录是计划位置，已有类的详细行号见父research。

- 陪伴历史/日期管理ViewModel/Screen与查询扩展，D1原文编辑/删除API的UI接线。
- SAF TXT/本地装扮codec、ContextDataBundle/ContextTransferViewModel及既有导入导出协调。
- 跨模块integration测试、迁移/旧功能回归及矩阵证据索引。
- 主会话负责规范/版本文档/任务树/具体提交清单；实施代理不擅自提交或发布。

你不是唯一在代码库工作的执行者。实施时核对最新差异、保留用户与其它任务改动，所有跨块文件在明确交接后串行写入；不回滚或覆盖他人文件。

## 兼容与失败策略

不把旧导出Chat页面当RP已接入口，不用模型摘要Formatter截断原文。新装扮文件拒绝夹带任意路径/大资源，私人资料缺域保持旧值；旧模型未知enum/link保守，源关联不能被剥掉变legacy。

范围遗漏、跨存储假原子、原文导出不全、旧任务/截图夹带是收尾风险。任何失败都保留已有原文/配置/独立资源，不通过删用例/重录基准/降库绕过。

## 验证设计

新增CompanionHistory/Export/AppearanceTransfer/Integration与真实Room导入测试；最终无selector完整JVM，受影响H/E04/E05等截图非零verify，旧schema升级到最终版及IME/系统栏/音频隔离设备故事。记录真实模型/实机缺口。

每个本块AC都需绑定行为证据；请求计数、源ID/修订、仓储结果、实际UI回调和错误/取消路径优先于实现镜像断言。遵循父计划的合成数据、隔离库及真实效果单独标注规则。

## 上下游交付

输出本块实际接口/文件范围、错误/恢复约定和验证结果；更新本块research中的implementation/check证据供下一块引用。公共contract发生实质变化时同步父/相关子设计，不只通过代理消息约定。
