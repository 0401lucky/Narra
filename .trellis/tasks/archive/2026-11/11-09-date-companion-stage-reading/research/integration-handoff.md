# D4 集成实施交接（2026-11-18，核心检查点）

状态：**完整产品与本实现者 44 项 JVM 源码已写入并冻结，等待最后整组验证。**
本记录不是 D4 AC 通过或 v2.4.1 完成交付的声明。

## 已写入

路径相对 `app/src/main/java/com/example/myapplication/`：

- `model/CompanionVisuals.kt`：角色图片/用途、套装表达键、布局、默认列表偏好、稳定资源引用与回退候选。
- `model/CompanionReading.kt`：独立阅读 scope/binding、时间+ID keyset、原文/分段锚点、SpeechUnit 与跨源选区数据。
- `model/CompanionContext.kt`：现有小映射/patch 增加可选 `visuals`，旧构造默认保留。
- `data/local/CompanionVisualCodec.kt` 与 `CompanionSettingsCodec.kt`：逐字段容错、同 ID 未知扩展保留、未知高视觉版本拒绝降级、expectedRevision CAS。
- `roleplay/companion/CompanionResourceResolver.kt`：套目标表情→套默认→旧本地图/旧远程图→头像→姓名；仅 PORTRAIT 可作主体，目标缺失不沿用上一拍。
- `data/repository/roleplay/CompanionResourcesRepository.kt`、`AndroidCompanionResourcesRepository.kt`：复用 LocalImageStore 私有复制，实际 bounds+采样像素解码，导入后 CAS，清理仅本实例未发布的新导入文件；保护 settings/scenario/消息附件引用。
- `data/local/chat/ConversationDao.kt`：只新增查询，无表/schema 改动。授权角色/persona/space/run、单聊、source 修订/可见性及实际选中 COMMITTED 回合在 keyset 查询前筛选。
- `data/repository/roleplay/CompanionReadingRepository.kt`、`RoomCompanionReadingRepository.kt`：从实际 session/binding 建独立阅读授权，不 enterScenario、不扩大模型历史 scope；连续用途才跨绑定，独立/legacy 用途限当前 conversation/run。游标使用值而非查游标行；刷新按原已展开窗口边界读取。
- `roleplay/companion/CompanionReadingSupport.kt`：保留源/分段锚点，去重与排序，复用原 UI mapper；独立的稳定 SpeechUnit/跨源范围接口。
- `viewmodel/CompanionReadingViewModel.kt`：本地窗口、前页、刷新、锚点、选区、权限失效和错误恢复状态；不依赖主 RoleplayViewModel。

Player 文件归 `/root/d4_player`，本实现者未编辑。原 12 个 UI 漂移文件、旧 schema、截图、构建版本与更新 JSON 均未修改。RoleplayScreen 的最小默认-null adapter 已由主会话串行授权，尚未写入。

## 主会话实际反馈

- `d4-core-01`：编译失败，0 测试执行；缺 VisualValidation import 与 Long 范围类型已定向修复。
- `d4-core-02`：产品/JVM 编译通过，1437 源码前后一致。Player 26 实际执行，25 通过、1 项组合文字 clusterCount 失败，已交独占 Player 实现者修复。
- 本实现者未运行 Gradle/formatter/设备；这些均由主会话唯一执行。没有宣称 Player 26 全过。
- 后续 `d4-player-03` 由主会话确认 27 个 Player 方法全过；组合字符修复后没有删预期。Core 10 文件与 Player 2 文件由主会话定向 IDE 格式化，范围外文件未变。

## 第一组 UI/AppGraph 接线（待首轮编译）

