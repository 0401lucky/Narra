# D4 已解析正文显示保真修复

2026-11-19，`/root/d4_rendered_text_fix`。主会话解除 26 检查点冻结后实施；本代理不运行
Gradle、设备或 formatter，不修改父任务、spec、版本、schema、PNG 或用户数据。

## 确认原因

正式 `companionBeat.content` 已经由 D3 解析、解码。Stage 的角色/用户气泡及 Reader 的
思想/旁白/对白继续调用旧零参 `toLongformParagraphs()`，会再次剥掉字面 XML 标签，
并在旧 `SmartParagraphHardMax=144` 无标点分段时按 UTF-16 index 硬切。Player 虽然只
发布完整 grapheme 前缀，后续多个独立 Text 仍可能拆开代理对或组合/ZWJ 簇。

## 最小源码范围

路径相对 `app/src/main/java/com/example/myapplication/`：

- `roleplay/companion/CompanionDisplayParagraphs.kt`：纯已解析段分配，保留全部字符，
  不清洗标签、不展开实体、不 trim/归一化空白。沿用 48/92/144、优先标点和短尾合并
  的显示策略，实际切点来自现有 Player 的 grapheme ends，单个超长簇仍完整保留。
- `roleplay/companion/CompanionBeatPlayer.kt`：只把既有 `companionGraphemeEnds` 的
  可见性 private 提升 internal；算法、单份 Unicode 表、Player API/计时/源行为不改。
- `ui/component/roleplay/RoleplayLongformText.kt`：保留旧零参函数原样；新增显式
  `parsedCompanion: Boolean` 重载。false 委托旧零参，true 使用纯已解析分段。
- `ui/component/roleplay/RoleplayDialogueBubbles.kt`：两个非流式正文分支传入
  `message.companionBeat != null`，remember 键包含该 flag；原保护 UI 的其它 hunk、
  配色、几何、输入、边框和回调保持。
- `ui/screen/roleplay/RoleplayReadingMode.kt`：思想、旁白和对白三处相同的伴随
  flag 与缓存键适配；旧非陪伴/普通/长文零参行为及原样式保持。

没有修改 `RoleplayLongformMarkupParser.kt`、持久字段、raw/span/source revision，
也没有从阅读/显示动作引入模型请求。

## 新增回归与交接

- JVM `CompanionDisplayParagraphsTest` 新增 7 个方法：字面标签/实体样文本、旧默认、
  精确保留空白、原中文标点偏好、跨 144 的代理对、组合/ZWJ/旗帜/Indic/Prepend/CRLF
  边界和超长单簇后的循环推进。每段独立检查 UTF-16，不仅拼回全文。
- Stage 新增 `parsedLiteralTagsAndHardBoundaryClusters_keepEveryRenderedTextFaithful`：
  真实气泡对白/旁白 TextLayout 与源/raw，字面标签和长 emoji/组合/ZWJ 簇完整；原
  12 个方法不改，Stage 现为 13 个方法。
- Reader 新增 `parsedTagsAndLongJoinedCharacters_remainFaithfulInActualReadingTexts`：
  真实思想/旁白/对白 TextLayout、每个独立 Text 的代理对与完整簇、完整文字/原文/
  修订及零读取/刷新/离场/选区误触发；原 11 个方法不改，Reader 现为 12 个方法。

本轮新增 7 个 JVM 和 2 个设备方法；三类 D4 原生合计应为 33（13+12+8），须由主
会话 APK 枚举后实际执行。Player 原 27 方法和旧段分配回归也应在可见性复用后复验。
定向 `git diff --check` 无空白错误（只有已有 LF/CRLF 提示）；未执行编译或测试，
当前不代表这些方法通过，也不代表 D4/2.4.1 验收通过。源码现冻结，主会话统一格式、
JVM/编译/Lint、33 设备和受影响旧截图 verify；失败须按真实报告继续定向修复。

## F5 连续空白的 annotated 消费边界

独立检查确认已解析正文可通过 `&#32;` 一次解码得到连续空格，中间显示段因此可以
完全为空白。旧 `buildDialogueAnnotatedString` 的 `isBlank` 早退会丢掉该段。

