# 2.4.1-dev 云端 UI 候选独立审阅

**结论：适合向用户展示完整对照并请求人工接纳。** 已查看全部 15 个变化测试组的代表样本，共 17 对原尺寸 before/after；未发现明确新增的正文裁切、操作消失或明显不可读。此结论是抽样视觉审阅，207 项仍是候选，本审阅未接纳、record 或替换基准。

## 来源与全量统计

- AndroidQuality run `44734382203`，Ubuntu/JDK17，源码 SHA `74d152d9cf4873a6c7ca16c81aebdf1811d5c527`；UI 提交来源按主会话交接为 `5a62efab`。审阅使用下载的 CI PNG，没有重新捕获 Windows 图。
- 独立读取下载的 JUnit XML：222 测试、207 failure、0 error、0 skipped；207 条均为 `java.lang.AssertionError`，message 均含 Roborazzi。`ScreenshotBaselineGuardTest` 1/1 无失败。
- [原始 results-summary.json](../../../../../../.tmp/narra-release-241/ci-44734382203/screenshots/test-results/roborazzi/debug/results-summary.json)：221 张，207 changed、14 unchanged、0 added、0 recorded。
- [review.json](../../../../../../.tmp/narra-release-241/ci-44734382203/review/review.json) 保存全部 207 项的路径、原尺寸、前后 SHA-256 和差异比例，`dimensionChanges=[]`。主会话另核对 HTML 的 207 cards / 414 images，0 缺资源；本审阅不重复承担 HTML 链接验证。
- [完整前后对照](../../../../../../.tmp/narra-release-241/ci-44734382203/review/index.html)。下表原图均来自其 `before/<测试组>/<图ID>.png` 与 `after/<测试组>/<图ID>.png`，没有把 compare 或缩略总览当作原尺寸检查。

## 代表样本：15 组 / 17 对

| 测试组 | 已看原尺寸前后图 ID | 视觉证据 |
| --- | --- | --- |
| BubbleScreenshotB02Test | `B02_p03-background-glass-on` | 合成背景图形及玻璃气泡仍在；三条正文完整。图标、边缘与单聊姓名行变化对应生产 UI。 |
| BubbleScreenshotB03Test | `B03_padding-horizontal-max` | 最大横向 padding 下用户一行、助手两行和描述完整；气泡宽度与文本换行保留，姓名行减少后整体上移。 |
| BubbleScreenshotB04Test | `B04_offline-no-background` | 线下保留角色标签，用户标签收起；原三段文本及其换行完整，描述承载面配色改变。 |
| ContextTransferScreenshotE06Test | `E06_transfer-first-narrow-light`、`E06_transfer-preview-notice-narrow-light` | 窄屏大字下本地文件导入/导出两动作完整，notice 两条正文及角色卡/世界书计数可读；前后取景一致。 |
| MessageBubbleScreenshotTest | `chat-user-message-matcha-light` | 固定两行消息与复制图标保留；主要是背景/内容色变化。右缘“你”的既有取景边界前后相同。 |
| OfflineRoleplayScreenshotE04Test | `E04_reading-narrow-dark` | 长标题、剧情数量、阅读正文和固定关闭 X 的布局相同；末行在滚动视口边界处截取是原有取景，没有新增覆盖。 |
| PageScreenshotH01Test | `H01_p01-messages-matcha-light`、`H01_p03-online-roleplay-matcha-dark` | 消息顺序、时间、置顶及四个主壳页签保留；底栏新增阴影/选中字重。线上三段正文完整，单聊姓名行收起，输入区保留。 |
| PageScreenshotH02Test | `H02_p03-online-empty-session` | 空会话仍为原空消息区域，头部与输入操作完整；主要是主题承载面和图标变化。 |
| PageScreenshotH03Test | `H03_p03-online-error-message` | 失败徽标、完整错误正文和原重回提示可读，未被新配色或输入区遮住。 |
| PageScreenshotH04Test | `H04_p03-online-narrow-dark` | 当前助手正文、描述与输入区纵向边界一致；顶部上一条用户尾部来自贴底取景，详见下节。 |
| ProviderSettingsScreenshotE11Test | `E11_ability-dialog-dark` | 五个能力项、选中状态，以及恢复自动/取消/保存全部可见；布局和文案保留。 |
| SessionSidebarScreenshotE05Test | `E05_reset-dialog-dark` | 新清理范围全文一直显示到“此操作不可撤销。”；取消和确定清空完整，破坏性动作仍用 error 色。 |
| SettingsHomeScreenshotE10Test | `E10_display-sheet-dark` | 思考/图片/代码选项、开关状态和末项保留；顶端上一项被取景截取前后相同。 |
| SkinScreenshotB01Test | `B01_imessage-bubbles-dark` | iMessage 用户蓝色和气泡几何保留，助手与描述在深色承载面可辨；姓名行收起属于共同变化。 |
| WorldBookScreenshotE06Test | `E06_book-delete-dialog-dark` | 完整书名、6 条条目的删除范围与不可撤销说明保留，取消/确认删除均完整；主要是主题表面/文字角色变化。 |