- 新增 `viewmodel/CompanionDisplayViewModel.kt`、`CompanionVisualSettingsViewModel.kt`；显示与素材草稿完全没有文本请求依赖。显示保存冻结角色 owner，解析有代次/ensureActive；阅读异步任务同样有 owner 代次与取消后回填守卫。
- 新增 `ui/screen/roleplay/CompanionStage.kt`、`CompanionReadingScreen.kt`、`CompanionVisualSettingsScreen.kt`、`CompanionTextGestures.kt`。Stage 使用原低层气泡和阅读段落，不使用旧消息菜单空回调；显式补全/下一拍/重播、片段与 HUD；长按在同一次 UP 交菜单，拖动/取消拒绝交出点击和选区。
- Reader 真实渲染图片 parts/附件及已保存 AI_PHOTO/GIFT 图片，Coil 解码后的真实比例进入列表重排；列表依源/段 key 和像素锚点恢复。选择起点/终点可跨场次，提供 nullable 选区取消回调。
- 原 `RoleplayReadingMode.kt` 只增复用段落入口，旧页面默认入口/颜色/段落不改。`RoleplayDialoguePanel.kt` 增默认-null 消息视口插槽，Stage 时取消旧列表 auto-scroll effect，原输入、建议、附件和快捷回调仍由原面板处理。
- `RoleplayScreenContent.kt` 增演出/列表/独立阅读/素材入口，并限制辅助操作滚动区；Stage 不重复旧 presence 立绘。正式播放只取当前 turn.replyMessageId 对应 COMPLETED/non-streaming/有效 metadata 回复。
- 唯一已授权保护 UI 文件 `RoleplayScreen.kt`：追加默认-null `companionDisplay` 参数、原 SceneContent 透传、旧 D3 HUD gate 在 Stage 时抑制双入口。所有既有几何/主题/快捷/输入 ack 保留。
- 新增 `ui/navigation/CompanionPresentationRoutes.kt`，`AppRoutes.kt` 增 URI 编解码配对的 Reader/Visuals 路由，`RoleplayNavGraph.kt` 接 activeCompanion 显示状态及新路由；设置进入阅读仍先等原 saveSettings 成功回调。新 Reader 不创建/切换当前运行。
- `AppGraph.kt` 装配资源与独立阅读仓储，旧场景删除的图片清理先检查完整引用，保护其它场景/角色/套装/消息共享文件。
- `sceneOverrides` 实现本场明确外观高于角色默认；SpeechUnit 增 `contentType`、原始 span 文本 `rawText` 与后续语言接口。ReadingCursor 与 Support 使用 SQLite BINARY 的 UTF-8 比较，不改 D2 请求 Cursor。
- root 独占 `AppSettingsStoreCompanionVisualTest.kt` 的 10 个真实 DataStore 用例；native_tests 独占三个新 Screen AndroidTest 与 D4 窄屏平台 fixture。其余 JVM/Room/文件测试仍由本实现者补齐。

## 工程/设备实际反馈与最后整组修复

- `d4-ui-engineering-05`：3 处 BoxWithConstraints receiver 编译错误，0 测试；已在外层捕获 height 修复，不删试验期待。
- `d4-ui-engineering-06`：产品/JVM 编译通过，Player 27 + StoreVisual 10 + 旧 Writing 11，共 48 实际方法全过。随后 AndroidTest 的缺 import 由主会话单行修复。
- `d4-ui-engineering-08`：AndroidTest 编译、Lint、双 APK通过，1449 源前后一致。
- `d4-device-09`：专用 AVD 27 实际方法，21 通过、6 失败；3 个 finally softInputMode 由 native_tests 修复，3 个生产问题由本实现者修复。不能把候选 PNG 生成算设备方法通过。
- 原 `StageScreen:335` 的日志是快捷面板“邀约”不显示，当前源当时并无 LIST 正文断言；已按真实失败修复，并由 native_tests 补 LIST 正文真实首末行。Stage 模式复用原 QuickActionPanel 与原 dispatch，改用独立有界窗口；旧默认 inline 路径保持。
- 失效表情图：独立 `key(candidate)` 图像状态，避免上一候选 Error 跨 compose producer key 跳过真实默认图；Error/Empty 继续有序回退。
- 删除首可见来源：窗口更新时暂停 snapshot 捕获，恢复完成后才提交真实邻居及 offset，避免旧 65px 被先写到新来源。
- Stage 的 IME 聚焦时暂时折叠顶部 chrome，portrait 按真实 fontScale/正文/操作预算约束，原持久布局不变；HUD gate 按当前来源角色，只有支持的协议/parser 能进入正式视口队列。
- Reader 绘真实背景并按本场/角色明确选择优先；正文继续不透明阅读面，原主题/段落/行高复用。
- Reader 的当前与候选 binding 用途均校验，并在 SQL 前置 purpose；连续入口不会混入同空间的 INDEPENDENT/LEGACY_COMPAT 候选。独立/legacy 仍限当前 conversation/run。
- LocalImageStore 在本次已知 target 的所有者内处理复制取消，含 IO 完成但尚未返回路径的竞态；文件 URI 按真实编码解析，保留旧 Android 路径语义。
- SettingsStore/AppSettingsStore 增只读完整 raw companion 引用；未来版本/kind/未知扩展仍保护共享文件，无法核对时保守跳过已发表物理清理。配置原 raw 不降级改写，复制取消和引用读取继续传播 CancellationException。
- Display 的 owner/解析代次、角色删除和迟到结果已覆盖；Reader 所有异步读取有代次与 tracked jobs；Visual 导入移除后无 dirty 的 close/save 同样清理本次临时图。

