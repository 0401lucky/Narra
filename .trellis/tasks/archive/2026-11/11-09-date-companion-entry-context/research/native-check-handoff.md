# D2 原生测试断言修复交接

2026-11-11，状态为 **FIXED_AND_REBUILT_PENDING_NATIVE_RERUN**。主会话首轮在隔离 `Narra_Memory_V24` 执行 21 项原生测试，19 项通过、2 项失败。此交接只覆盖失败后的测试修正与重建；两方法的设备复验由主会话执行。

## Findings (fixed)

- `CompanionEntryScreenTest.kt`：接受开场测试把单独台词传给默认完整匹配的 `assertTextContains`，实际预览包含旁白与台词。改成完整 `角色拉开椅子。\n\n请坐一会儿。` 的 `assertTextEquals`，同时保护两部分正文及顺序；原来的重复接受、直接开始禁用和停止回调断言均保留。
- `CompanionEntryNavigationTest.kt`：阅读目的地包含前缀 `阅读原会话：`，旧断言却只匹配 `scenarioId`。阅读与随后尚未运行到的设置目的地均改为完整文字相等断言，保留包含 `/ ? & #` 的真实导航参数、返回与零请求检查，不放宽到子串匹配。

修改仅为上述 3 处断言和 2 个 import。未修改生产代码、JVM 测试、截图基准或数据库。将这 5 处改动在内存中反向恢复后，两个文件的原始字节 SHA 均与 `check-final-source-hashes.json` 一致；准确差异见 `native-check-assertion-fixes.patch`。

## Findings (not fixed)

无确认但未修复的代码问题。两条修正后的原生用例尚待主会话重新执行，不能将编译成功计为原生通过。

## Verification

- Formatter：Android Studio Panda 2 对两文件完成格式化，exit 0；`native-check-format-01.log`。
- TypeCheck：`:app:compileDebugAndroidTestKotlin` 通过；生产 Kotlin 仍为 UP-TO-DATE。
- Lint：`:app:lintDebug` 通过，0 errors、56 warnings；`native-check-lint-results-debug.xml`。
- Build：`:app:assembleDebugAndroidTest` 通过，单次构建 2m52s，exit 0；`native-check-build-command.json`、`native-check-build-01.log`、`native-check-build-exit.json` 保存实际命令与结果。
- Native：首轮 `device-native-01.log` / `device-native-01-summary.json` 保留 19 通过、2 失败证据。复验选择器在 `native-check-validation.json`，应让两个方法完整运行到其后续动作与断言。
- JVM / 旧截图：生产、JVM 与截图源码均未变化，沿用 `check-final-validation.json` 的 397 唯一 JVM / 35 截图证据；本次未重复执行。
- 范围：1396 项冻结源码中仅上述两文件发生变化。新冻结清单为 `native-check-source-hashes.json`，SHA-256 `c329fefa79673fbd3b04cb270acaf981d9650bee185a1abf3a7be4d4bed905e3`。准确前后 SHA 见 `native-check-changed-files.json`。

| 产物 | SHA-256 |
| --- | --- |
| 主 APK，保持原包 | `d74b4822fd51de451667b8a85f180f29bef454597331eae6ec435c254b1a7204` |
| 新 AndroidTest APK | `653ea58d00dde421cb6a60a22e9e7deec1a478bd919d82efee0487ac2d22c573` |

新测试 APK 的 apksigner 校验通过，证书 SHA-256 为 `3a6c57eec0641bc00830252eb821d62c9e9b2f639c5989d0bc42b29b843546e8`，与原主包一致；见 `native-check-test-signature.log`。

单次 Gradle daemon 26844 与启动进程 26440 已正常退出，无本轮 Kotlin / formatter 残留；见 `native-check-process-cleanup.json`。**全部源码与 Gradle / formatter 所有权已交回主会话。** 主会话自行维护的质量规范与视觉审阅记录未被此检查者改写。
