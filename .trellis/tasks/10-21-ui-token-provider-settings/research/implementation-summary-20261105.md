# P11 提供商配置实施记录

本批基点为 `main / ac22bb271007c814b6ef49b8499858acc815661c`，沿用已批准的 v2.3 第四批计划。七个 Provider 局部文件、必要的共享 Dialog opt-in、行为测试和 32 组前后候选已完成。**最终 Lint/dev 构建/AndroidTest Kotlin 编译退出0，55条原有警告与3条 hints，无新增项；全部 Gradle 已结束，所有权交还主会话。** 已接纳基准仍为 126 张，没有 record、复制新基准、暂存、提交或推送。

## 产品范围与共享边界

- `ProviderSettingsScreen.kt`：列表顶栏消费 P10 的 `horizontalPadding`、`titleMaxLines`、`titleIsHeading`；卡片/搜索/状态标签显式消费 Narra 令牌。长名称和当前模型可换行；启用面使用 `primaryContainer/onPrimaryContainer`，健康状态继续带文字，圆点改用真实 Material 角色并保留原脉冲。
- `ProviderDetailChrome.kt`：Tab 有 `Role.Tab`、selected 与分组语义；未选项不再降低正文 alpha。标题完整换行，超长内容在实际窗口高度 40% 以内独立滚动，返回保持独立 48dp；滚动状态按 provider.id 隔离。压力测试从仅剩 28dp 的表单视口修复到 273.5dp，标题可滚到真实 max。
- `ProviderConfigTab.kt`：表单/保存操作采用令牌，启用行只有一个 Switch 回调。输入标签包含焦点态均取可读的真实表面内容色。协议/模式说明采用本页局部标题与真实 NarraAlertDialog，原文案和“知道了”关闭保留。Config 无浮动操作，原 120dp 空白经测量后改为 24dp；保存/删除仍处于底栏上方。
- `ProviderDetailScreen.kt`、`ProviderModelTab.kt`：获取模型动作提供名称、禁用原因及加载进度语义；数量独立排版。模型列表底部避让根据浮动区实际测量高度加令牌间距计算，末项编辑/移除完整可见。模型排序和选择回调保持原样。
- `ProviderModelDialogs.kt`：选中卡/抓取项使用成对内容色；长名称、能力标签与独立动作自然排版。抓取搜索与手工输入改为带真实标签的输入控件，空 ID 错误关联输入语义；手工 ID/显示名的 trim 与回退规则保持。能力取消、保存/恢复自动及清空确认均保持原显式操作。
- `ProviderTemplateDialog.kt`：真实 Dialog 的有界内容使用实际 `LocalWindowInfo.containerSize`，保留原模板和关闭回调；移除占满窗口的背景 Box，模板列表与固定取消区分离。真实设备窗外触控/系统返回仍待执行，不以窗口属性代替实测。
- `ui/component/NarraAlertDialog.kt`：只增加 `shape: Shape = RoundedCornerShape(NarraUi.cardRadius)` 与 `actionContentColor: Color = MaterialTheme.colorScheme.primary`。默认外观/布局/insets/动画不变；破坏性确认继续优先 error。P11 显式选择 large/onSurface；NarraInfoDialog 和其他消费者仍走旧默认。原源码/Git blob 与消费者清单见 [补充冻结](shared-dialog-baseline-20261105.json)。

没有修改 SettingsComponents、导航/VM/存储/Provider 协议实现、全局主题或其他设置页面；“未找到 provider”提示保留原外观，仅做行为回归。

## 兼容与行为证据

新增 8 个 JVM 行为类：`ProviderSettingsActionsTest`、`ProviderConfigAccessibilityTest`、`ProviderModelsAccessibilityTest`、`ProviderDialogsTest`、`ProviderColorsTest`、`ProviderPersistenceTest`、`ProviderSharedDialogCompatibilityTest`、`ProviderNavigationTest`，当前合计 38 项。加 14 项源码守卫后，最新 **52/52** 实际通过。

- 列表自动健康检测保持原触发条件；纯 UI fixture 的健康入口为本地回调，图中健康 map 为固定最终态。真实 VM/NavHost 测试显式注入 Test API；不读取用户数据或真实凭据。
- 模板返回 ID 后先等待对应草稿进入状态，再只导航一次；取消不创建提供商。Provider ID、协议、mode、URL、key、模型与能力 override 不变。
- Tab/密钥显隐按原 providerId 键控；pending fetch 只在其所属 provider 出现。Material 密码字段切到明文时原 Password 语义旗标会保留，测试以“隐藏密钥”动作及实际 TextLayout 显示结果验证，再检查新 ID 默认隐藏，没有改变产品状态策略。
- 首末项与保存/底栏/FAB 测量同时检查未裁切布局和真实 touch bounds；末项动作精确绑定最后模型卡，编辑与移除边缘点击不会选择模型。主要触控至少 48dp。
- 手工空 ID 不提交，取消只关窗口；能力恢复自动/清空/删除仍需显式动作；最后一个提供商不能删。
- 真实 SettingsViewModel + 延迟写入 Fake 验证显式保存失败保留完整草稿/模型 override、复位保存中并不执行成功回调。真实 `registerSettingsProviderRoutes` / NavHost 验证列表和详情返回均在写入完成前立即 pop；后续写入失败仍留草稿，不宣称失败留在原 destination。
- 新增 `androidTest/ProviderSettingsFlowTest.kt` 的 2 项整页回归，只使用本源集合成 builder，未执行设备测试。

