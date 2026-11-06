# 首轮设施探针的生命周期失败

- 命令：`gradlew.bat :app:testDebugUnitTest --tests com.example.myapplication.ui.screen.settings.worldbook.WorldBookCaptureProbeTest --tests com.example.myapplication.ui.screen.settings.ContextTransferCaptureProbeTest --console=plain`。
- 测试源码编译实际执行并通过；没有取得整轮产品测试通过结果。
- `thread-dump.txt` 显示测试线程停在 `P06RenderTest.restoreP06Clock` 的 `RuntimeEnvironment.setFontScale`，经配置更新进入 `Choreographer.doFrame/scheduleVsync` 持续重排。
- 原因：JUnit `@After` 在 Compose rule 内部运行；Composition/Activity 尚未释放，就把 Robolectric 虚拟 uptime 回拨到了启动前。恢复字体使仍存活的 Choreographer 收到与其上一帧不一致的时间。
- 已核对 PID 36576 为这次 `GradleWorkerMain`，并以线程栈确认 P06 归属；仅终止该测试 worker。Gradle 返回 1，终止日志与退出码保留在本目录。未终止其他进程，未修改系统时钟或产品代码。
- 修复在 P06 测试宿主中把恢复移到 `order=-2` 的外层 TestRule，在继承的 Compose rule 完成释放后执行，并断言 Activity 生命周期已为 DESTROYED。下一轮使用独立目录；本轮不标记为通过。
