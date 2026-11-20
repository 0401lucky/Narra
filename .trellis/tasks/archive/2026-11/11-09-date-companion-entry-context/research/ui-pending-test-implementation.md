# D2 待保存结果 UI 测试交接

2026-11-11，`companion_d2_ui_tests`新增并交回独立检查者`companion_d2_check`：

`app/src/androidTest/java/com/example/myapplication/ui/screen/roleplay/CompanionPendingResultsNavigationTest.kt`

FQCN：`com.example.myapplication.ui.screen.roleplay.CompanionPendingResultsNavigationTest`。

本次只有该新测试文件和本记录；未修改生产、原有13项UI测试、共享helper或截图基准。检查者继续拥有生产、格式化、Gradle及最终源码冻结。

## 四项行为测试

1. `originalOnlineRoute_pendingRowsSaveExactAttemptsAndBackClosesOnlyDialog`：phase NONE 的原线上/普通route仍可打开待保存结果；两行分别传入对应attemptId；第一次Back只关闭弹窗，下一次由原NavHost返回。
2. `activeNewScene_savesOldResultOnceWhileBusyAndKeepsItsRecoveryInTheModal`：ACTIVE新场B可补旧A结果；忙态拒绝第二次实际触控，错误后同一结果可重试；关闭弹窗不切场，下一次Back才离开。
3. `foreignScenarioWhileDialogIsOpen_hidesOldAndForeignResultActions`：对话框打开时输入状态切为其它scenario，旧结果弹窗/按钮移除，其它角色文字和保存动作不显示。
4. `narrowLargeFont_lastResultSaveAndCloseRemainReachableWithFullTouchTargets`：局部320dp/200%字体，六份长结果可滚到最后的保存按钮，保存及关闭触控区域不少于48dp；实际宽度按DpRect.right-left检查。

测试直接消费生产`CompanionRouteContent`及其`CompanionPendingResultsBar`/`NarraAlertDialog`，没有复制生产scope或路由判定。普通会话内容使用测试slot；只证明UI委托/导航边界，持久模式/场次、Room授权和零HTTP仍由检查者的VM/仓储测试负责。未初始化AppGraph或真实服务。

## 静态交接与未执行项

- 作者源码有4个`@Test`，无尾随空白；未使用此前不兼容的DpRect.width属性。
- 作者交接SHA-256：`0d142c79572f3818e8c71d0805605f5e30e2a5016a2a7d2f2af935b3d37176bc`。该值是检查者格式化前的作者快照，不是最终APK冻结证明。
- 本代理未运行Gradle、设备或模型；当前不能报告这四项通过。编译、instrumentation结果及后续修复以检查者/主会话原始报告为准。
- LocalConfiguration与LocalDensity只在测试Composition内覆盖；Dialog据此使用窄屏约束，没有修改系统显示或字体设置。

## 可选视觉候选

窄屏用例运行到捕获步骤时，将真实Dialog root像素写入目标app：

`cacheDir/companion-ui-review/pending-results-320dp-font200-last-save-dialog.png`

候选展示最后一份结果的保存与关闭操作，不是整个结果列表全景或已接纳基准。是否产生文件及视觉表现，须在实际运行后确认。
