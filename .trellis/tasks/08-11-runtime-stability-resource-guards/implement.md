# 运行时异常、取消与资源边界：实施计划

## Completion Definition

AC1～AC9 满足：取消与旧任务竞态有确定测试，主线程同步 IO 被移出，TTS / Base64 在大
分配前具备边界，系统资源异常时可释放，且禁用组件、成功路径和敏感信息边界不回归。

## Pre-Start Gate

1. 启动设置子任务已完成 `SettingsViewModel.kt` 的设置订阅 / 草稿同步修改与检查。
2. 本任务只继续修改同文件的声音样本请求构造和读取 / Base64 区域，不回退前置改动。
3. RP 子任务独占 `RoleplayActionMessageCards.kt` 和语音卡 `MediaPlayer`；本任务不得触碰。
4. 父任务最终规划已由 lucky 明确批准，当前子任务才可 `task.py start`。

## Ordered Checklist

1. **建立回归测试与可注入边界**
   - 为 TranslationViewModel、Worker、更新、脚本和语音补取消 / 旧结果测试。
   - 为 dispatcher、下载查询、文件读取和响应读取提供最小可测试入口。

2. **修复取消与任务 identity**
   - 所有宽泛捕获先重抛 `CancellationException`。
   - 翻译 / 轮询任务增加当前 Job 或 generation 守卫，旧任务不得写新状态。
   - 悬浮翻译流式 UI 更新归属父 Job 并合并更新频率。

3. **移出主线程同步工作**
   - 包装 `DownloadManager.query()` 的 IO 边界。
   - 将声音样本读取 / Base64、导出序列化和文件写入迁移到合适 dispatcher。
   - 验证取消和页面失活后没有 stale UI 写入。

4. **增加输入大小门禁**
   - 文件元数据检查先于 `readBytes()`。
   - TTS body 有界读取，Base64 在规范化 / 解码前检查最大长度。
   - 补正常、边界、超限、缺失 Content-Length 和恶意错误体测试。

5. **收口系统资源**
   - Bitmap / Image / MediaProjection 在异常和取消路径 `finally` 释放。
   - 确认 Manifest disabled 状态和产品导航不变。

6. **质量验证**
   - 运行相关 JVM 测试、AndroidTest 源码编译、Lint 和 assembleDebug。
   - 检查日志 / UI 不含密钥、Base64、响应正文和本地路径。
   - 检查 diff 不包含 Room、RP Compose、远程诊断 SDK 或组件启用改动。

## Validation Commands

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.viewmodel.TranslationViewModelTest" --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.viewmodel.AppUpdateViewModelTest" --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.conversation.VoiceSynthesisCoordinatorTest" --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.data.repository.tts.MimoTtsClientTest" --console=plain
.\gradlew.bat :app:compileDebugAndroidTestKotlin --console=plain
.\gradlew.bat lintDebug --console=plain
.\gradlew.bat assembleDebug --console=plain
```

单个 JVM 测试命令最大等待 60 秒。系统组件真机测试缺设备时只记录未取得结果。

## Risky Files / Rollback Points

- `TranslationViewModel.kt`、`ScreenTranslatorService.kt`：generation / Job 清理必须只影响当前 run。
- `VoiceSynthesisCoordinator.kt`、`SettingsViewModel.kt` 声音样本区域：dispatcher 迁移不能改变
  请求字段和状态顺序，也不能覆盖启动设置子任务的订阅区域。
- `MimoTtsClient.kt`、`AudioFileStorage.kt`：大小上限必须覆盖现有合法音频。
- `MediaProjectionCaptureEngine.kt`：避免双重回收和释放后访问。
- 工作区存在既有改动；实施前逐文件审查 diff，不覆盖 lucky 的内容。
