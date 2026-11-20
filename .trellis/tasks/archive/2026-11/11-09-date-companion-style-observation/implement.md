# D3 正文协议、文风与观测：执行计划

2026-11-09整体方案已获实施批准；本块按下述依赖激活，当前状态见task.json。开始前复核 [PRD](prd.md)、[design](design.md)及真实JSONL上下文。

## 开始条件

- [x] [D2](../11-09-date-companion-entry-context/prd.md) 已通过独立验收并提供对应接口/验证证据，见其research/handoff.md与device-final-validation.json。
- [x] 记录本块实际源码/工作区范围、数据库版本和旧用户差异；research/execution-baseline.json保存1396项源码、274项保护文件、schema 55及开始前工作区；task.py validate通过。
- [x] 主会话仅激活本子任务，派发以Active task:本目录开头的trellis-implement；共享文件/Gradle执行权已交给companion_d3_implement，隔离模拟器与上轮构建进程已关闭。

2026-11-11原生测试独立交接：`d3_native_ui_tests`仅负责新建`CompanionSettingsScreenTest.kt`与`CompanionObservationPanelTest.kt`及内部fixture，完成后交回主实现者；不运行Gradle/formatter。主实现者继续生产/JVM及唯一构建所有权，AndroidTest编译等待两文件释放。

原生助手现已交回15项/2类源码（research/native-ui-test-handoff.md），全部源码/formatter/Gradle重新归主实现者。主会话设备计划为新15项加受影响D2的21项，共36项/6类；见research/device-test-plan.json。当前仅计划，须等独立检查冻结后执行并据实际源码校准数量。

独立检查者companion_d3_check已开始只读预审，仅可写自己的check-readonly.md；作者仍拥有全部源码与构建。正式全范围自修验证在作者最终交接后由主会话转移所有权。

## 有序实施

- [x] 1. 锁定D2请求接口和D1raw持久字段，确认统一raw读取所有消费方。
- [x] 2. 实现受限XML扩展与观察投影，保留旧parser默认行为和纯文fallback。
- [x] 3. 实现版本化ParsedReply/Beat/diagnostics，不回写消息正文或parts投影。
- [x] 4. 补五文风/三人称/模板/细节hint与稳定观测ID的请求快照。
- [x] 5. 完成角色小map的codec/CAS、草稿保存和旧设置/角色更新兼容。
- [x] 6. 补parser/配置/原文往返/历史改名/坏块/原生设置交互回归。
- [x] 7. 2026-11-18必要工程/设备与独立检查通过，固定投影/CAS接口交D4；见research/handoff-2026-11-18.md。旧视觉基准接纳及真实模型效果继续单列。

## 验证命令

下列旧测试选择器来自静态研究；先通过rg确认当前实际名称。新Companion测试在实现后发现并运行，零测试不能算通过。本轮规划未运行这些命令。

```powershell
rg --files app/src/test app/src/androidTest | rg 'Companion|Roleplay|Memory|Voice|Translation'
./gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.roleplay.RoleplayOutputParserTest" --tests "com.example.myapplication.roleplay.RoleplayMessageFormatSupportTest" --tests "com.example.myapplication.roleplay.RoleplayMessageUiMapperTest" --console=plain
./gradlew.bat :app:testDebugUnitTest --tests "*Companion*Test" --console=plain
./gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin lintDebug --console=plain
```

新增CompanionReplyParser/RawContent/Settings/Observation测试；Settings相关真实Compose回调、200%字体、保存失败保稿。用删除parser或改变raw优先级会失败的往返测试，不只校验字符串常量。

构建/截图/设备按 [父验证计划](../11-09-date-companion/implement.md) 与实际风险执行。共享数据/协议变化必须跑相应旧回归；无新失败或修改不反复全量测试。D6负责最终完整JVM/受影响截图/双APK/集成。

## 独立检查与交接

- [ ] trellis-check全范围复核本块需求/实际源码/原始验证报告；相关真实失败修复后定向复验。
- [ ] 本块AC和父矩阵已关联实际证据；未运行模型/实机效果如实标注。
- [ ] 主会话核验源码与证据对应，按真实新合同更新规范/版本记录。
- [ ] 输出下块可使用的接口、限制、实际文件清单和验证命令；不得提前称父任务完成。
- [ ] 提交/基准接纳/归档按本轮最终具体范围和仓库规则，由主会话协调，当前不自动提交。

## 风险与回退

raw与parts优先级、旧格式误判、DataStore整列表覆盖、OBSERVE混入事实为重点。回退保留新配置/原文，暂停新版本生成即可；不能降级覆盖高版本配置。