主会话在工程 29 完成后解除指定六文件冻结。本轮保留两种旧三参 annotated builder
的签名和空白默认，新加显式 `parsedCompanion` 重载；仅 true 允许原样 append 空白，
Character 气泡及思想/旁白/对白 Reader 透传 flag，Character 的第二层 remember 也
加入 flag。零参段分配、旧普通渲染与用户几何保持。

- JVM 当前 `CompanionDisplayParagraphsTest` 新增第 8 个方法：旧/false 全空白仍为空，
  true 精确保留；实际分段后经两种 annotated builder 拼接保留全部 300 空格。
- 现有 Stage F5 方法追加实体空格对白拍，Reader F5 方法追加思想/旁白/对白三种实体
  空格拍。从真实 TextLayout 保留所有非空段（含纯空白），精确拼接合法解码后正文，
  显式证明纯空白中间 Text 未丢。两种原有 F5 标签/emoji/组合簇强断言继续保留。
- Stage/Reader 方法名称和数量保持 13/12；三类原生仍为 33。没有运行本轮编译、
  formatter 或设备；实际行为需主会话按新 SHA 再验证，不能沿用工程 29 的通过结果。

仅 LongformText、DialogueBubbles、ReadingMode、现有 JVM helperTest 和 Stage/Reader
两个测试源有必要变更；`git diff --check` 无空白错误。该六文件再次冻结并交还主会话。

## Reader 新增方法的单项诊断

主会话工程 31 实际 45/45 JVM、编译及双 APK通过；设备 32 的 33 项中 32 通过。
Stage 的新增回归已通过，Reader 旧 11 项和 Visual 8 项也通过。唯一 Reader 新方法
在正文 layout 过滤后为空处失败，原始错误没有指出当前源项或实际列表位置。

本代理只修改 Reader 测试与本记录。静态核对 LazyColumn 永远保留
`companion_reader_older` 头项，所以源 index+1 的索引对应未被修改；原 Text 查询、
过滤、原文/独立字符/标签/300 空格和全部行为断言保持。新增断言前诊断记录：

- 请求 index/item/contentType、源 items 序列、当前 firstVisible、真实 visible key/index/
  offset/size、视口起止、滚动状态、最后锚点与 followingBottom。
- 目标 item 的 unmerged 节点几何、原作用域 Text 节点、全部 TextLayout 和过滤后
  的数量及真实文本（空格在取证文本中可见化，不改渲染数据）。
- 当前整个 unmerged semantics 的节点与配置，保存实际 Reader PNG/TXT 到
  `d4-candidates/reader-parsed-before-item-{index}`。失败断言包含源项和当前列表 key。

仅 `CompanionReadingScreenTest.kt` 一文件有诊断性源码变化，方法名称/数量仍为12。
未改生产、滚动策略或查询来绕过失败；只做 `git diff --check`，未运行编译、设备或
formatter。源再次冻结，等待主会话定向单方法取证，根据真实结果再处理。

## Reader 35 取证后的真实回看驱动

35 的第 0 思想项已成功查询完整正文；随后请求第 1 旁白项时，真实列表移到 index
5/6，followingBottom 仍为 true，目标作用域没有节点。数据证明不是旁白未渲染或
标签错误，而是新增用例把 `performScrollToIndex` 当作用户回看，未解除跟底所有权。

仅修正这一新增测试的方法驱动：先复用既有 `scrollIntoHistory` 真实触控退出跟底，
随后各源项按既有触控→索引滚动→触控模式回看，等待当前布局，严格校验源段 itemId
对应真实 visible key/index 与目标标签可见后，再读取完整 TextLayout。六个 beat 属于
同一 canonical 消息，不用 messageId 代替段身份。不修改 following flag、Fake 状态、
生产代码、查询、超时或旧 11 方法。

原文、标签、独立字符、连续 300 空格、source/raw/零回调的全部断言保持；诊断仅在
当前目标或过滤后正文缺失时保存，减少成功时的整页取证开销，并保留原 35 日志和
before 图片。Reader 方法数仍 12，三类设备总数仍 33。定向 diff 检查通过，未运行
Gradle、设备或 formatter；单个 Reader 测试源再次冻结，交主会话实际复验。
