# D3 实施中记录

2026-11-11，D3实施者维护。此文件记录续接点，最终验收以implementation-handoff / validation和独立检查为准。

## 已接入的生产链

- CompanionCharacterSettings.writing、独立codec与角色DataStore CAS；保存回执按实际编码后值返回，保留同ID未知扩展。
- descriptor.presentation与live settingsRevision分离；明确RETRY复用原表现/hint，REGENERATE冻结当前选择。
- Room读取时校验selected COMMITTED attempt并附加Transient metadata；绑定源ID/修订/raw hash/角色/关联/格式，JSON导入不恢复此字段。
- 同源XML/observe parser/projector、源span与typed beat，准备/列表/阅读及语义消费者接线。canonical content和TEXT保真，投影不回写消息。
- 实际“陪伴表达”路由、草稿/保存/取消/模板/稳定观测ID与固定错误反馈；观测只读当前源快照。
- 角色复制通过saveAssistantsWithCompanionCopy在一次DataStore edit中读取当前源角色和小配置，保留同期角色/选择，目标冲突或未知源schema整体拒绝。

## 实际验证

- `implementation-compile-02.log`：首次生产Kotlin编译通过。第01次只因PowerShell未引用带点的-P参数而失败，未进入源码编译。
- `implementation-core-red-results/`：28项已执行，25通过、3失败；失败为版本Int溢出、规范化Saved回执与实际读取不一致、仅observe仍可提交。三处生产修正已写入，后续通过状态仍须复验。
- 原生助手已交回两类15项测试，未编译/未执行，见native-ui-test-handoff.md。后续将增加ReadingMode当前显示gate回归，预期数量需按最终源码重新计算。
- `implementation-jvm-01.log`在生产编译阶段遇到2GiB heap OOM，未进入该组JVM运行，不能计为测试结果。后续只用命令级3GiB覆写，不改gradle.properties。

## 当前待处理

独立只读预审指出旧parser的裸new/old/test残留规则与不完整实体解码会损坏合法正文。已补真实collector/Room及实体回归，正以冻结源码运行`implementation-preservation-red-01`。该组结束前不改产品源码；之后新增受控保正文模式，旧parser默认兼容保持。明确结构化控制/JSONPatch与observe-only仍应拒绝成为正文。

主会话已明确：当前角色关闭观测时，准备、会话及阅读宿主都隐藏入口/弹窗；历史标签始终取原快照，重新开启不补造原来没有的观测。ReadingMode的显式gate尚待接线。

新增三协议HTTP、真实Room/DataStore、配置草稿恢复和现有角色/解析兼容测试已写入。下一步完成上述修正后，运行完整相关作者验证、统一IDE格式化、受影响截图verify、Lint/全源集编译与双APK。所有现有schema、旧截图、版本/更新参数保持；未调用真实模型/TTS，设备由主会话执行。
