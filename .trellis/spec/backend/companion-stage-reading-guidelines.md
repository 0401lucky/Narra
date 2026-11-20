# 陪伴演出、素材与连续阅读

## 1. 适用范围

修改本地逐拍播放、演出素材、跨场阅读、滚动锚点或声音选段时阅读。正文与
来源投影遵守 [正文表现](./companion-presentation-guidelines.md)，运行资格遵守
[可靠回合](./companion-foundation-guidelines.md)，模型授权仍由
[入口与上下文](./companion-entry-context-guidelines.md) 定义。

以下源码路径相对 `app/src/main/java/com/example/myapplication/`。D4 的实际验证
记录在 `.trellis/tasks/11-09-date-companion-stage-reading/research/`；接口文档不代替
对应源码版本的测试结果。

## 2. 接口

```kotlin
// roleplay/companion/CompanionBeatPlayer：本地显示状态，无请求/仓储依赖。
val state: StateFlow<CompanionBeatPlayerState>
fun setSource(reply: ParsedCompanionReply?)
fun advanceOrReveal()
fun replay()
fun stop()
fun close()

// data/repository/roleplay/CompanionReadingRepository
suspend fun authorize(scenarioId: String, characterId: String): CompanionReadingScope?
suspend fun refreshScope(scope: CompanionReadingScope): CompanionReadingScope?
suspend fun page(scope: CompanionReadingScope, before: CompanionReadingCursor? = null,
                 limit: Int = 40): CompanionReadingPage
suspend fun readWindow(scope: CompanionReadingScope,
                       oldestInclusive: CompanionReadingCursor): List<CompanionReadingRecord>
fun observeChanges(scope: CompanionReadingScope): Flow<Unit>

// data/repository/roleplay/CompanionResourcesRepository
suspend fun importImage(uri: String, kind: CompanionImageKind, name: String): CompanionImageResource
suspend fun save(assistantId: String, expectedRevision: Long, visuals: CompanionVisualSettings,
                 importedPaths: Set<String> = emptySet()): CompanionSettingsWriteResult
suspend fun discardImports(paths: Set<String>)
```

`ui/component/roleplay/RoleplayInputBar` 的可选参数
`compactInlineEditor: Boolean = false` 保留旧调用兼容；默认输入最多三行、占位文本
Clip。只在 Stage 布局压力下显式启用一行编辑器及一行省略占位，输入原值仍完整。

仓储、Store 和 ViewModel 继续在 `AppGraph` 装配。阅读与素材路由使用
`AppRoutes.roleplayCompanionReading` / `roleplayCompanionVisuals`，参数编码与读取
配对；打开旧阅读不调用 `enterScenario`，不改变当前生成或重生成目标。

## 3. 数据与生命周期合同

### 正式来源与播放器

- 宿主只把已完成、非流式且 metadata 有效的 D3 回复加入正式队列；缺来源或
  不支持 protocol/parser 时保留原列表正文，不从当前配置补造正式来源。
- 比较完整 `CompanionReplySource`、raw 与 beats；相同输入重组不重启，新源或
  修订撤销旧时钟和选段。拍集合是本次快照，不写 canonical 或来源 revision。
- TYPING 的一次点击只取消旧 timer 并补全本拍；下一次才推进。末拍后 END，
  重播只使用本地快照。单一 typingJob 和 generation 拒绝取消后的迟到回填。
- 可见前缀按完整 grapheme 展开，保留中文、代理对、组合字符、emoji 和 GB9c
  Indic 连写；不能按单个 `Char` 截断。界面在 RESUMED 生命周期拥有播放器。
- 已解析 `companionBeat.content` 只交 `CompanionDisplayParagraphs.split` 分配显示段，
  不再次剥标签、解码实体、trim 或归一化空白。字面 `<narration>`、`&#65;` 及实体
  一次解码后的连续空格都是正文。48/92/144 的标点优先切点使用 Player 同一份
  grapheme ends，超长单簇完整保留，不能在两个独立 Text 之间拆字符。
- 段分配与两种 annotated builder 显式透传 `parsedCompanion`；纯空白中间段也要
  原样 append。`remember` 同时包含正文和该 flag，Character 的 annotated 缓存也
  包含 flag。旧零参段分配与三参 annotated builder 的签名、JVM ABI 和默认行为
  保留，false 继续旧路径，不能为陪伴修复全局改写普通消息渲染。
- `stop` 可在重新进入演出时复用；`close` 或父 scope 取消永久释放。输入、滚动、
  菜单、观测和快捷操作各自消费事件，不隐式推进或触发文本模型请求。
