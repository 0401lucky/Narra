# UI 信息架构精简 + 交互打磨设计

- 日期：2026-07-19
- 状态：已实现（C1–C5）；实现计划见 `docs/superpowers/plans/2026-07-19-ui-ia-simplify.md`
- 方案档位：**C（信息架构大扫）**
- 关联反馈：外部用户「精简 APP / 去重复入口 / 侧栏用预设 / 对齐 TAVO 顺手度」
- 关联文档：`docs/真预设功能设计.md`（会话级预设覆盖此前明确未做）

## 1. 背景与问题

外部用户反馈核心不是「缺功能」，而是：

1. **入口重复**：消息列表三点、会话资料全页、会话设定侧栏、全局设置、「我」页，同一意图多路径。
2. **今日动态冗余**：发现页大卡与「角色空间 → 朋友圈/信箱」重复。
3. **预设路径长**：全局预设管理 + 角色默认预设存在，但**对话内不能一点切换**；会话级覆盖未实现。
4. **新建角色偏重**：多层页面才能建完，阻碍「先聊起来」。
5. **观感与顺手度**：图标语义弱、列表偏系统默认、按压反馈弱，和沉浸扮演调性不统一。

## 2. 目标与非目标

### 目标

1. **当前会话优先**：侧栏成为本会话配置唯一主台。
2. **库与运行时分离**：设置 /「我」管资产库；侧栏管「这次对话怎么用」。
3. **一条路径做一件事**：去掉消息三点、去掉今日动态大卡、合并会话视觉配置。
4. **会话级预设**：`scenario → assistant → global → 内置` 四级回退。
5. **新建角色极简**：名字即可创建，高级项后置。
6. **UI 打磨**：图标语义、分组卡片、按压/选中/切换反馈统一到 Narra 精致风（不换主题色板）。

### 非目标

- 不大改全局主题色 / Material 色板策略。
- 不重做朋友圈、信箱、通话业务逻辑。
- 不复刻 TAVO 全量功能，只对齐「侧栏换预设 + 少重复」体验。
- 不删除预设库管理能力（导入/编辑/排序/默认），只收口入口。
- 不做强制更新、不做无关模块重构。

### 已确认决策

| 决策点 | 结论 |
|--------|------|
| 推进档位 | C 全量 |
| 今日动态 | **整卡移除**（不做弱化 badge） |
| 设置页「预设管理」 | **移除**，主入口改为「我 → 预设库」 |
| UI | 架构变更同时做图标细化与交互打磨 |

## 3. 目标信息架构

```
【对话内 · 会话设定侧栏】                 【全局 · 我 / 设置】
├ 成员概览                                ├ 个人资料 / 面具切换
├ 会话设定                                ├ 资料库
│  ├ 情景设定                             │  ├ 会话管理
│  ├ 用户身份                             │  ├ 角色资料
│  ├ 本会话预设  ← 新增                   │  ├ 世界书
│  ├ 视觉（背景/立绘）← 从会话资料迁入    │  ├ 记忆档案
│  └ 主题 / 快捷切换                      │  ├ 预设库  ← 主入口
├ 高级                                    │  └ 资料导入导出
│  ├ 提供商连接                           └ 应用
│  ├ 人设提示词 / 世界书 / 记忆             ├ 设置（模型/显示/工具/更新）
└ 创作者（上下文日志等）                    └ 关于
```

## 4. 功能改动规格

### 4.1 消息列表去掉三点（C1）

| 项 | 处理 |
|----|------|
| `ChatSummaryRow` 右侧 `MoreVert` | **删除** |
| 行布局 | 头像 + 标题/时间 + 摘要/角标，右侧不再预留三点占位；可略增文字区宽度 |
| 编辑会话资料 | 会话管理列表进入瘦身后的编辑页；对话内用侧栏 |
| 置顶 / 免打扰 / 清空 / 删除 | **保留左滑**，补齐操作后的轻反馈（见 §5.3） |

**交互打磨：**

- 左滑露出动作时，动作按钮用圆角色块 + 白字图标，语义色：置顶=primary、免打扰=tertiary、清空=outline、删除=error。
- 滑动跟手；松手吸附阈值保持现有逻辑，松手成功执行时可选短震动（`HapticFeedbackType.LongPress` 或 `TextHandleMove`，与项目现有一致即可）。
- 列表行按压：`surface` → `surfaceVariant` 轻微变色（`indication` / `MutableInteractionSource`），避免裸 `clickable` 无涟漪。

### 4.2 发现页移除今日动态（C1）