受影响回归包含 P10 共享/入口动作、P05、P02 无障碍以及 Settings ViewModel/Draft/ProviderDraft/Load/Health/Persistence/Mutation 支持。144 项首轮 143 通过，唯一长标题定位器失败修正后 1/1 通过；随后窗口尺寸 API 修正后 P11/守卫 52/52 再次通过。[逐项最新状态](targeted-final-status.json) 汇总 144 个不同用例全部已消除失败，明确不是一次 144/144 全绿运行。

## 真实颜色与已修正问题

八种生产解析 scheme（MATCHA/VANILLA/OCEAN 浅深及固定动态浅深）从实际 TextLayout 获取前景、同一页面/窗口像素获取承载面。覆盖列表/健康/启用、Config 焦点标签、Tab、保存、模型卡、抓取项与操作、手工错误、能力选项和模板窗口。

固定 dynamic-dark 使用 `lightColorScheme()` 输入经生产解析后，primary `#6750A4` 直接作普通文字的对比实测约 2.82（background）、2.66（surface）、2.08（surfaceContainerHigh）。首轮 48 项中这 5 个颜色用例失败，已通过 P11 局部 onSurface 输入/文字动作及共享 Dialog 可选动作色修复；主按钮仍为 primary/onPrimary，破坏性确认仍为 error。最新八种 scheme 均满足实际文字至少 4.5:1，没有修改全局 scheme。

源码守卫对七文件和共享 Dialog 保持完整扫描；`requiredDeclarations` 只检查精确存在/数量，未设置 `scannedDeclarations` 时本来就扫描整个文件。补充 helper 的 required 名单及体内注入负向检查是加强移位/缺失防护，不是修复“helper 原本未扫描”。只为未迁移的缺失 provider 提示、共享 Dialog 旧间距及合法透明角色登记精确例外，没有目录豁免或算法放宽。

## 截图与来源

- [before](before/manifest.json) 在产品修改前冻结全部 32 张；[after](after/manifest.json) 用同样的尺寸/字体/固定数据与打开/滚动步骤取得。两阶段各 12 个真实独立窗口；无窗口时必须失败，不回退底页。
- 常规为 360×800dp/100%，窄屏与窗口为 320×640dp/200%，density=2。独立窗口以其真实 root 捕获并记录实际尺寸，未伪造为固定底页。
- 窄屏列表前/后滚动为 662/662 → 646/646；Config 保存为 2509/2509 → 2199/2199；Models 末项为 2219/2219 → 2273/2273，浅深一致。每项还记录完整操作 raw/touch 矩形及末项卡/FAB/底栏关系。
- 首轮 before 的两项能力弹窗打开动作被 FAB 遮挡，窗口守卫正确拒绝；产品修改前已修复为点真实末项的可见编辑按钮并重取完整 before。原 30 张及失败日志保留在 `before-initial-results/`，不作为最终 before；本批不需要修改主工作区恢复旧源码。
- 126 张原基准 SHA 始终与 [冻结清单](accepted-png-hashes-before-p11.txt) 一致。after 源哈希包含 8 个实际生产修改文件及未改的 SettingsComponents，并引用补冻的共享 Dialog 原始来源。
- [整包真实截图结果](screenshot-final-results/summary.json)：**159 项、15 suites、126 旧图通过；32 E11 + 1 计数守卫仅缺基准失败**，0 errors/skipped、79秒、退出1。没有接纳新 PNG、record、降低阈值或豁免计数守卫。

## 验证与剩余步骤

- [最新 P11/守卫](provider-final-results/summary.json)：52/52、9 suites、实际执行1分钟、退出0。
- [受影响首轮](targeted-final-initial-results/summary.json)：144项/24 suites、143通过、1定位器失败，70秒、退出1；[最后标题复验](title-stress-final-results/summary.json)：1/1，19秒、退出0。
- Android Studio Panda 2 原生 formatter 使用隔离 profile 定向格式化 19 个本批文件，标题/设备测试修正后再格式化对应3个文件；日志见 `formatter-20261105.log` 与 `formatter-final-20261105.log`。未格式化其他页面或仓库。
- 定向 `git diff --check` 退出0，仅有仓库既有 LF/CRLF 提示。
- [最终质量结果](quality-verified/summary.json)：退出0，5分25秒，13个task实际执行/47个UP-TO-DATE。Lint 主源码/单测/设备测试分析、dev打包和AndroidTest Kotlin编译均实际执行；0 errors、55条原有warnings/3条hints，与P10逐项对照无新增或删除。主Kotlin编译复用前一轮实际编译产物，不能称设备运行。
- 最终 dev 验证包仍为 `Narra-v2.2.12-dev-20212-dev.apk`，82,095,990 bytes，SHA-256 `bd8a84f3fc000a608a040887d6090a70c1f0ad291d14932bb7d24a02050a8e54`，没有上传/发布。[最终哈希复核](implementation-final-hashes.json) 确认 after、质量记录与当前9个源码哈希一致，126张已接纳PNG、版本/更新配置及带原CI差异的snapshot manifest不变。
- `quality-initial/` 是新增设备测试 DpRect 宽高写法错误导致的失败轮；已修正为坐标差和完整边界检查。`quality-final/` 是退出0但有3条新增窗口尺寸 API 警告的中间轮，不能作为最终结论；最后的有效报告为 **quality-verified/**。已经改为 LocalWindowInfo，没有增加 suppress 或降低检查标准。

人工视觉接纳、接纳后全包截图/完整 JVM、独立检查及具体提交/归档由主会话继续。功能仍归属未发布 v2.3；构建和更新配置保持 2.2.12-dev，未发布/推送。真机 TalkBack、系统返回/窗外触控、IME/系统栏、实际材质、D01、物理性能及新视觉远端 CI 均未取得证据。
