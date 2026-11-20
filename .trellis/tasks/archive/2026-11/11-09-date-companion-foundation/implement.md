# D1 场次与可靠回合基础：执行计划

2026-11-09整体方案已获实施批准；本块按下述依赖激活，当前状态见task.json。开始前复核 [PRD](prd.md)、[design](design.md)及真实JSONL上下文。

## 开始条件

- [x] 父任务最新完整规划已获后续实施批准。
- [x] 记录本块实际源码/工作区范围、数据库版本和旧用户差异；上下文路径有效，task.py validate通过。
- [x] 主会话仅激活本子任务，派发以Active task:本目录开头的trellis-implement；共享文件/Gradle执行权已交接。

## 有序实施

- [x] 1. 核对最新schema、旧消息mapper和所有来源/摘要消费入口；冻结D1范围及v2.4基线。
- [x] 2. 添加附属表/link/资格、约束与固定旧库迁移；验证旧消息默认值和未知关联保守行为。
- [x] 3. 实现逻辑turn与attempt claim/state/hash、RESULT_READY持久产物及唯一回执。
- [x] 4. 补主流CompletionEvidence和新collector，保持旧事件调用方行为。
- [x] 5. 实现canonical CAS、输入资格提升、CONTROL_NOOP覆盖和人工修订/删除的失效。
- [x] 6. 用Fake/MockWebServer及文件型Room注入各断点；验证旧入口与兼容引擎来源。
- [x] 7. 完成独立代码检查、相关单测、Lint/编译/迁移设备验证，记录真实证据并交D2。

## 验证命令

下列旧测试选择器来自静态研究；先通过rg确认当前实际名称。新Companion测试在实现后发现并运行，零测试不能算通过。本轮规划未运行这些命令。

```powershell
rg --files app/src/test app/src/androidTest | rg 'Companion|Roleplay|Memory|Voice|Translation'
./gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.roleplay.RoleplayRoundTripSupportTest" --tests "com.example.myapplication.viewmodel.RoleplayViewModelTest" --tests "com.example.myapplication.viewmodel.RoleplayDurableMemoryFlowTest" --console=plain
./gradlew.bat :app:testDebugUnitTest --tests "*Companion*Test" --console=plain
./gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin lintDebug --console=plain
```

新增CompanionRepository/Turn/SourceEligibility/CompletionEvidence等测试（名称由实现落定）；真实Room固定旧库升级、两个owner claim、文件库关闭重开、CONTROL_NOOP连续覆盖。高风险共享来源改动须包含v2.4记忆相关联合回归。

构建/截图/设备按 [父验证计划](../11-09-date-companion/implement.md) 与实际风险执行。共享数据/协议变化必须跑相应旧回归；无新失败或修改不反复全量测试。D6负责最终完整JVM/受影响截图/双APK/集成。

## 独立检查与交接

- [x] trellis-check全范围复核本块需求/实际源码/原始验证报告；相关真实失败修复后定向复验。
- [x] 本块AC和父矩阵已关联实际证据；未运行模型/实机效果如实标注。
- [x] 主会话核验源码与证据对应，按真实新合同更新规范/版本记录。
- [x] 输出下块可使用的接口、限制、实际文件清单和验证命令；不得提前称父任务完成。
- [ ] 提交/基准接纳/归档按本轮最终具体范围和仓库规则，由主会话协调，当前不自动提交。

2026-11-10工程验收通过；提交归档留到完整版本收尾，不阻塞D2。见 [交接记录](research/handoff.md)。

## 风险与回退

schema、消息映射、sameSource/validSource/摘要资格与主流事件为高风险。回退不降schema、不删除附属行；可停用新调用，但保留linked读取/保守资格。D1未验收前不得接新生产入口。
