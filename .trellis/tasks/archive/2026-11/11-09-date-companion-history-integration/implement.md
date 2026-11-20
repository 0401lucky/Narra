# D6 历史导出与集成交付：执行计划

2026-11-09整体方案已获实施批准；本块按下述依赖激活，当前状态见task.json。开始前复核 [PRD](prd.md)、[design](design.md)及真实JSONL上下文。

## 开始条件

- [x] [D1](../11-09-date-companion-foundation/prd.md)、[D2](../11-09-date-companion-entry-context/prd.md)、[D3](../11-09-date-companion-style-observation/prd.md)、[D4](../11-09-date-companion-stage-reading/prd.md)、[D5](../11-09-date-companion-voice/prd.md) 已通过独立工程验收并提供对应接口/验证证据；真实效果与视觉接纳单列，见 research/prerequisite-2026-11-19.md。
- [x] 记录本块实际源码/工作区范围、数据库版本56和旧用户差异；上下文路径有效，task.py validate通过。
- [x] 主会话仅激活本子任务，派发以Active task:本目录开头的三名原生trellis-implement；文件所有权与主会话唯一Gradle/formatter/设备执行权已交接，见 research/ownership.md。

## 有序实施

- [x] 1. 逐项审阅D1–D5交接证据与实际接口，建立44+8项覆盖账本，52/52实际工程绑定。
- [x] 2. 完成按场次/日期/年月和旧档查询、定位与管理；接CAS编辑/多选删除。
- [x] 3. 实现仓储全量TXT/SAF和本地装扮白名单导出/导入。
- [x] 4. 扩展现有私人资料配置域的ID/资源映射、分阶段回执与CAS恢复。
- [x] 5. 执行完整入口→预览→接受→继续→重生成→改删→阅读/声音→回聊天的隔离故事。
- [x] 6. 修复真实集成失败，运行唯一完整JVM并定向复验、非零截图verify、Lint/编译/双APK与原生故事。截图1/53匹配，52差异仍待接纳。
- [x] 7. 独立全范围最终证据复核通过，规范/版本/矩阵、11候选/TXT与范围已审阅，journal Session17已记录。提交/视觉基准/归档未授权，分别保留待处理。

## 验证命令

下列是规划时保留的选择器示例，不是执行回执。2026-11-20实际命令、零/非零结果、
XML/原生输出、源码/APK绑定以 [最终交接](research/handoff.md) 和 validation/rounds.json 为准。

```powershell
rg --files app/src/test app/src/androidTest | rg 'Companion|Roleplay|Memory|Voice|Translation'
./gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.viewmodel.RoleplayViewModelTest" --tests "com.example.myapplication.viewmodel.RoleplayDurableMemoryFlowTest" --tests "com.example.myapplication.viewmodel.RoleplayMemoryLifecycleTest" --tests "com.example.myapplication.data.repository.roleplay.RoleplayRunRepositoryTest" --console=plain
./gradlew.bat :app:testDebugUnitTest --tests "*Companion*Test" --console=plain
./gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin lintDebug --console=plain
```

新增CompanionHistory/Export/AppearanceTransfer/Integration与真实Room导入测试；最终无selector完整JVM，受影响H/E04/E05等截图非零verify，旧schema升级到最终版及IME/系统栏/音频隔离设备故事。记录真实模型/实机缺口。

构建/截图/设备按 [父验证计划](../11-09-date-companion/implement.md) 与实际风险执行。共享数据/协议变化必须跑相应旧回归；无新失败或修改不反复全量测试。D6负责最终完整JVM/受影响截图/双APK/集成。

## 独立检查与交接

- [x] trellis-check全范围复核本块需求/实际源码/原始验证报告通过，无遗留工程问题；相关真实失败均修复后定向复验。
- [x] 本块AC和父矩阵已关联实际证据；未运行模型/实机效果如实标注。
- [x] 主会话核验源码与证据对应，按真实新合同更新规范/版本记录。
- [x] 输出实际接口、限制、75路径清单与验证命令；不宣称整个版本发布完成。
- [x] 用户本轮明确批准提交/发版与207项UI候选，工作/基准分别提交，Actions44745192024和AndroidQuality44744946541全部通过；工程归档及journal由主会话收尾，真实效果缺口保留。

## 风险与回退

范围遗漏、跨存储假原子、原文导出不全、旧任务/截图夹带是收尾风险。任何失败都保留已有原文/配置/独立资源，不通过删用例/重录基准/降库绕过。
