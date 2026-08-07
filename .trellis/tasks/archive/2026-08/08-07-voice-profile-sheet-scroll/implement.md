# 角色音色配置滚动修复：实施计划

## Completion Definition

角色音色弹窗在小屏、大字体、长文本、克隆错误和输入法场景下可滚动到并点击保存，
既有草稿/试听行为不变，Compose 测试、Lint 和构建通过。

## Ordered Checklist

1. 使用 `trellis-before-dev` 读取 UI、质量和错误处理规范，确认 `VoiceSynthesisSettingsScreen`
   的现有测试入口。
2. 先补可复现的 Compose 测试入口（AC1-AC3）；必要时只提取同包可见的 Sheet 内容，不改变
   ViewModel、设置模型或公开导航契约。
3. 为 `AssistantVoiceProfileSheet` 使用单一滚动容器和稳定滚动状态（AC1、AC2），加入
   IME/导航栏安全区与底部 padding；避免嵌套滚动。
4. 增加或更新设备测试（AC1-AC3），覆盖三种模式、长设计描述、克隆错误提示、模式切换和
   保存回调。
5. 在小屏/大字体设备上实际操作滚动、测试、取消和保存（AC1、AC2）。
6. 运行受影响测试、Lint 和 debug 构建（AC4）。

## Validation

```powershell
.\gradlew.bat :app:compileDebugAndroidTestKotlin --console=plain
.\gradlew.bat connectedDebugAndroidTest --console=plain
.\gradlew.bat lintDebug --console=plain
.\gradlew.bat assembleDebug --console=plain
```

## Risky Files / Rollback

- `VoiceSynthesisSettingsScreen.kt`：只改弹窗布局，不改设置模型和存储。
- 若设备测试无法运行，必须明确报告，不能以 JVM 编译代替滚动验收。

## Acceptance Mapping

- AC1 → 步骤 2-5。
- AC2 → 步骤 2-5。
- AC3 → 步骤 2、4。
- AC4 → 步骤 6。
