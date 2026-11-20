# D2 三种开场与上下文衔接：执行计划

2026-11-09整体方案已获实施批准；本块按下述依赖激活，当前状态见task.json。开始前复核 [PRD](prd.md)、[design](design.md)及真实JSONL上下文。

## 开始条件

- [x] [D1](../11-09-date-companion-foundation/prd.md) 已通过独立验收并提供对应接口/验证证据；见其research/handoff.md与device-final-validation.json。
- [x] 记录本块实际源码/工作区范围、数据库版本和旧用户差异；上下文路径有效，task.py validate通过。最终范围见research/check-files.json，源码与schema 55验证见research/device-final-validation.json。
- [x] 主会话仅激活本子任务，派发以Active task:本目录开头的trellis-implement；共享文件/Gradle执行权已交接给companion_implement。

## 有序实施

- [x] 1. 确认D1证据/接口；区分四类入口动作，补导航一次性意图与准确return origin。
- [x] 2. 实现三开场预览/接受/直接开始，显式控制greeting seed与离开取消。
- [x] 3. 接INPUT/CONTINUE和当前最新答reroll，复用D1保存/错误恢复，不用旧清空回退函数。
- [x] 4. 实现授权scope及同角色/persona日常原文聚合、截止/空范围和来源复核。
- [x] 5. 统一每请求recall/世界书与媒介转译，分离事实、互动规则和表现注入。
- [x] 6. 接时间开关/ZoneId基础配置并补真实HTTP、失败竞态与导航测试。
- [x] 7. 独立检查相关单测、Lint/编译/必要原生导航，交D3协议/配置稳定入口。

## 验证命令

下列旧测试选择器来自静态研究；先通过rg确认当前实际名称。新Companion测试在实现后发现并运行，零测试不能算通过。本轮规划未运行这些命令。

```powershell
rg --files app/src/test app/src/androidTest | rg 'Companion|Roleplay|Memory|Voice|Translation'
./gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.viewmodel.RoleplayViewModelTest" --tests "com.example.myapplication.context.PromptContextAssemblerTest" --tests "com.example.myapplication.data.repository.ai.GatewayRequestMessageBuilderTest" --tests "com.example.myapplication.data.repository.roleplay.RoleplayRunRepositoryTest" --console=plain
./gradlew.bat :app:testDebugUnitTest --tests "*Companion*Test" --console=plain
./gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin lintDebug --console=plain
```

新增CompanionEntry/HistoryScope/Prompt/TimePolicy测试；MockWebServer抓最终请求，不仅检查中间prompt。实际Compose入口、开场保存失败、返回原会话/重组与输入法。角色表达真实模型情境单列，不以Fake冒充效果。

构建/截图/设备按 [父验证计划](../11-09-date-companion/implement.md) 与实际风险执行。共享数据/协议变化必须跑相应旧回归；无新失败或修改不反复全量测试。D6负责最终完整JVM/受影响截图/双APK/集成。

## 独立检查与交接

- [x] trellis-check全范围复核本块需求/实际源码/原始验证报告；相关真实失败修复后定向复验。
- [x] 本块AC和父矩阵已关联实际证据；未运行模型/实机效果如实标注。
- [x] 主会话核验源码与证据对应，按真实新合同更新规范/版本记录。
- [x] 输出下块可使用的接口、限制、实际文件清单和验证命令；不得提前称父任务完成。
- [ ] 提交/基准接纳/归档按本轮最终具体范围和仓库规则，由主会话协调，当前不自动提交。

## 风险与回退

共享上下文和入口容易扩大范围，必须从ROLEPLAY_GRAPH真实路径验证；空scope不fallback。可停用新场入口回退，但保留已存场次/原文及阅读。