| 项 | 处理 |
|----|------|
| `PhoneEcosystemEntry` 大卡 | **移除** |
| `PhoneEcosystemSheet` 入口 | 同步移除；无引用后删除 Sheet 与死代码（或本轮一并删） |
| `RoleplayPhoneEcosystemSnapshot` 数据层 | **可保留**（后续若做角标再用）；本轮 UI 不再消费 |

发现页结构：

```
✨ AI 创作
  [摇一摇] [角色图工作台]

🪪 角色空间
  [朋友圈] [查手机] [日记]
  [信箱]   [视频通话]
```

**UI 打磨：**

- 分区标题 `DiscoverSectionHeader`：图标 + 标题，字重 SemiBold，分区间距 20–24dp。
- AI 磁贴：保持 22dp 圆角 + 柔和竖向渐变；图标容器 44dp、图标 22dp；按压缩放 `0.97`（`graphicsLayer` + 按压状态）。
- 角色空间网格：统一图标语义（见 §5.1）；有未读时（若后续接角标）仅在对应磁贴右上角小红点，**不**恢复顶部大卡。
- 空数据不额外占位说明；直接展示磁贴即可。

### 4.3 「我」与设置入口重排（C2）

#### 「我」页分组

```
[头像 Hero + 面具切换]   （保留，卡片化）

资料库
  会话管理 / 角色资料 / 世界书 / 记忆档案 / 预设库 / 资料导入导出

应用
  设置 / 关于
```

- 新增 **预设库** → 现有 `PresetListScreen`（与设置原入口同一目的地）。
- 列表从扁平 `FeatureRow + Divider` 改为 **分组卡片**（同设置页 `SettingsGroup` 语言）：圆角 18–20dp、组内分隔线、组外间距 16–20dp。

#### 设置主页

- **移除**「预设管理」列表行。
- 保留：外观、显示、提供商、默认模型/提示、语音、脚本实验室、版本更新等。
- 角色详情「默认预设」**保留**（角色级默认，配合会话覆写）。

### 4.4 会话级预设（C3）

#### 数据

`RoleplayScenario` / `RoleplayScenarioEntity` 新增：

```kotlin
// 空字符串 = 跟随角色默认 → 再回退全局默认 → 内置
val presetId: String = ""
```

- Room 版本 **+1**，迁移写默认 `""`。
- 导入导出 / 上下文迁移若包含 scenario 字段，同步编解码 `presetId`。

#### 解析

扩展 `PresetSelection.kt`：

```kotlin
fun resolveActivePresetId(
    globalDefaultPresetId: String,
    assistantDefaultPresetId: String?,
    scenarioPresetId: String? = null,
): String
```

优先级：

1. `scenario.presetId` 非空  
2. `assistant.defaultPresetId` 非空  
3. `settings.defaultPresetId`  
4. `DEFAULT_PRESET_ID`

`PromptContextAssembler` 组装时传入当前 scenario 的 `presetId`。

#### 侧栏 UI

「会话设定」卡新增行：

| 行 | 摘要 | 行为 |
|----|------|------|
| 本会话预设 | `名称 · 跟随角色` / `名称 · 本会话` | 打开选择 Sheet |

Sheet 行为：

- 首项：**跟随角色默认**（清空 `scenario.presetId`）
- 下列全部可用预设；当前生效项打勾
- 点选即保存，Sheet 关闭，摘要即时更新
- 底部次要链：**管理预设库** → 跳转预设列表（全局库，不在侧栏内嵌编辑器）

**交互：**

- Sheet：`skipPartiallyExpanded = true`，列表项高度舒适（≥ 48dp 触控）
- 切换成功：短震动 + 摘要文字交叉淡入（可选 `AnimatedContent`）
- 加载/保存失败：Snackbar 中文提示，不静默失败

### 4.5 视觉迁入侧栏 + 会话资料瘦身（C4）

#### 侧栏新增 VISUAL 子页

字段（从 `RoleplayScenarioEditScreen` 迁入）：

- 背景图  
- 用户立绘（本地 / URL）  
- 角色立绘（本地 / URL）  

主列表摘要：`已设背景` / `已设立绘` / `默认视觉` 等短文案。

#### 会话资料页保留

- 会话备注、会话背景补充（+ 写入提示词开关）
- 绑定角色 / 会话形态  
- 用户面具绑定（若侧栏身份页已覆盖，可改为只读摘要 +「去会话设定」链接；本轮允许暂留以免范围膨胀）  
- 显示名覆写、开场旁白  

#### 会话资料页移除（迁出）

- 背景图 / 用户立绘 / 角色立绘整块「视觉资源」区  

新建会话：创建后进对话，视觉在侧栏补。

### 4.6 新建角色精简（C5）