- 正式 Stage 在大字体或面板高度不足时压缩输入/辅助区域，AI 帮写入口仍可打开
  复用原建议组件的有界 Dialog；打开/关闭只改显示状态，生成仍是原显式操作。
  IME 期间临时折叠显示工具栏与 chrome，隐藏键盘后恢复，不改持久偏好或正文行数。

### 素材与配置

- `CompanionVisualSettings.preferredMode == null` 保持旧 LIST 默认；只有用户主动
  选择才保存演出偏好。角色默认与 `sceneOverrides[scenarioId]` 的明确本场选择分开。
- 套目标表情 → 套默认 → 旧本地图 → 旧远程图 → 头像 → 姓名，有序尝试实际可读
  候选；运行时 Coil 解码失败继续下一候选。仅 PORTRAIT 能作主体，BACKGROUND
  与 DECOR 各有用途，上一拍表情不能压住本拍的默认回退。
- 资源以稳定 ID 与私有路径引用；最多 100 图、20 套装、40 表情映射/本场覆盖，
  名称最多 60 字。布局比例通过 `CompanionStageLayout.normalized()` 校验。
- 导入先在 `LocalImageStore` 复制并关闭流，再核 bounds 和采样像素；本地文件
  上限 32 MiB、单边 32768、总像素 100000000。IO 完成后才尝试角色 revision CAS。
- 复制取消由持有实际 target 的 `LocalImageStore.copyToAppStorage` 清理并重新
  抛出取消，包括 IO 写完但结果尚未回到调用方的竞态；不能仅靠外层收到 path。
- 素材草稿只清理本仓储实例新导入、未发表且无完整引用的路径。CAS 冲突保留
  草稿与临时图；成功发表后移出 unpublished，移除已保存引用不直接删除共享图。
- `SettingsStore.companionImageReferences()` 从完整 raw 配置提取引用；未知版本、
  kind 与扩展字段不能因 typed 投影丢失而被物理清理。场景、角色、消息 parts/
  attachments 同时参与 canonical 路径扫描；无法完整核对时保守保留已发表文件。

### 阅读、锚点与声音选段

- `CompanionReadingScope` 与 `AllowedCompanionHistoryScope` 无继承关系，不能作为
  模型请求授权。当前与候选绑定都校验角色/persona/空间/用途；CONTINUOUS 才
  允许连续候选，INDEPENDENT/LEGACY_COMPAT 只读取本 conversation/run。
- SQL 在 keyset 前校验单聊、实际绑定、来源修订/可见性和 selected COMMITTED
  attempt；Room 再核完整 domain 资格。不可用、未提交或无有效正文的来源不入页。
- `CompanionReadingCursor(createdAt, messageId)` 使用 SQLite BINARY 的 UTF-8 字节
  顺序；before/from 保存排序值，不重新查游标行。分页范围 1..200，limit+1 判断
  hasOlder，返回按时间升序；`readWindow` 保留已展开窗口，去重采用消息身份。
- 锚点保存 message/item 身份、cursor、像素 offset 与 followingBottom。前插、
  新答、图片解码、字体和 IME 重排时，上翻用户保锚，原在底部才跟随；删除首可见
  来源后恢复幸存邻居。窗口恢复期间不把旧布局的 offset 写给新来源。
- 用户拖动或惯性滚动中遇到窗口更新，协调必须采用最后的实际用户位置；不能
  禁止捕获直到停滚，再恢复更新瞬间的旧锚点而撤销后续手势。
- `CompanionSpeechSupport.units` 输出 `CompanionSpeechUnit`，包含完整 source、
  beatId、显示 text、原始 UTF-16 半开 rawSpan/rawText、speaker、contentType、
  独立 voiceEmotion 与可选 languageTag。范围偏移针对显示 text，不能当 raw 坐标。
- `select` 支持跨回复范围，拒绝非法边界、倒序、空选区及拆开的代理对；修订或
  删除后通过 `isCurrent` 撤销失效选段。D4 只提供选段数据与回调，声音模块拥有
  自己的网络/播放生命周期，不能复用播放器 generation 作为声音请求权威。

## 4. 校验与错误矩阵

