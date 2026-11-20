# D4 原生测试交接（2026-11-18）

状态：**四个测试源已冻结并释放给主会话；当前 30 个方法，等待编译和设备复验。**
本代理未运行 Gradle、formatter、设备、模型或 TTS，不以源码存在代替通过。

## 文件与选择器

以下源码路径相对 app/src/androidTest/java/com/example/myapplication/ui/screen/roleplay/。

| 文件 / instrumentation class selector | 方法数 |
| --- | ---: |
| CompanionStageScreenTest.kt / com.example.myapplication.ui.screen.roleplay.CompanionStageScreenTest | 12 |
| CompanionReadingScreenTest.kt / com.example.myapplication.ui.screen.roleplay.CompanionReadingScreenTest | 10 |
| CompanionVisualSettingsScreenTest.kt / com.example.myapplication.ui.screen.roleplay.CompanionVisualSettingsScreenTest | 8 |
| CompanionD4UiFixture.kt | 共用夹具，无测试方法 |

本次解除冻结后只修改 Stage 测试、共用夹具及此交接；保留主会话已修的 Reader
onAllNodesWithTag import 和真实 DpRect bottom-top/right-left 比例测量。
产品代码、JVM、现有夹具、spec、父/版本文档均未编辑。

## 已取得的真实结果

主会话 resume-2026-11-18/d4-device-09/device-summary.json 完整执行旧 27 方法：
**21 通过，6 失败，无跳过、无 runner error。** 不将各轮相加。
Reader 图片真实解码/4:1 比例重排保锚已包含在这 27 项中并通过。

三个窄屏方法在共用夹具 finally 失败：保存值 0，实际仍为 16。主内容、
平台/Layout/TextLayout 字体 2.0、滚尾和候选生成已执行，但整个方法仍记失败。
另三个失败由集成代理修产品：

- Reader 删首可见来源后已到正确邻居，但偏移应 0，实为 65。
- Stage 坏表情图回退默认图的真实 image tag 5 秒内未出现。
- 完整 RoleplayScreen 的快捷格子第二排“邀约”不可见。

当前新增三个方法尚未编译/运行；原 27 场景全部保留。旧整页方法补强
STAGE → LIST 后第一拍生产 Text 的完整首末行进入真实消息列表视口，
保留原 10 项快捷可见、IME、输入和零 send/retry/generate 回调断言。

## 窗口恢复根因与修复

[AOSP android16-release Window.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/core/java/android/view/Window.java)
的 setSoftInputMode 在 mode 为 0 时仅执行 mHasSoftInputMode = false，
没有执行 attrs.softInputMode = mode。因此直接调用 setter 不能把本次指定的
ADJUST_RESIZE=16 写回保存的 UNSPECIFIED=0；不是需要容忍的字号/布局差异。

夹具仍先 dispose Composition/独立 Dialog、移除专用 ComposeView、恢复原宽高；
随后精确恢复保存的 LayoutParams.softInputMode，再调用 setter 恢复平台的
UNSPECIFIED 标记。原 mode/宽高/基础 Resources 的精确断言全部保留，
候选恢复元数据新增 softInputModeBefore/After。需要实际复验确认最终为 0。

## 三个新增方法

- stageHud_gateRevisionAndMalformedObservation_keepLocalPlaybackAndBackOwnership：
  实际 CompanionStage 内 HUD，开关/Back 不改整个播放器状态；重新开启读取旧
  snapshot label；revision 变化关闭旧 HUD、旧 timer 到达不改新拍；坏块保正文、
  无 HUD；覆盖层 Back 与宿主 Back 分层，选区回调没有非空误触发。
- roleplayScreen_stageHud_usesCurrentGateAndOldLabels_andRejectsMissingSourceMetadata：
  实际 RoleplayScreen → CompanionStageViewport 按来源角色当前 settings gate；
  恰好一个 HUD，历史 label 不被当前 label 改写；缺 metadata 不进正式队列
  （0/0），原列表正文仍可读；Back 及零 send/retry/generate 回调。
- narrowRoleplayScreenFont200_stageInputQuickActionsAndIme_keepAWholeBodyLineAndNeverSend：
  实际完整 RoleplayScreen、Stage slot、生产输入栏和旧快捷；配置 Context、
  专用 ComposeView、Resources/Layout/TextLayout 均核对 200%；在真实 IME 和
  快捷展开状态分别量到至少一整行正文，输入/补全/更多的真实触控至少 48dp；
  10 个快捷可滚动到达；Back 依次消费 IME、查手机 Dialog、离场；
  send/retry/generate 为 0。没有另搭简化 TextField 代替生产输入。

## 证据与边界

原生 PNG / 实测元数据只写合成测试 app cache 的 d4-candidates/：
Stage 末段、Reader 末记录/真实图片保锚、Visual 最后控制项/撤稿 Dialog，
以及三页平台恢复文本。新增完整 RoleplayScreen 候选名称为
roleplay-stage-320dp-font200-ime 和 roleplay-stage-320dp-font200-quick-actions。
主会话负责取回、哈希和审阅；没有修改截图 baseline/manifest 或执行 record。

窗口使用配置 Context 和专用 ComposeView，核对真实 Android Resources、
实际布局与 TextLayout fontScale=2.0，不用单独 LocalDensity override 证明大字。
字体动态重排另设用例；Reader 的独立输入控件只提供真实 IME/Insets。
48dp 检查真实 touch bounds；长按取消保持同一指针 down→hold→move→up，
不是后续第二次 swipe。

视觉设置测试直接消费真实 Screen 的草稿/保存/错误/冲突/导入状态并驱动回调。
仓储 CAS、Room 授权、共享资产删除闭包、真实请求计数及当前 encounter 权威
由集成/主会话领域测试承担，不能用这里的 UI state fixture 冒充这些结果。
真实模型/TTS、完整皮肤/旧截图回归与视觉接纳不在本测试通过声明内。

## 冻结点（主会话 formatter 前）

| 文件 | SHA-256 |
| --- | --- |
| Stage | 058B72768AFE94058C7AFAFA45702DCB8917FB0C68A673C8FFDD138C5406F8B9 |
| Reader | 95DF5C0D5BB545E4058358AA08753D797F27C5ADA6B2A5CD7F9BAB10F057962E |
| Visual | 21E59AEC2FFD2767A5C22F43B7B00861B9DA95D8100609F9E9BDD29F7C7C73AA |
| Fixture | 1322ACC7891A7426A052A4E7213869B0C4A13D442F816B6E5BCC5559D1C99511 |

后续真实编译/设备失败由主会话 followup；四个测试文件当前交由主会话统一验证，
不会在冻结期间继续写入。