`AssistantBasicScreen` 在 `isNew == true` 时：

| 字段 | 必填 |
|------|------|
| 名字 | 是 |
| 头像或图标 | 否 |
| 简介 | 否 |
| 核心人设短文本（写入 `systemPrompt` 初值） | 否（建议展示，可空） |

- 主按钮：**创建**（成功后回上一页或通讯录）  
- 可选次按钮/勾选：**创建并开始会话**（有则创建默认会话并导航进 RP；无则仅创建角色）  
- 朋友圈策略、扩展、记忆、图工作台、默认预设 → **详情页后置**，新建页不出现  

编辑已有角色：基础设定可保持现有字段，不强行砍功能。

## 5. UI / 图标 / 交互打磨规格

本章与 C1–C5 **同轮交付**，不是可选 polish。

### 5.1 图标语义表

优先使用 Material Icons **圆角语义清晰**的一组；避免「万能齿轮 / 万能书本」重复。

| 入口 | 推荐图标 | 备注 |
|------|----------|------|
| 会话管理 | `Chat` / `Forum` | 与消息列表一致 |
| 角色资料 | `Person` / `Badge` | |
| 世界书 | `MenuBook` | 已用 AutoMirrored |
| 记忆档案 | `Psychology` 或 `AutoStories` | 与侧栏长记忆区分：档案用 `AutoStories`，开关用 `Psychology` |
| 预设库 | `LibraryBooks` 或 `ViewAgenda` | 与「条目列表」语义一致 |
| 导入导出 | `ImportExport` 或 `CloudSync` | 优先 `ImportExport` 更贴切 |
| 设置 | `Settings` | |
| 关于 | `Info` | |
| 本会话预设 | `Tune` 或 `Style` | 侧栏行 leading |
| 视觉 | `Image` / `Wallpaper` | |
| 朋友圈 | `Forum` / `PhotoLibrary` | 发现网格内统一 |
| 查手机 | `PhoneAndroid` | |
| 日记 | `MenuBook` 或 `EditNote` | |
| 信箱 | `Mail` / `Email` | |
| 视频通话 | `Videocam` | |
| 摇一摇 | `Casino` / `AutoAwesome` | |
| 角色图工作台 | `Image` / `Brush` | |

图标容器规范（「我」资料库 / 发现网格共用）：

- 容器：40–44dp，圆角 12–14dp  
- 图标：20–22dp  
- 底色：按分组用 `primaryContainer` / `secondaryContainer` / `tertiaryContainer` **轮换或按语义固定**，避免整页同一 secondary 糊成一片  
- 建议固定映射：资料库偏 secondary；应用偏 surfaceVariant；发现「角色空间」五项各自固定 container 色（与现有 Discover 一致可微调）

### 5.2 列表与卡片形态

1. **「我」页**：分组卡片，禁止长串 `ListItem + Divider` 通栏割裂感。  
2. **FeatureRow 升级**为 `ProfileLibraryRow`：  
   - leading 图标容器（§5.1）  
   - title SemiBold + subtitle `onSurfaceVariant`  
   - trailing `KeyboardArrowRight` 淡色  
   - 整行最小高度 56–64dp  
3. **消息列表行**：去掉三点后，时间右对齐、摘要单行 ellipsis；置顶/免打扰小图标与摘要同行尾部，间距 4dp。  
4. **侧栏 SummaryLinkRow**：本会话预设 / 视觉 与现有情景、身份同一组件，保证对齐与分隔线一致。  
5. **预设选择 Sheet**：选中行 primary 勾选 + 可选浅 `primaryContainer` 底；「跟随角色」单独置顶并加说明副文案。

### 5.3 交互与动效

| 场景 | 规格 |
|------|------|
| 可点击卡片/磁贴 | 涟漪 + 按压缩放约 `0.97–0.98`，时长 100–150ms |
| 左滑动作执行 | 轻触觉反馈；删除类二次确认若已有则保留 |
| 侧栏页切换 | 沿用现有 `AnimatedContent` 水平滑动 |
| 预设切换成功 | 轻触觉；摘要文案更新；无需全屏 loading |
| 新建角色成功 | Snackbar「已创建角色」；若「并开始会话」则直接导航 |
| 空状态 | 会话列表 / 预设库空态：插画或大图标 + 一句说明 + 主按钮（沿用现有 EmptyState 风格统一） |
| 危险操作 | 删除会话/角色：确认对话框，主按钮 error 色 |

**不做：** 花哨 Lottie、全页共享元素大动画、与业务无关的 parallax。

### 5.4 文案统一（简体中文）