## 重点风险的源码核对

### H04 顶部边界没有新增遮挡证据

该样本为 640×1280 PNG，对应 320×640dp / 200% 字体。after 顶部仅露上一条用户气泡尾部；before 已没有显示该用户正文。当前助手气泡、描述和输入区边界基本相同，不能因首条未完整入镜直接判为退化。

[RoleplayOnlinePhoneContent.kt](../../../../../../app/src/main/java/com/example/myapplication/ui/screen/roleplay/RoleplayOnlinePhoneContent.kt) 将 header、独立 weight 消息视口、输入区放在同一 Column；消息仍使用原列表 state/稳定 key。单聊明确传 `showSpeakerName=false`，群聊仍显示姓名。减少姓名行后，贴底取景能露出更多上一条内容。

[PageScreenshotH04Test.kt](../../../../../../app/src/test/java/com/example/myapplication/ui/screenshot/PageScreenshotH04Test.kt) 明确说明列表初始贴底、首条可在视口外，并在 capture 前检查 header.bottom ≤ viewport.top、viewport.bottom ≤ input.top 及尾条真实内容。该 CI 类的失败均是最后的 Roborazzi 像素比较，没有额外行为断言失败。

### 颜色、姓名与图标变化可以对应到生产组件

[ChatAppTheme.kt](../../../../../../app/src/main/java/com/example/myapplication/ui/theme/ChatAppTheme.kt) 的 background/surface/container/内容角色能解释大面积背景像素差异；差异比例接近 100% 不等于整个页面结构坏了。[RoleplayNoBackgroundSkin.kt](../../../../../../app/src/main/java/com/example/myapplication/ui/component/roleplay/RoleplayNoBackgroundSkin.kt) 继续使用保存的宽度、圆角、padding 与尾角；阅读色按实际承载面解析。[ImmersivePhoneShell.kt](../../../../../../app/src/main/java/com/example/myapplication/ui/screen/immersive/ImmersivePhoneShell.kt) 的底栏 shadow、border、选中字重及线上 Lucide 图标对应样本变化。

### 清空说明应保留新的 C6/D6 语义

[strings_roleplay.xml](../../../../../../app/src/main/res/values/strings_roleplay.xml) 的 `roleplay_reset_dialog_body` 明确清空当前消息、摘要、日记、在线信息及依赖原文的记忆，同时保留场景、独立人工修订和其它对话；[RoleplaySettingsContent.kt](../../../../../../app/src/main/java/com/example/myapplication/ui/screen/roleplay/RoleplaySettingsContent.kt) 使用该资源及 `isDestructive=true`。这与 [C6 管理合同](../../../../../spec/backend/memory-management-guidelines.md) 的原文依赖清理相符，不能为了匹配旧 PNG 还原成“只清空剧情消息”。

资料 notice 样本的固定正文前后相同；[ContextTransferScreen.kt](../../../../../../app/src/main/java/com/example/myapplication/ui/screen/settings/ContextTransferScreen.kt) 已按真实计数增加陪伴偏好及“保留旧包缺域、不备份全聊天或声音库”说明，符合 [D6 合同](../../../../../spec/backend/companion-history-transfer-guidelines.md)。上述固定预览样本没有 `companionCount>0`，不能声称已从这些 PNG 验证全部 D6 恢复界面。

## 未审项、修复与验证边界

- 其余 190 张 changed 及 14 张 unchanged 未逐张人工视觉审阅；全部 207 张的统计/尺寸检查与上述 17 对视觉证据分别成立。
- 静态 PNG 不能证明滚动交互、真实 48dp touch bounds、IME、物理设备或全部主题的数值对比度。本轮没有新增明确产品缺陷，未修改生产源码或基准，也不把本审阅当成完整质量门禁通过。
- Lint：未执行，按派发范围由主会话负责。
- TypeCheck：未执行，按派发范围由主会话负责。
- Tests：未运行新测试；只读 CI screenshot verify 为 fail（207 个已有基准像素差异），计数守卫 pass，其它 JVM 问题由主会话/另一 reviewer 单独处理。
- 唯一写入本报告；未运行 Gradle/Git/设备/发布，也未捕获新截图或改变宿主、源码、基准。无需因本轮抽样发现同步新增 spec 规则。

人工接纳遵循 [质量规范的截图规则](../../../../../spec/backend/quality-guidelines.md)：更新基准须人工确认后单独提交。建议主会话给用户完整 207 项对照并明确抽样范围，用户决定是否接纳，之后再执行 CI 宿主的最终 verify。
