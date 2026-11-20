# D2 照片输入保存回执测试交接

2026-11-11，`companion_d2_ui_tests`仅新增：

`app/src/androidTest/java/com/example/myapplication/ui/screen/roleplay/CompanionInputPersistenceScreenTest.kt`

FQCN：`com.example.myapplication.ui.screen.roleplay.CompanionInputPersistenceScreenTest`，4项原生测试：

1. `sendingWithoutInputSavedAck_keepsPhotoUntilMatchingAckArrives`：发送进入busy而未收到输入保存ack时，照片仍在且不可移除；匹配ack到达才清空，生成仍busy也不影响回执清理。
2. `inputSaveFailure_preservesExactPhotoForRetryAndOnlySuccessfulAckClearsIt`：未ack的保存失败恢复可操作后，同一图片URI仍可明确重发；成功重试ack才清除。
3. `staleInputSavedAck_doesNotClearNewlySelectedPhotoWithTheSameFileName`：选择另一张同名、不同URI图片后，旧ack不能清除新图；下一次实际发送包含新URI。
4. `legacyCallbackWithoutInputSavedAck_preservesOriginalSendStartClearing`：可选callback为null时，既有onSend及发送开始清图行为仍兼容。

## 实际测试边界

- 渲染真实`RoleplayScreen`，通过`LocalActivityResultRegistryOwner`注入记录式registry；点击真实更多操作/添加图片，检查`PickVisualMedia`契约及`ImageOnly`输入，再派发合成URI。
- PNG为测试在目标app私有cache新建的8×8位图，同名文件分处唯一目录；不打开真实相册或用户文件，测试后只删除自身创建的文件和空目录。
- 对实际回调中的`ChatMessagePart`断言IMAGE类型、完整URI和image MIME；旧图与新图的显示文件名相同但URI不同，不用文件名标签数量代替附件身份验证。
- 只模拟UI收到的busy/error/ack状态，未复制发送组件或生产清理逻辑。Room成功前ack不得被调用的持久契约由检查者的VM/仓储测试负责；本文件不测试或复制RoleplayNavGraph的busy融合条件。

## 交接状态

- 新源码4个`@Test`、无尾随空白；作者SHA-256：`f7ebb6ece83f744b202dc3124e2d8fcad6c89f82fec2625c1b1b5f7d80fd747c`。
- 文件已冻结并交回`companion_d2_check`统一格式化/编译。此hash是格式化前作者快照，不是最终APK证明。
- 本代理未运行Gradle、设备、模型或网络。实际编译/设备结果及修复以检查者/主会话日志为准，当前不标为通过。
- 原有17项UI测试、生产代码、共享helper、基准与系统设置均未修改；未新增截图候选。
