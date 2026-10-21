# v2.3 统一设计令牌与无障碍试点：技术设计

## 1. 方向与交付边界

采用用户选择的统一方向。P01 / P02 / P03 的默认视觉均迁移，P03 不能只增加颜色适配接口却继续固定 Narra 调色。用户保存的几何和明确选择的品牌皮肤具有优先权，作为精确例外登记。需求及验收以 [prd.md](prd.md) 为准。

本任务交付一个共享 UI 契约及三处调用迁移，它们必须集成验证；按有序批次执行，不另建平行子任务。原路线图与旧草案保持为来源，不挪动、归档或替其他历史任务更新状态。

## 2. 令牌和默认形状

扩展 `ui/theme/UiTokens.kt`，复用 `NarraUi` 与 `NarraMinimumTouchTarget`，新增共享形状定义 `NarraShapes`，由 `ChatAppTheme` 安装到 `MaterialTheme.shapes`。

| 用途 | 统一值 / Material 槽位 | 迁移说明 |
| --- | --- | --- |
| 小控件、输入框、紧凑标记 | 8dp / extraSmall、small | 指定为共享 small 形状 |
| 普通卡片、默认气泡与面板 | 16dp / medium | 现有 12 / 14 / 18 / 20dp 默认圆角按用途迁移 |
| 弹层、主容器、默认玻璃气泡 | 24dp / large、extraLarge | 现有 22 / 28 / 30dp 默认圆角按用途迁移 |
| 胶囊标签、底栏选择块、圆形控件 | 相对尺寸 50% / pill | 不再用 999dp 模拟全圆 |
| 布局间距 | `space4/8/12/16/24/32` | 内外边距、项间距与布局 Spacer 使用这六档 |
| 触控、图标、边框、尺寸上限 | 各自语义令牌 | 48dp 为最小命中，图标可见大小不强制放大 |

试点页面横向内边距 20→24dp，行内 14 / 18→16dp，6 / 10→8dp，22 / 28→24dp，5→4dp；根据控件用途复核，不能纯数值替换。零间距为布局中性值。头像比例、边框、图标偏移、系统 Insets、屏幕宽度与图片尺寸不纳入六档限制。

既有 `NarraUi` 字段若被试点外调用，保留原值和兼容别名；试点显式迁移到新令牌，不借修改旧字段让全 App 隐式改版。`MaterialTheme.shapes` 的隐式消费者会受影响，实施先列出 Card / Sheet / Dialog / TextField / Button 读取方并补共享组件回归，无法观察的影响登记为未覆盖项。

`RoleplayGlassTokens` 保留非试点消费者所需的旧字段；试点通过统一形状与间距读取。`DefaultRoleplayDialogueBubbleStyle` 只更新默认玻璃路径的半径 / padding，不改变原有 0.82 / 0.86 宽度或玻璃选择谓词。

## 3. 配色数据流与皮肤优先级

```text
AppRoot 的主题选择 / 浅深色 / 动态 ColorScheme
  → ChatAppTheme.resolveAppColorScheme
  → MaterialTheme.colorScheme + shapes
  → P01 / P02，以及 P03 的语义颜色解析

皮肤设置 + 真实 backdropState + 玻璃开关
  → 单一皮肤 / 材质解析
  → ImmersiveRoleplayColors
  → 头部 / 输入区、气泡、旁白和错误消息
```

扩展现有 `ImmersiveRoleplayColors` / 皮肤解析以表达成对的内容与背景，避免并存第二套 `RoleplayColors` 或无消费者的 CompositionLocal。将静态皮肤调色数据集中到 `ui/theme/` 的专用文件，渲染层只消费角色；这是共享所有权整理，仍对该文件和品牌例外做清点，不通过移动文件绕过检查。

| 场景 | 默认颜色来源 | 个性化优先级 |
| --- | --- | --- |
| P01 / P02 背景、卡片、正文、次要文字 | `background`、`surfaceContainerLow`、`onSurface`、`onSurfaceVariant` | 应用显式主题选择优先于系统深色 |
| P03 Narra 无背景 / 关闭背景玻璃 | 用户气泡 `primaryContainer/onPrimaryContainer`；角色气泡 `surfaceContainerLow/onSurface`；旁白 `surfaceContainerHighest/onSurfaceVariant` | 跟随三种应用主题和固定动态 scheme |
| 高亮 / 系统提示 / 错误 | `primary` 或对应内容角色、`secondaryContainer/onSecondaryContainer`、`errorContainer/onErrorContainer` | 文字配对根据实际承载表面校验 |
| 其他 12 个明确选择的皮肤 | 集中保存品牌主色、气泡表面和装饰，统一解析语义角色 | 保留可识别品牌色；深浅色按主题协调非品牌阅读面，必要时调整文字达到 4.5:1 |
| 图片加载成功且有效玻璃开启 | 现有 `deriveImmersiveGlassPalette` 与阅读玻璃表面解析 | 保留真实背景，按合成后的文字承载面校验 |
| 已配置图片但未加载 | 无图 / 主题路径 | 不伪造有图状态；本轮不新增失败占位业务 |

非 Narra 皮肤不被动态色重新染成应用主色，但公共按钮、错误与系统提示仍使用 Material 语义角色。NOIR 等皮肤的品牌深色面可作为登记例外；液态玻璃保留透明材质。皮肤所有可阅读文字均受对比度要求约束。