## 最后整组选取（源码已存在，尚未运行）

本实现者 JVM 8 类 / 44 个 `@Test`：

| Selector | 方法数 | 行为证据范围 |
| --- | ---: | --- |
| `com.example.myapplication.data.repository.roleplay.CompanionReadingPaginationTest` | 5 | 真实 Room 134 合法混合来源、单聊/角色/persona/空间/用途/selected attempt/待处理过滤、分页、删游标、保窗口、Unicode BINARY、image-only |
| `com.example.myapplication.data.repository.roleplay.CompanionResourcesRepositoryTest` | 8 | 真实 ContentResolver/私有 PNG/像素、CAS、坏文件清理、跨角色/场景/消息共享、未来 raw 与删除、原文/来源不变 |
| `com.example.myapplication.data.repository.LocalImageStoreCancellationTest` | 2 | 实际文件流 EOF 取消、关流、前后目录等同、编码 file URI 与共享文件保护 |
| `com.example.myapplication.roleplay.companion.CompanionReadingSupportTest` | 5 | 原始 span/内容类型/情绪、跨两会话选区、修订/删除失效、emoji边界与段落锚点 |
| `com.example.myapplication.roleplay.companion.CompanionResourceResolverTest` | 4 | 套装/表达键/本场优先级、DECOR/缺图、旧图/头像/姓名默认 |
| `com.example.myapplication.viewmodel.CompanionDisplayViewModelTest` | 6 | 默认偏好、保存失败、冻结 owner、NonCancellable 迟到、角色删除 |
| `com.example.myapplication.viewmodel.CompanionReadingViewModelTest` | 6 | 展开窗口/刷新/失败重试、删边界、opening/older/refresh 的角色删除迟到 |
| `com.example.myapplication.viewmodel.CompanionVisualSettingsViewModelTest` | 8 | SavedState 草稿/CAS、导入后设置变化、失败保稿、无 dirty 的清理、成功与失效角色/高版本 |

测试支持：`testutil/CompanionD4SettingsStore.kt`，仅测试 Fake，所有 Fake 请求点有计数或明确错误，不接模型。

加独占 Player 27 与主会话 StoreVisual 10，本块 JVM selector 共 81 方法；旧 `AppSettingsStoreCompanionWritingTest` 11、`LocalImageStoreTest` 与受影响阅读/列表回归另计。
native_tests 当前 4 源已冻结，30 方法（原 27 + Stage HUD 2 + 真实整屏 320dp/200%/IME 1）；主会话统一实际设备运行与截图 verify。

## 下一组必要工作

1. 主会话统一最后整组 IDE formatter、编译、81 项本块 JVM 与必要旧回归、Lint/双 APK及30设备。
2. 按实际报告修复、定向复验；独立审查核对 AC 与源码/验证对应。
3. 按实际受影响范围截图 verify，候选与旧漂移分开；未接纳的原基准保持。

## 当前限制

最后整组尚未验证，不能据以前编译结果称当前源码通过。09 的真实失败和原始证据保留；修复后的设备效果仍待复验。原有视觉基准未接纳，已发表无引用素材采取保守保留；仅本次未发布导入可清理。D5/D6 保持后续依赖，不调用收费模型/TTS，不发布或提交。
