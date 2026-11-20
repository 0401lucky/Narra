# D3 原生界面测试源码交接

2026-11-11，状态：**SOURCE_READY_NOT_COMPILED_NOT_EXECUTED**。本子代理只新增下列两份AndroidTest源码和本交接记录；没有修改生产/JVM/旧D2测试，没有运行Gradle、IDE格式化、设备或真实服务。全部源码所有权现交回 `companion_d3_implement`，由其统一格式化、编译和打包，再由主会话执行设备验收。

## 文件与用例

路径相对 `app/src/androidTest/java/com/example/myapplication/ui/screen/roleplay/`。

| 文件 | 项数 | 主要覆盖 |
| --- | --- | --- |
| `CompanionSettingsScreenTest.kt` | 9 | 真实CompanionSettingsViewModel与Screen：全部文风/默认/三人称、模板应用/更新/删除后保留文本、稳定观测ID/六项上限/重名、保存失败保稿、重复保存/取消、CAS冲突显式重载、320dp/200%/48dp、IME与放弃草稿弹窗、较新配置/角色删除只读。 |
| `CompanionObservationPanelTest.kt` | 6 | 实际CompanionReplyProjector/Parser产物与Panel：历史快照标签、无块/坏块/未知字段清空、源修订变化关闭旧弹窗、关闭/重新启用不自动展开、系统Back仅关闭弹窗、320dp/200%长观测滚到底和固定关闭按钮。 |

设置测试使用测试文件内的 `NativeSettingsStore` 提供可控Flow和保存结果；除此之外的设置写入均报错。保存/冲突行为驱动实际VM；它不代替生产DataStore的CAS及序列化验证。观测测试使用合成消息与明确的源元数据，再调用真实projector；关联图的Room验证由核心JVM/仓储用例负责。没有复制生产解析器或草稿状态机来构造测试控件。

## 原生执行选择器

```text
com.example.myapplication.ui.screen.roleplay.CompanionSettingsScreenTest
com.example.myapplication.ui.screen.roleplay.CompanionObservationPanelTest
```

预期共 **15项/2类**，零测试不计通过。应完整执行；不是仅编译后标为原生通过。固定保存区的错误/冲突动作断言直接检查 `assertIsDisplayed`，没有先滚动去寻找错误文案。

## 预定截图

执行对应成功走到捕获点的用例后，PNG位于目标应用 `cache/companion-d3-ui-review/`：

- `settings-320dp-font200-save-error.png`：长表单底部保存失败，保留草稿、错误及保存/取消完整操作区。
- `settings-320dp-font200-conflict-actions.png`：冲突后的原草稿与固定错误/读取最新设置操作，取消重载弹窗后捕获。
- `observation-320dp-font200-last-field.png`：实际Dialog滚动范围到最大值，最后字段与至少48dp关闭按钮。

这些是待设备产出的审阅候选；当前没有生成PNG，也没有登记、record或更新Roborazzi基准。

## 静态核对与未执行项

- `git diff --check -- <两测试文件>`执行成功；另逐行检查，两文件均无尾随空白。
- 以源码提取核对Fake实现了SettingsStore全部当前方法，没有缺失或额外方法；其它设置写入都使用明确失败。
- 按 `@Test` 统计9+6项；所有测试方法均为常规Unit函数。
- 尚未编译、运行Lint、格式化或执行任何设备用例。核心已确认并将保存错误/冲突反馈放到固定操作区；实际大字体/IME与Dialog行为须由设备结果决定，不放宽相关断言来规避失败。

交回时SHA-256（统一格式化后需由核心重新冻结）：

| 文件 | SHA-256 |
| --- | --- |
| `CompanionSettingsScreenTest.kt` | `f7fdc50701fc64c73a71e4137042a110b32b2f5249250ef2f29ab6abf35e62f9` |
| `CompanionObservationPanelTest.kt` | `63d3bec0e0b1fe9bdb8c2389f99f4431cebb64f21adac70b3a6e8e805caefeda` |

没有本子代理创建的后台进程、Gradle会话或设备操作需要清理。
