# P06 工作提交清单

状态：用户已确认清单并归档，已完成`c925da5e`（实现/测试）与`b7af5429`（视觉基准）。本清单保留当时的精确提交路径；当前任务已移到归档目录。用户随后另行授权推送main，Actions/R2发版由新任务承接。

## 1. feat(ui): 统一v2.3世界书与资料库无障碍行为

本笔共3783个文件：9个生产文件、25个测试/登记文件、3个规范/版本文件，以及3746个本任务文档与证据文件。

### 生产文件

- `app/src/main/java/com/example/myapplication/ui/component/NarraButton.kt`
- `app/src/main/java/com/example/myapplication/ui/component/worldbook/KeywordChipInput.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/ContextTransferScreen.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/SettingsComponents.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/worldbook/WorldBookBookDetailScreen.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/worldbook/WorldBookContentFullscreenSheet.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/worldbook/WorldBookEditScreen.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/worldbook/WorldBookEditSections.kt`
- `app/src/main/java/com/example/myapplication/ui/screen/settings/worldbook/WorldBookListScreen.kt`

### 测试与登记

- `app/src/test/java/com/example/myapplication/ui/screen/settings/ContextTransferBehaviorTest.kt`
- `app/src/test/java/com/example/myapplication/ui/screen/settings/ContextTransferBusyTest.kt`
- `app/src/test/java/com/example/myapplication/ui/screen/settings/ContextTransferCaptureProbeTest.kt`
- `app/src/test/java/com/example/myapplication/ui/screen/settings/ContextTransferColorsTest.kt`
- `app/src/test/java/com/example/myapplication/ui/screen/settings/ContextTransferPersistenceTest.kt`
- `app/src/test/java/com/example/myapplication/ui/screen/settings/ContextTransferPreviewCaptureTest.kt`
- `app/src/test/java/com/example/myapplication/ui/screen/settings/P06LibraryEntryTest.kt`
- `app/src/test/java/com/example/myapplication/ui/screen/settings/P06SharedComponentsTest.kt`
- `app/src/test/java/com/example/myapplication/ui/screen/settings/worldbook/P06ColorTestSupport.kt`
- `app/src/test/java/com/example/myapplication/ui/screen/settings/worldbook/P06PopupPixelGuardTest.kt`
- `app/src/test/java/com/example/myapplication/ui/screen/settings/worldbook/P06WorldBookPersistenceTest.kt`
- `app/src/test/java/com/example/myapplication/ui/screen/settings/worldbook/P06WorldBookTestSupport.kt`
- `app/src/test/java/com/example/myapplication/ui/screen/settings/worldbook/WorldBookAccessibilityTest.kt`
- `app/src/test/java/com/example/myapplication/ui/screen/settings/worldbook/WorldBookCaptureProbeTest.kt`
- `app/src/test/java/com/example/myapplication/ui/screen/settings/worldbook/WorldBookColorsTest.kt`
- `app/src/test/java/com/example/myapplication/ui/screen/settings/worldbook/WorldBookUiBehaviorTest.kt`
- `app/src/test/java/com/example/myapplication/ui/screenshot/ContextTransferRenderTest.kt`
- `app/src/test/java/com/example/myapplication/ui/screenshot/ContextTransferScreenshotE06Test.kt`
- `app/src/test/java/com/example/myapplication/ui/screenshot/LegacyTextFieldScrollProbe.kt`
- `app/src/test/java/com/example/myapplication/ui/screenshot/P06RenderTest.kt`
- `app/src/test/java/com/example/myapplication/ui/screenshot/ScreenshotCases.kt`
- `app/src/test/java/com/example/myapplication/ui/screenshot/WorldBookRenderTest.kt`
- `app/src/test/java/com/example/myapplication/ui/screenshot/WorldBookScreenshotE06Test.kt`
- `app/src/test/java/com/example/myapplication/ui/theme/UiTokenSourceGuardTest.kt`
- `app/src/test/java/com/example/myapplication/ui/theme/UiTokenSourceInventory.kt`

### 规范、版本与证据

- `.trellis/spec/backend/quality-guidelines.md`
- `.trellis/spec/backend/ui-token-contract.md`
- `版本功能添加/优化.md`

任务证据仅限当前`.trellis/tasks/11-05-ui-token-worldbook/`内的规划、研究、原始与修正前后图、视觉接纳、语义/边界、JUnit/Lint报告、哈希与提交清单。完整逐文件列表见[commit-plan.json](commit-plan.json)的`commits[0].files`。

版本文档仅stage本批P06段落，v2.4记忆规划段保留；两处部分暂存补丁已按HEAD检查，尚未应用。

## 2. test(screenshot): 接纳v2.3世界书与资料库视觉基准

- `WorldBookScreenshotE06Test` 47张与`ContextTransferScreenshotE06Test` 16张已接纳PNG，共63张。
- `app/src/test/snapshots/manifest.md`仅新增P06接纳段落；旧CI文字差异不进入索引。
- 共64项，逐文件见JSON的`commits[1].files`，部分暂存路径/目标SHA见`partialStaging`。

## 验证

- 接纳后222/222截图、1973/1973无selector完整JVM通过，实际执行、退出0，无失败/错误/跳过。
- 221张PNG及14+9来源无漂移；63张新增严格匹配已审SHA，原158张保持。
- Lint/dev/AndroidTest编译通过，无新增警告；真机和远端CI未冒充通过。

## 排除与后续

- 116条其他工作区状态排除，原路径见JSON的`excluded_status_entries`；包括v2.4规划及子任务、历史搬迁、无关截图/资料。
- 本任务另有70个binary缓存、formatter参数和旧CI差异备份等不纳入，见`excluded_task_artifacts`。已忽略的`.log`仍只留本地，不强制add。
- 两笔工作提交后仅归档本任务并记录journal。当前archive helper会stage整个archive目录，改用受支持的`--no-commit`后精确暂存当前任务移动及必要引用，避免夹带其他历史状态。
- 构建/更新仍2.2.12-dev，功能属于未发布v2.3。不推送、不发包，不启动v2.4任务。
