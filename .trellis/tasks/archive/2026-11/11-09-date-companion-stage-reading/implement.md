# D4 立绘演出与连续阅读：执行计划

2026-11-09整体方案已获实施批准；本块按下述依赖激活，当前状态见task.json。开始前复核 [PRD](prd.md)、[design](design.md)及真实JSONL上下文。

## 开始条件

- [x] [D3](../11-09-date-companion-style-observation/prd.md) 必要工程/原生行为验收通过，最新接口与当前37JVM/37设备证据见其research/handoff-2026-11-18.md；既有UI视觉差异未接纳，单独保留。
- [x] 本次继续已记录1425项源码/281保护项、schema55及展开工作区基线于research/continue-2026-11-18/baseline；与上次源码/保护项无漂移，8条实施/14条检查上下文task.py validate通过。
- [x] 主会话已激活本子任务；本次继续由原生d4_player独占纯播放器/对应测试，d4_integration独占素材/阅读/UI及必要adapter，主会话保持唯一Gradle/设备/formatter；继承会话模型，不改配置或模型参数。

## 有序实施

- [x] 1. 接D3投影，纯播放器/唯一时钟代次/源版本和生命周期已实现；d4-player-03实际27项全过，含GB9c修复，1437源码前后一致。Android字符与真实页面仍按后续步骤验证。
- [x] 2. 在现有线下内容面板增加演出/阅读切换，保留原消息与快捷回调。
- [x] 3. 完成图集/套装/布局/资源引用与旧图回退，图像IO后CAS提交。
- [x] 4. 实现基础跨场次keyset分页与锚点，覆盖前插/删游标/图片/字体/IME。
- [x] 5. 统一覆盖层返回、长按取消、触控语义、系统栏与大字体布局。
- [x] 6. 运行播放器/资源/分页及原生交互；受影响旧截图verify并保存候选。
- [x] 7. 独立工程检查并交D5稳定SpeechUnit/选区/生命周期接口；旧UI视觉基准接纳单独保留。

2026-11-19 本轮工程收口见 [交接](research/handoff-2026-11-19.md)：JVM分轮最新去重
149项、原生分轮最新去重33项通过，产品/JVM/AndroidTest编译、双APK及最终Lint通过。
35旧截图实际1匹配/34既有差异，与D3候选逐字节一致，本轮新增像素差异0；未接纳基准，
不称verify全过。用户追问耗时后仅做最后单项复验和一次Lint/截图收尾，不重复整组。

## 验证命令

下列旧测试选择器来自静态研究；先通过rg确认当前实际名称。新Companion测试在实现后发现并运行，零测试不能算通过。本轮规划未运行这些命令。

```powershell
rg --files app/src/test app/src/androidTest | rg 'Companion|Roleplay|Memory|Voice|Translation'
./gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.ui.component.roleplay.RoleplayStreamingAutoScrollTest" --tests "com.example.myapplication.ui.component.roleplay.OfflineRoleplayAccessibilityTest" --tests "com.example.myapplication.ui.component.roleplay.RoleplayReadingModeAccessibilityTest" --tests "com.example.myapplication.ui.component.roleplay.RoleplayLazyContentTypeTest" --console=plain
./gradlew.bat :app:testDebugUnitTest --tests "*Companion*Test" --console=plain
./gradlew.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin lintDebug --console=plain
```

新增CompanionBeatPlayer/Resources/ReadingPagination/StageUi测试；原生IME和系统返回，真实图片路径/像素核验；受影响E04/E05/手机入口截图按实际变更选择verify，绝不预先record。

构建/截图/设备按 [父验证计划](../11-09-date-companion/implement.md) 与实际风险执行。共享数据/协议变化必须跑相应旧回归；无新失败或修改不反复全量测试。D6负责最终完整JVM/受影响截图/双APK/集成。

## 独立检查与交接

- [x] trellis-check全范围复核本块需求/实际源码/原始验证报告；相关真实失败修复后定向复验。
- [x] 本块AC和父矩阵已关联实际证据；未运行模型/实机效果如实标注。
- [x] 主会话核验源码与证据对应，按真实新合同更新规范/版本记录。
- [x] 输出下块可使用的接口、限制、实际文件清单和验证命令；不得提前称父任务完成。
- [ ] 提交/基准接纳/归档按本轮最终具体范围和仓库规则，由主会话协调，当前不自动提交。

## 风险与回退

高频重组/旧timer、私有资源引用、滚动锚点和旧UI基准为风险。回退只停用演出视图，原文/新场仍可阅读；不删除用户素材或全局样式。
