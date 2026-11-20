# D4 Stage 恢复后的四文件修复

恢复会话 `01a1ee8a-f384-74b3-a0e4-cfebec64373d` 后，主会话实际运行
`resume-2026-11-18/d4-recovered-device-14`：30 方法中 26 通过、4 失败。
本代理只负责其中三个 Stage 用例；Reader 由另一实施者拥有。

## 写入范围与冻结

路径相对 `app/src/`：

- `main/java/com/example/myapplication/ui/component/roleplay/RoleplayDialoguePanel.kt`
- `main/java/com/example/myapplication/ui/component/roleplay/RoleplayInputBarComponents.kt`
- `main/java/com/example/myapplication/ui/screen/roleplay/RoleplayScreenContent.kt`
- `androidTest/java/com/example/myapplication/ui/screen/roleplay/CompanionStageScreenTest.kt`

四文件已释放给主会话统一格式、编译和设备复验。未改 `CompanionStage.kt`、
共享 `CompanionD4UiFixture.kt`、保护项 `RoleplayScreen.kt`、原 PNG/schema、
父任务/spec/版本/更新 JSON。不是本轮工程或 D4 验收通过声明。

## 三个失败的处理

### 窄屏大字整屏正文不可见

原主屏把标题、显示工具栏、AI 建议、200% 字体堆叠输入和 Stage 同时放在
剩余高度内。Stage 正文虽使用 weight，但其它非加权内容可消耗其全部空间；
原来只在 IME 出现时收起标题，显示工具栏与建议仍占高度。

- 只有有正式 Stage 插槽且字体至少 1.5 或实际面板不足 `48dp × 8` 时，
  AI 建议改由现有输入区的「AI 帮写」按钮打开原 `RoleplaySuggestionSection`。
  Dialog 有界、固定关闭，关闭与系统 Back 不触发建议生成。
- Stage 的 IME 显示期间暂时收起显示工具栏与标题收起柄，关闭键盘后恢复。
- `RoleplayInputBar` 追加 `compactInlineEditor: Boolean = false`；仅上述
  Stage 条件显式传 true，当前行最多一行、占位文字不增高，长草稿仍保留原值
  和现有完整编辑入口。旧调用仍为三行输入、原占位溢出模式。
- 演出正文的最大行数、原文、parser/来源、播放器、皮肤及持久几何全部未改。
- 原窄屏真实主屏/平台 200%/IME/完整正文行/48dp/十项快捷/零请求断言保留，
  加上「AI 帮写」的可达性与 Back 只关建议 Dialog 的断言。

### 切 LIST 后首行仍在视口外

原夹具把 `performScrollTo().assertIsDisplayed()` 当作全部文本行入视口的保证。
它只能证明目标当前可见；14 轮实际首行仍被裁切。现在先真实触控上翻，
按实际 `TextLayoutResult` 的第一行与列表视口对齐，再保留原首行/末行/
无省略强断言。新增 `roleplay-list-complete-canonical-body` 候选与真实几何，
不能用候选生成替代方法通过。

### HUD 最后一次 Back 关闭 Activity

生产宿主 `ui/navigation/CompanionRouteContent.kt` 在 ACTIVE 安装自己的
BackHandler；单独渲染 `RoleplayScreen` 的设备夹具遗漏该 owner，所以最后
一次 Back 落到 ComponentActivity。HUD 与窄屏整屏夹具仅补同一回调的宿主
BackHandler；HUD/IME/Dialog 先消费、最后离场和零 send/retry/generate 断言不变。

## 尚待主会话实测

本代理未运行 Gradle、formatter、设备、收费模型或 TTS。
建议统一编译/Lint/dev 与 AndroidTest 包，然后重跑完整三类 30 方法；
当前三个失败尚不能记为已通过。LIST 的完整几何及窄屏 IME/快捷候选要和
新源码冻结 SHA 对应。已通过的纯 Stage 打字、HUD、真实坏图与拖动取消
不得被上述修复回归破坏。

## 2026-11-19：修复前的布局诊断检查点

17 轮实际 31 方法为 29 通过、2 失败。HUD 宿主返回已通过；仍剩原窄屏整屏
正文不可见，以及原 LIST 正文最后一行不完整。先保留该生产检查点，只追加
`CompanionStageScreenTest.kt` 的失败前取证，等待主会话运行两个原方法。

- `roleplay-stage-before-display-assertion`：首次正文可见性断言之前保存完整
  主屏 Canvas PNG、平台/布局 fontScale、各层 bounds/未裁切 position+size、
  正文 TextLayout 全部行的首尾位置与省略状态。