| 旧/混用 | 统一为 |
|---------|--------|
| 预设管理（设置内） | **预设库**（「我」） |
| 默认预设（角色详情） | 保留「默认预设」 |
| 会话侧栏新行 | **本会话预设** |
| 视觉子页标题 | **视觉** 或 **背景与立绘**（实现时二选一，全文统一） |
| 今日动态相关文案 | 删除，无残留 |

### 5.5 无障碍与触控

- 图标按钮保留 `contentDescription`（中文）  
- 去掉三点后，编辑能力必须仍可通过会话管理或侧栏到达（键盘/读屏可及）  
- 触控目标 ≥ 48dp  

## 6. 实现分期

| 阶段 | 内容 | 风险 |
|------|------|------|
| **C1** | 去消息三点 + 消息行布局收尾；去今日动态 + 发现页间距/磁贴交互 | 低 |
| **C2** | 「我」分组卡片 + 图标语义 + 预设库入口；设置移除预设管理 | 低 |
| **C3** | scenario.presetId + migration + 解析链 + 侧栏 Sheet + 单测 | 中 |
| **C4** | VISUAL 子页 + 会话资料瘦身 + 图片选择器复用 | 中 |
| **C5** | 新建角色最小集 + 成功反馈；可选「创建并开始」 | 低～中 |

允许单 PR 串行 C1→C5，或拆 PR：`C1+C2` → `C3` → `C4+C5`。  
UI 打磨绑定各阶段页面，**不单独拖尾 PR「再 polish」**。

## 7. 主要改动面

### UI

- `ui/screen/immersive/ImmersivePhoneShell.kt` — 消息行、发现页、「我」页  
- `ui/screen/roleplay/RoleplaySettingsSidebarPanel.kt` — 预设行、VISUAL 页  
- `ui/screen/roleplay/RoleplaySettingsScreen.kt` — 子页枚举与导航  
- `ui/screen/roleplay/RoleplayScenarioEditScreen.kt` — 瘦身  
- `ui/screen/settings/SettingsScreen.kt` — 去预设入口  
- `ui/screen/settings/AssistantBasicScreen.kt` — 新建精简  
- 导航：`RoleplayNavGraph` / `SettingsNavGraph` / `SettingsAssistantNavRoutes` 按需  

### 数据 / 领域

- `model/RoleplayScenario.kt`  
- `data/local/roleplay/RoleplayScenarioEntity.kt` + DAO/mapper  
- `ChatDatabase` migration（版本 +1）  
- `model/PresetSelection.kt`  
- `context/PromptContextAssembler.kt`  
- 导入导出编解码（若覆盖 scenario）  

### 测试

- `resolveActivePresetId` 四级回退  
- scenario `presetId` 读写 / migration  
- Assembler 使用会话预设（有则优先）  
- 现有角色默认预设用例不回归  

## 8. 验收标准

1. 消息列表无右侧三点；左滑置顶/免打扰/清空/删除仍可用，有按压与动作反馈。  
2. 发现页无「今日动态」大卡；朋友圈等入口仍在角色空间网格，磁贴有按压反馈。  
3. 「我」页为分组卡片 + 语义图标；含「预设库」；设置页无「预设管理」重复行。  
4. 侧栏可切换本会话预设；仅影响该会话请求上下文。  
5. 清空会话预设后回退角色默认（再全局）。  
6. 背景/立绘可在侧栏视觉页修改，会话资料全页不再承载该块。  
7. 新建角色：仅名字可创建；高级项不在新建页。  
8. 图标与文案符合 §5；无「今日动态」残留文案。  
9. 相关单测通过；Room migration 覆盖新列。  

## 9. 风险与缓解

| 风险 | 缓解 |
|------|------|
| 用户仍想从消息列表进编辑 | 会话管理入口醒目；文档/变更说明一句即可 |
| 去掉设置预设入口后找不到 | 「我 → 预设库」+ 侧栏 Sheet「管理预设库」双达 |
| migration 漏导 | 版本 +1、单测断言 ALL_MIGRATIONS 连续 |
| 视觉迁移后新建会话流程变长 | 新建不强制设图；侧栏按需补 |
| 打磨过度改动无关页面 | 只碰本规格点名的页面与共享小组件 |

## 10. 规格自检

- [x] 无 TBD / 占位章节  
- [x] 今日动态、设置预设入口决策已写死  
- [x] 数据字段、解析链、UI 位置一致  
- [x] 范围聚焦 IA + 绑定页面的图标/交互，无无关重构  
- [x] 分期与验收可验证  

---

**下一步：** 用户审查本规格 → 通过后用 writing-plans 产出实现计划 → 再编码。