`rememberRoleplayNoBackgroundSkinSpec`、`rememberOnlinePhoneColors`、`rememberImmersiveRoleplayColors` 及背景调色缓存包含实际 `ColorScheme` / 主题输入、皮肤设置和图片身份；不能只用 preset 或系统深色状态作键。修复背景缓存时核对图片相同但应用主题变化的重算路径。

对透明气泡，将 foreground / surface / backing 依次 `compositeOver` 后计算亮度对比。复用 `ImmersiveGlass.kt` 的阅读面、遮罩与对比处理；如实际测得不足，优先调整内容色，其次加强文字承载面，保持玻璃开关 / 图片语义。宿主固定样本不证明任意真实图片和设备材质均已验证；这部分交给 D01。

## 4. 用户配置和共享影响

- `RoleplayNoBackgroundSkinSettings` 没有区分默认 / 手调数值的字段。所有现存 `bubbleRadiusDp`、`maxWidthPercent`、横纵 padding、尾角和玻璃开关继续原值传递；不能“等于 18 就改 16”或修改 model 常量来统一历史用户配置。
- 统一默认几何作用于未受用户设置覆盖的控件和默认玻璃样式。用户几何覆盖是一类例外，由解析器集中承接，包括尚等于历史默认值的现存配置。
- 皮肤选项、存储枚举和 JSON 字段不变。沿用原有 `normalized()` 和保存回读链路，不增加迁移、隐藏开关或新的全局主题模式。
- 线上有效玻璃继续为 `hasImage && onlineBackgroundGlassEnabled`，线下保持原谓词；width / shape / colors / 渲染外壳使用同一有效状态，头部和背景继续使用真实 `backdropState.hasImage`。
- `RoleplayNoBackgroundSkinSpec` / `ImmersiveRoleplayColors` 为共享入口，线下气泡和设置预览属于必要的调用方回归，但不扩展为线下整页改版。

## 5. 无障碍与大字体

复用 `NarraIconButton` 与 Material 的最小触控能力；保留可见图标大小。把 P03 42dp 回到顶部 / 底部按钮改为至少 48dp 命中。区分图标有无独立动作：文字菜单的装饰图标保持空描述，独立操作使用有效中文名称；底栏统一合并图标与标签语义并提供选中状态。

P01 底栏从约束过紧的固定高度改为最小高度与内容驱动，P03 头部 / 输入区避免字体放大后覆盖消息列表。列表 padding 来自真实容器尺寸 / Insets 或布局约束，不能为 200% 字体硬编码一次性补偿。窄屏允许内容换行与滚动，关闭 / 发送 / 返回操作仍可达，不修改发送、自动贴底与取消协议。

主要验证动作：P01 切换底栏和新增会话，P02 搜索 / 选择 / 更多菜单，P03 返回 / 菜单 / 输入 / 发送或停止 / 回到底部。通过语义树、回调与触控边界断言验证，不仅比较图片。

## 6. 违规检查

先以现有 JUnit 源码守卫模式实现试点级规则，不引入 detekt 或额外 Lint 模块。检查范围为迁移文件及后续新增的同类试点源文件；覆盖颜色构造、默认圆角构造和明确布局上下文（padding、间距、Spacer）的字面量。规则忽略注释 / 字符串，保留文件与符号上下文；不声称具备完整 Kotlin 类型解析。

建立精确例外清单，记录路径、声明 / 表达式、用途和兼容理由。品牌调色、动态几何、尾角、图标尺寸和系统 Insets 不获得整个目录豁免。未消费的例外、未扫描的迁移文件和新的匹配都失败。规则测试包含违规 / 合法表达式、注释与字符串、默认形状、动态用户参数和移位声明；无法静态区分的行为用 UI / 颜色测试和人工审查补齐。

## 7. 截图、验收与回退

1. 实施前运行旧基准 verify，保存 78 张 PNG 的 SHA-256 与环境。新覆盖不删除旧 ID 或降低像素阈值。
2. 迁移后对旧基准 verify，预期产生 actual / compare 差异；明确报告失败和对应 T1 / T2 / T4，不把视觉变化写成测试通过。
3. 从 actual 输出整理候选图与前后对照，保持 `app/src/test/snapshots` 原件不变；如工具链需单独 record，则先增加仅显式启用的候选目录参数并验证输出隔离，默认 / CI 仍指向原目录，不在检查前 record。
4. 经用户逐批确认视觉候选后才更新对应基准和 manifest，最终在冻结参数下 verify。未经确认时交付可审阅候选并保留 A6 未完成，检查代理不得自动 record 来消除失败。
5. 复用 H01–H04、B01–B04、C01，补实际 P03 动态色、主题切换缓存和大字体交互断言。若截图 fixture 手写旧 padding 或形状，改为调用生产令牌；不能令 fixture 绕过待测生产路径。

回退按批次进行：令牌定义、P01/P02、P03、无障碍 / 门禁分别保留明确 diff；产品代码与候选图分开处理。遇到意外共享视觉影响时先缩小本批变更并补证据，不回滚工作区原有改动。已确认基准在人工接纳前始终可用作原始对照。

## 8. 风险和延期项

共享 Material 形状及气泡解析会影响试点以外的隐式消费者，必须单列检查。原安全网 D01 / 性能项缺测不由本轮规划消除；执行时重新探测设备，具备有效条件才运行现有 baselineprofile 路径，模拟器只作交互验证。发包、版本号与更新清单不在本任务交付范围。
