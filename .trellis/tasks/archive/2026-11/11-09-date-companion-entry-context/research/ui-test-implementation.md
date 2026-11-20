# D2 原生 UI 测试交接

日期：2026-11-11。实施者：`companion_d2_ui_tests`。

## 文件与执行权

仅新增两个产品测试文件：

- `app/src/androidTest/java/com/example/myapplication/ui/screen/roleplay/CompanionEntryScreenTest.kt`：10项。
- `app/src/androidTest/java/com/example/myapplication/ui/screen/roleplay/CompanionEntryNavigationTest.kt`：3项。

没有修改生产、共享fixture、版本/任务状态、截图登记表或基准。生产`CompanionRouteContent`由主实施者提取并接入实际`RoleplayNavGraph`；本代理只消费该真实wrapper。两个测试文件已交回`companion_d2_implement`统一编译，后续Gradle和设备由主实施者/主会话协调。

## 行为覆盖

| 测试边界 | 实际断言 |
| --- | --- |
| 三开场与空态 | 页面本身不触发任何回调；三个实际按钮分别传APPROACH/INVITE/DIRECT；无会话时只有返回可操作 |
| 预览与保存失败 | 接受后的忙态禁止再次接受和切开场，保留停止；ACCEPT/SAVE恢复动作不回调重新生成，未落盘产物不能接受 |
| 操作区 | CONTINUE、最新答重生成、新场、补保存各自走实际控件回调；忙态和待恢复状态阻止不适当操作 |
| 阅读/设置/返回 | 普通旧route没有entryId仍显示恢复的PREVIEW；真实wrapper经过NavHost阅读/设置目的地后返回同一预览，原origin/entryId不变；导航不触发生成回调 |
| 跨场隔离 | 新entry面对其它scenario状态先显示loading，不泄露其它预览；普通旧route面对其它scenario状态保留普通内容和NavHost自身Back |
| 返回层级 | 实际NarraAlertDialog先消费Back，关闭未完成结果不离场；下一次Back才离场；键盘可见时首次系统Back仅收起IME并保留时区草稿 |
| 可访问布局 | 局部320dp宽、200%字体，长预览后接受与时区保存仍可滚动到达；按钮至少48dp；保存失败保留草稿，可重试相同内容 |

Screen测试直接渲染`CompanionEntryScreen`/`CompanionSceneActions`，只统计真实UI委托行为，不复制ViewModel业务状态机。HTTP调用数、数据库提交、进程重开和准确恢复原运行仍由主实施者的Room/HTTP/VM回归证明。

Navigation测试在原生NavHost内使用生产`CompanionRouteContent`，没有复制它的preparing/Back条件。阅读和设置使用测试目的地验证导航往返；它不初始化整个AppGraph，也不替代实际RoleplayReadingMode/设置页面自身测试或全图启动验收。

## 验证状态

- 静态检查：实际13个`@Test`方法，两个文件均无尾随空白。
- 静态调用核对：`RoleplayNavGraph`已调用本轮生产wrapper。
- 本代理未运行Gradle、Kotlin编译、设备或真实服务。以下命令是交接计划，不能记为通过：

```powershell
./gradlew.bat :app:compileDebugAndroidTestKotlin --offline --console=plain
./gradlew.bat :app:assembleDebugAndroidTest --offline --console=plain
```

冻结应用与测试APK后，在隔离AVD按下列两个FQCN执行定向instrumentation：

```text
com.example.myapplication.ui.screen.roleplay.CompanionEntryScreenTest
com.example.myapplication.ui.screen.roleplay.CompanionEntryNavigationTest
```

IME测试要求实际软键盘可见并检查WindowInsets后再按Back，不能把未出现键盘的运行当作键盘验收通过。尺寸和字体只通过测试Composition局部覆盖，不修改宿主或Android全局设置。

## 视觉候选

窄屏大字用例执行到相应步骤时，会在目标app的`cacheDir/companion-ui-review/`写入：

- `entry-320dp-font200-preview-actions.png`
- `entry-320dp-font200-zone-save-retry.png`

捕获的是实际320dp测试宿主在对应滚动位置的像素，分别展示长预览后的操作区和保存重试；不是整篇长文全景、Roborazzi基准或已接纳截图。是否实际产生、设备表现和后续修复结果，以主会话的原始instrumentation报告为准。
