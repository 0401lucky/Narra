# D3 设备定向修复上下文（2026-11-18）

完整 D3 独立审查和 R4/R5 已完成，见 check-2026-11-18.md；完整 19 项清单保存于
check-full-context-2026-11-18.jsonl，后续需要扩大范围时恢复。此次只收窄原生设备失败修复，不重做 D3 全范围审查。

- 新回归按方法去重 37 全部通过；原 254 不计新修复。联合 Debug/UnitTest/AndroidTest/Baseline Kotlin、Lint、双 APK通过，1425源无漂移。
- 35旧截图：1匹配34像素差异，68actual/compare已固定，代表图对应既有UI，281保护项不变；视觉未接纳，不record。
- 专用 Narra_Memory_V24 / emulator-5580 首轮37设备：18通过19失败，原始结果在 resume-2026-11-18/d3-device-04。
- javap确认 Debug class 和 compile classes.jar 已为17字段 RoleplayCompanionUiState，AndroidTest的旧D2 class仍调用15字段默认构造器/copy，另有旧CompanionEntryCallbacks。是旧增量ABI，不加兼容构造器。
- templates_applyUpdateAndDeletePreserveAppliedText已由此前checker改用精确EditableText匹配，仍需设备复验；当前只改了该AndroidTest文件。
- 观测 narrowLargeFont_longObservationScrollsToEndAndKeepsCloseTouchTarget 实际失败第270行 scroll maxValue=0，48dp检查已通过。需要核对Dialog窗口是否继承外层测试LocalDensity/fontScale=2：当前合成host320dp/600dp，但Dialog是独立AndroidComposeView，可覆盖平台LocalDensity/Configuration。不能靠弱化滚动或只把字符串变长来声称真实200%字体已验证；必要时用隔离AVD/活动窗口真实fontScale并恢复，或对确认的产品字体继承问题做最小修复。
- 主会话正在 d3-android-recompile-05：compileDebugAndroidTestKotlin --rerun、assembleDebugAndroidTest，命令级kotlin.incremental=false/in-process，禁止其它Gradle/formatter/设备或源码并行变更。

主会话负责唯一Gradle和隔离设备、父/版本/spec。代码库有其它UI/v2.4改动，保留全部12UI漂移文件，不改主题/基准/版本/更新JSON，不提交发布。新checker先只读给出观测滚动真实原因与最小修复建议；主会话释放编译冻结后再自修，必要读其它规范/源文件按需进行，不重读整套历史。