- `roleplay-list-before-complete-assertion`：LIST 首末行断言之前保存相同资料。
- 如继续到后续步骤，IME 和快捷展开的一整行断言前也保存相应主屏证据。
- 原 320dp/200%、长原文、首末行/一整行、48dp、零请求与 Back 强断言不变，
  两方法仍允许真实失败；候选在断言之前已写入合成缓存，失败后可由主会话拉取。
- 仅同时将测试私有 `@Composable stage` 及全部调用改为 `Stage`；所有
  `@Test` 名称与 12 个 Stage 方法数保持。

此短检查点没有生产修复，不推断剩余失败的具体高度根因。
主会话取得实际 PNG/几何后再做整个主屏预算修复，随后处理输入快捷 Dialog
的实际窗口高度 Lint。未运行构建、设备或 formatter。

## 20 轮后的测试诊断修正

20 轮两个方法实际均失败，生产来源不变。只有 LIST 的 PNG/TXT 成功保存；
窄屏诊断提前调用全文节点 `fetchSemanticsNode()`，在节点不匹配时抛错，未保存
窄屏 before。不能将这种全文 no-match 直接解释为 Stage 视口被挤空，也需检查
当前打字片段、首帧点击、ADVANCE 的 Disabled/标签以及 PROGRESS。

本检查点仍只改测试：

- 诊断从 host 开始，无全文节点也记录 `text.count=0`；finally 保存整个主屏。
  同时保留 ADVANCE/PROGRESS/beat 的合并与未合并语义、当前部分 Text、按钮
  Disabled/OnClick、各层几何和实际平台/布局字体，不再以前置正文存在作为取图条件。
- 真实正文使用未合并的 TextLayout owner，并用 `layoutInfo.coordinates`
  计算行坐标。20 的 merged 气泡为 656×508，而其 TextLayout 为 540×444；
  用气泡 origin 配文字行偏移会漏计 padding。新证据同时记录 owner 与 layout 尺寸。
- LIST 的 444px/9行正文高于389px消息视口，保留原长文，真实上翻验证完整首行，
  再真实下翻验证完整末行；所有行仍检查无省略，原文/来源/零请求强断言不变。
  首尾各有独立真实 PNG/行几何，不再要求不可能的长段落同屏容纳所有行。
- `roleplay-list-before-body-query` 先保存查询前主屏；首/尾断言前分别保存
  `roleplay-list-before-complete-assertion` 和 `roleplay-list-before-tail-assertion`。
  窄屏 before 与后续 IME/快捷诊断名称保持。

尚待主会话编译并重跑原两个选择器。没有做生产预算修复，没有更改列表皮肤、
行高、全局几何、原始正文或设备断言容差。

## 23 轮测量确认与最终三文件增量

23 轮两个方法实际为 1 通过、1 失败。LIST 已通过原144字、真实首末行、IME、
旧快捷及零请求，首尾 PNG 保留。窄屏 before 的平台与布局均为320dp/600dp、
density=2、fontScale=2，ADVANCE 已启用并显示「下一拍」，PROGRESS为1/2。

**原窄屏失败的确认根因是测试把一段逻辑正文等同单个 Text 节点。** 实际
Stage beat 内有85、85、34字的三个 Text，总和正好是原204字；576×200px正文
视口中第一段真实行620..706完整位于596..796。此前「查不到全文就是正文
视口被挤空」的解释不成立。原生产阅读分段、正文、皮肤、字号和行高保持。

本次精确源码范围：

- `CompanionStageScreenTest.kt`：Beat范围内按实际段序和节点ID定位未合并 Text，
  不以重复85字段落作唯一文本匹配；所有段按顺序拼接严格等于204字，逐段无省略。
  选真实可见段测完整行，IME/AI帮写/旧快捷/Back/48dp/来源及零请求继续完整执行。
- `CompanionStage.kt`：23 PNG中的1/2 owner宽80px却只显示52px，最后一个字符
  被控制行横滚裁掉。拍数移到控制行固定末端，操作区仍可横向滚动；不改字号、
  文本、播放器或几何设置。测试增加拍数整行四边/无省略与动作滚入后的48dp证明。
- `RoleplayInputBarComponents.kt`：仅D4新增快捷 Dialog的最大高度改用该弹窗
  当前 `LocalWindowInfo.containerSize` 按实际density换算，原默认与有界快捷/关闭保持。
  未修改全局 `NarraAlertDialog` 的既有屏幕配置代码。

本轮没有再改 `RoleplayScreenContent.kt` 或 `RoleplayDialoguePanel.kt` 的顶部预算。
三文件交主会话统一格式/编译/Lint/双APK、31设备和受影响旧截图；本代理未执行
这些命令，不能从23的before或新测试存在宣称本次修复已验证通过。