| 条件 | 必须观察到的行为 |
| --- | --- |
| 打字中补全、旧 tick/finally 迟到 | 本拍完整，旧回调不能改字或推进 |
| 缺/失效来源 metadata | 原文仍可读，无正式队列或补造观测 |
| 非 PORTRAIT、坏图、缺表达键 | 按用途与有序候选稳定回退 |
| 导入取消、坏图片 | 流关闭，仅清理本次私有 target，取消继续传播 |
| 保存冲突、角色删除、未来版本 | 不覆盖新配置，草稿与失败反馈保留 |
| raw 引用读取失败或未知结构 | 已发表共享文件保留，不按 typed 空集合误删 |
| 阅读范围失效 | 拒绝读取/迟到回填，提供重新打开或重试反馈 |
| 删除游标行或首可见来源 | 可继续分页，恢复幸存邻居，不跳底 |
| 窄屏、大字体、IME/菜单覆盖 | 至少完整正文行与关键动作可达，Back 按层消费 |

## 5. 正常、旧数据与异常案例

正常：角色选择套装，本拍表情图缺失时使用套默认；同角色连续场次分页时保留
已加载旧页。旧数据：未选演出模式仍显示原列表与旧立绘，旧场图不永久覆盖后续
主动选中的套装。异常：当前连续空间内存在独立用途绑定时不纳入阅读；删除原
场景时另一个高版本 raw 配置仍引用同一图，实际私有文件必须保留。

## 6. 必需验证

- Player 虚拟时钟覆盖补全/下一拍/重播、源变更、同步重入、不可取消迟到与完整
  字符前缀；实际 Android Stage 再验证字符与事件所有权。
- `CompanionReadingPaginationTest` 使用真实 Room 混合 121+ 合法记录以及不同
  角色/persona/空间/用途、群聊、未选 attempt；断言不重漏、删游标仍续读与 UTF-8
  顺序。UI 保锚测试独立覆盖前插、图片比例、字体/IME、删除与底部跟随。
- 刷新与触控交错用同一指针 down→拖动→更新记录→继续拖动→up，核对松手后
  保留最后用户位置；两次独立 swipe 不能覆盖这个竞争窗口。
- `CompanionResourcesRepositoryTest` / `LocalImageStoreCancellationTest` 断言真实
  ContentResolver、私有文件和像素、CAS、EOF 取消、未知 raw 与跨角色/消息共享。
- 三个 ViewModel 覆盖失败保稿、冻结 owner、角色删除及 NonCancellable 迟到；
  `AppSettingsStoreCompanionVisualTest` 使用真实 DataStore 验证小映射与旧保存兼容。
- 真实 RoleplayScreen、Stage、输入和快捷组合核对平台 320dp/200%、IME、48dp、
  完整正文行、层级 Back 与零 send/retry/generate；长按后同指针拖动取消。
- 原阅读段落可能把同一拍长文拆成多个 Text。按 beat 范围读取真实 unmerged Text，
  依次拼接核对完整显示原文，并检查各段无省略；不能用单节点全文匹配误判未显示。
  行坐标必须取对应 Text owner 的实际坐标，不能用合并气泡外框的 origin。
- 字面标签、实体样文字、跨 144 的代理对/组合/ZWJ 簇和 300 实体空格覆盖实际
  Stage 与思想/旁白/对白 Reader。逐段检查 UTF-16 与完整簇，保留非空的纯空白
  TextLayout 再精确拼接；只拼回全文会掩盖两个独立 Text 各自含坏代理的情况。
- Reader 逐项回看先通过真实 UserInput 取得历史位置所有权，再按 itemId 核对
  当前可见项与对应 Text。`performScrollToIndex` 本身不能证明已关闭跟底；默认
  followingBottom 下长项重排可把目标带回末尾。查询失败先保存实际 key/index、
  anchor、followingBottom、作用域原 Text 和全部/过滤后 TextLayout，不能直接
  推断正文未渲染，也不能手改 Fake flag、缩短正文或放宽断言绕过实际滚动。
- 长段落高于视口时，以真实滚动分别验证首行与末行完整。候选和实际位置分别保存，
  不要求所有行同时进入同一帧，也不通过缩短原文、缩字号或放宽可见性来满足断言。
- 受影响旧页面运行 Roborazzi verify，新增原生候选绑定平台/布局/TextLayout 参数。
  比较失败、候选生成和人工接纳分别记录，不预先 record 或替换旧基准。

## 7. 错误与正确做法

```kotlin
// 错误：显示动作变成重新收费生成；读取 scope 被当作模型授权。
requests.newAttempt(turnId)

// 正确：显示事件只消费当前已验证投影。
player.advanceOrReveal()
player.replay()

// 错误：仅扫 typed resources，再删除未出现的已保存文件。
val references = currentVisuals.resources.map { it.path }.toSet()

// 正确：完整 raw、场景和消息引用由统一扫描入口核对。
imageReferences.deleteIfUnreferenced(oldScenePath, imageStore)
```
