# Tavo 对标重构方案：长记忆管理 & 上下文日志（已完成归档）

> 归档时间：2026-04-25
> 归档说明：本文档为 `tavo_alignment_plan.md` 的历史副本，记录任务 A（A1/A2/A3/A4）与任务 B（B1/B2）的设计与落地详情。后续推进的 P0/P1/P2 在 `tavo_alignment_plan.md` 中维护。

---

## 一、Tavo 设计分析

### 1. 长记忆管理（Tavo）

![Tavo 长记忆管理](file:///d:/code/AndroidStudioprojects/MyApplication/tavo_mem3.png)

**设计特征：**
- **极简全屏页面**：只有标题 + 一个大文本编辑区域 + 保存按钮
- **纯文本编辑**：所有记忆条目写在一个 `TextField` 中，每行 `- xxx` 是一条记忆
- **底部提示**：`ⓘ 可以在此编辑记忆内容，每行为一条记忆点`
- **渐变保存按钮**：全宽、渐变色（紫→橙），很有辨识度
- 没有分类筛选、搜索、角色过滤等复杂功能

### 2. 上下文日志（Tavo）

````carousel
![Tavo 上下文日志 - 详情](file:///d:/code/AndroidStudioprojects/MyApplication/tavo_ctx.png)
<!-- slide -->
![Tavo 上下文日志 - 列表](file:///d:/code/AndroidStudioprojects/MyApplication/tavo_ctx3.png)
````

**设计特征：**
- **列表视图**：按时间倒序列出所有日志条目，每条显示 prompt 首行摘要 + 时间戳 + `⋮` 操作按钮
- **详情视图**（点进一条后）：
  - 顶部：API 信息卡片（来源、模型名），右上角有"长记忆"快捷入口按钮
  - 元数据行：日期时间 + token 数
  - 组件列表：世界书、预设、正则 等参与的组件 + 值
  - 提示文案：`ⓘ tokens 仅为预估值...`
  - 内容区域：完整 prompt 正文
  - **底部分段标签栏**：角色卡 / 聊天历史 / 预设 / 世界书 / 长记忆，彩色圆点标识

---

## 二、Narra 现状 vs Tavo 差异

### 长记忆管理

| 维度 | Narra 现状 | Tavo 设计 | 差距 |
|------|-----------|-----------|------|
| **入口** | 导航到独立全屏页 | 导航到独立全屏页 | ✅ 一致 |
| **架构** | Scaffold + LazyColumn，支持角色筛选、搜索、分类 tab（全部/核心/情景/总结/时间线） | 简洁单页 TextField 编辑 | 🔴 Narra 过度工程化 |
| **记忆编辑** | 每条记忆是独立卡片，有 pin/delete 操作 | 全部记忆合并成一个可编辑文本框 | 🔴 Narra 更功能化但不直觉 |
| **保存** | 实时自动保存（pin/delete 按钮即操作） | 统一保存按钮 | 🟡 交互模式不同 |
| **视觉** | Material3 标准卡片 + 边框 | 极简纯白卡片 + 渐变保存按钮 | 🟡 风格差异 |

### 上下文日志

| 维度 | Narra 现状 | Tavo 设计 | 差距 |
|------|-----------|-----------|------|
| **入口** | ModalBottomSheet 弹出（roleplay 设定面板 + chat `ChatScreenOverlays` 都已接通） | 独立全屏页面 | 🔴 交互模式不同（A1 改全屏） |
| **多条日志** | ❌ 只显示最新一条快照 | ✅ 保留最近 15 条，按时间列表 | 🔴 核心功能缺失 |
| **详情布局** | ✅ 已对齐 Tavo：API 信息卡 + 时间/tokens + 组件摘要 + 彩色竖线分段块 + 底部彩色圆点标签栏（已支持点击筛选） | 同左 | ✅ 主体已完成（git 工作区） |
| **右上角"长记忆"快捷入口** | ✅ 已加（Roleplay/Chat 两个调用点都已 wire 到 `SETTINGS_MEMORY` 导航） | ✅ 复制 + 长记忆入口 | ✅ A3 完成 |
| **FontFamily.Cursive** | ✅ 已不再使用 | 不使用 | ✅ 已对齐 |
| **列表项菜单** | — | 右侧 `⋮` 三点菜单 = 导出 JSON / 删除 | 🔴 A2 新功能 |

---

## 三、重构方案

### 任务 A：上下文日志重构（优先级 P0，**✅ 已完成**）

> [!IMPORTANT]
> 上下文日志是调试和理解模型行为的核心工具，重构意义远大于长记忆管理。

#### A1. 从 BottomSheet 改为独立全屏页面（✅ 已完成）
- 新增路由 `AppRoutes.SETTINGS_CONTEXT_LOG`（顶层路由，不绑定 roleplay/chat 任何场景）
- 创建 `ContextLogScreen.kt`（替代 `ContextGovernanceSheet.kt` 的 ModalBottomSheet 容器；详情页 Composable 抽为公共 `ContextLogDetailBody`）
- 入口梳理：
  - **Roleplay 设定面板**：已改为 `navigate(SETTINGS_CONTEXT_LOG)`（`RoleplaySettingsScreen` / `RoleplaySettingsContent` / `RoleplaySettingsSidebarPanel` / `RoleplayNavGraph` 全链路改名 `onOpenContextLog`）
  - **Chat 模式**：原本 `ChatStateSupport`/`ChatViewModel` 已维护 `contextGovernance` 但无入口；A1 顺势在 Chat 顶栏接通了 `onOpenContextLog`（替代旧的 `onOpenPromptDebugSheet`）
- 旧的 `ContextGovernanceSheet` Composable 已整体删除，BottomSheet 容器一并下线（颠覆式破坏性更改）

#### A2. 支持多条日志列表（✅ 已完成）

**存储层（基于 ADB 实测确认，已落地）：**
- **内存 ring buffer，上限 15 条**，不做 Room 持久化（容器：`AppGraph.contextLogStore` 单例）
- **作用域：全局合并 15 条**（chat + roleplay + memory 三类同列），不按场景隔离
- **触发时机**：每次"请求发起前"落一条；`ChatViewModel` / `RoleplayViewModel` / `MemoryProposalPromptService` 三方都向它写
- ViewModel：`ContextLogViewModel` 通过 `stateIn(WhileSubscribed(5_000))` 订阅 store

**列表页 UI（已对齐 Tavo 实测截图）：**
- 纯时间倒序列表，无类型筛选 tab
- 每条显示 prompt 第一行（原样）+ 时间戳（当天 `HH:mm`、跨天 `M月d日 HH:mm`）+ 右侧 `⋮` 菜单（导出 JSON / 删除单条）
- 列表底部固定文案：「每次发送请求到模型时的上下文日志，最多保留 15 条」
- 点击条目进入详情页（复用 `ContextLogDetailBody`）

#### A3. 详情页布局对标 Tavo（**✅ 已完成**）
- ✅ 顶部信息卡：API 来源 + 模型 + 类型标签（`ContextGovernanceSheet.kt:303` `ContextLogApiCard`）
- ✅ 元数据行：时间 + tokens 估算（`ContextGovernanceSheet.kt:378-401`）
- ✅ 组件摘要列表：每个 sourceType 一行（`ContextGovernanceSheet.kt:414-454`）
- ✅ 彩色竖线分段块（`ContextLogSectionBlock` `ContextGovernanceSheet.kt:462-516`）
- ✅ 底部彩色圆点标签栏 + 点击筛选（`ContextLogSourceTabBar` `ContextGovernanceSheet.kt:520-571`）
- ✅ 不再使用 `FontFamily.Cursive`
- ✅ 顶部右上角"长记忆"快捷入口按钮：`Psychology` 图标按钮，导航到 `SETTINGS_MEMORY`

#### A4. 长记忆提取提示词查看（依赖 A2 落地，✅ 已完成）

**实测发现 Tavo 的关键行为（已对齐）：**
- Tavo 把每次记忆提取请求作为一条独立日志写入和聊天日志同列的列表
- 详情页只有一个 system 段，右上角通过 `聊天` / `长记忆` 标签区分类型
- system prompt 内部包含 `# 已知信息处理 [重要]` 段

**实现路径（已落地）：**
1. ✅ **升级 `MemoryProposalPromptService`**：`generateMemoryEntries` / `generateRoleplayMemoryEntries` / `condenseRoleplayMemories` 三个方法都新增 `existingMemories` 参数；`appendKnownMemoriesSection` 在 prompt 末尾附加 `# 已知信息处理 [重要]` 块（`<已知信息>...</已知信息>` 包裹）
2. ✅ **写入 `ContextLogStore`**：在 `requestCompletionContent` 调用之前 `pushContextLog(prompt, ...)`，构造一条 `promptModeLabel = "长记忆"`、单 SYSTEM_RULE section 的 snapshot
3. ✅ **接通已存记忆**：`ConversationMemoryExtractionCoordinator` 新增 `collectExistingChatMemories` / `collectExistingRoleplayMemories`，从 `MemoryRepository` 按 scope 过滤后传入提示词
4. ✅ **详情页精简布局**：通过单 SYSTEM_RULE section 数据驱动天然产出"只有一个 system 段"的形态，无需额外 UI 分支

### 任务 B：长记忆管理简化（优先级 P1，**✅ 已完成**）

#### B1. 新增"简洁编辑模式"（✅ 已完成）
- 保留现有的高级管理页面（适合 power user）：`AssistantMemoryScreen` + `MemoryManagementScreen` 维持不变
- 在 roleplay settings 的入口改为优先打开 Tavo 式简洁编辑页：`RoleplayNavGraph` 的 `onOpenLongMemorySettings` 在 `assistantId` 非空时跳转 `SETTINGS_ASSISTANT_MEMORY_SIMPLE`
- 简洁页面：标题 + 大 TextField（每行一条 `- xxx`） + Info 提示 + 紫→粉→橙渐变保存按钮 + "切换高级管理"卡片
- 实现位置：
  - 路由 `AppRoutes.SETTINGS_ASSISTANT_MEMORY_SIMPLE = "settings/assistants/{assistantId}/memory/simple"` + `settingsAssistantMemorySimple(id)` 工厂
  - Composable `ui/screen/settings/memory/SimpleMemoryEditorScreen.kt`
  - 注册 `SettingsAssistantNavRoutes.kt:198-220`

#### B2. 保存逻辑（✅ 已完成）
- 用户编辑后点保存 → 按行解析 `- ` / `*` / `•` / `·` 列表标记 → 与现有记忆按 content diff → 仅做新增/删除（同 content 保留原 id 与 pinned）
- 作用域：根据 `assistant.useGlobalMemory` 决定写入 GLOBAL（scopeId 留空）或 ASSISTANT（scopeId = assistantId）；CONVERSATION scope（情景记忆）与 `ConversationSummary`（剧情摘要）不参与
- 自动去重保序（LinkedHashSet），空行/纯空白跳过
- 实现位置：`viewmodel/SimpleMemoryEditorViewModel.kt`
- 单测：`test/.../viewmodel/SimpleMemoryEditorViewModelTest.kt`，4 个用例覆盖列表标记解析+去重 / pinned 优先排序 / save 增删 diff / GLOBAL 作用域写入

> [!NOTE]
> 实施时确认：保留高级管理页（`AssistantMemoryScreen`、`MemoryManagementScreen`）为可选入口，避免丢失 Narra 现有的 pin/分类/时间线/摘要等功能。简洁页适合"快速整理核心记忆"，高级页适合"按维度审计"。

---

## 四、Tavo 长记忆提取提示词结构（补充调研）

````carousel
![长记忆提取提示词 - 上半部分](file:///d:/code/AndroidStudioprojects/MyApplication/tavo_mem_prompt1.png)
<!-- slide -->
![长记忆提取提示词 - 下半部分](file:///d:/code/AndroidStudioprojects/MyApplication/tavo_mem_prompt2.png)
````

**Tavo 长记忆提取的完整 Prompt 结构：**

| 段落 | 内容 |
|------|------|
| 角色定义 | "你是信息提取专家，负责从对话中识别并提取..." |
| 提取重点 | 关键信息（仅重要信息）、重要事件（记忆深刻的互动） |
| 提取范围 | 个人/偏好/健康/事件/关系/价值观/忽略思考 |
| 已知信息处理 | `<已知信息>..已有记忆..</已知信息>` |
| 去重规则 | 新信息必须与已知信息比对，相同/冲突的忽略 |
| 输出规范 | `- ` 无序列表，每行一条，无新信息返回空白 |
| 输出示例 | 3 条示例记忆 |
| 底部标注 | `system  479 tokens` |

> [!IMPORTANT]
> 这条日志是**独立的长记忆提取请求**（非对话请求），因此只有一个 system 段，
> 没有角色卡/聊天历史/预设等分段。Tavo 通过 `聊天` 和 `长记忆` 右上角标签来区分类型。

---

## 五、执行顺序回顾

1. ✅ **A3 收尾** — 在 `ContextGovernanceSheet.kt` 顶部右上角增加"长记忆"快捷入口按钮
2. ✅ **A1 + A2 同 PR 落地** — Sheet → 全屏页 + 列表/详情两级视图 + `ContextLogStore` 内存 ring buffer + Chat 入口补齐
3. ✅ **A4** — 升级 `MemoryProposalPromptService` 提示词结构（补 `# 已知信息` 段）+ 写入 `ContextLogStore` + 详情页精简布局
4. ✅ **B1/B2** — 长记忆简洁编辑模式

---

## 六、Tavo 行为参考（ADB 实测确认）

> [!NOTE]
> 本节记录通过 ADB 实机查看 Tavo 上下文日志后确认的行为细节。

### 列表页
- 容器：独立全屏页面，顶部 `<` 返回 + 标题"上下文日志"
- 列表项布局：
  - 主体文本 = prompt 文本第一行（**不清理 HTML/markdown/特殊符号**，原样展示）
  - 时间戳：当天省略日期仅 `HH:mm`；跨天 `M月d日 HH:mm`
  - 右侧 `⋮` 三点菜单：**导出（输出 JSON 文件）/ 删除单条**
  - 行间距较大，每条独立块、无边框
- 底部固定文案：「每次发送请求到模型时的上下文日志，最多保留 15 条」
- **无任何筛选 tab**（chat 和 memory 混排同列）

### 详情页
- 顶部：API 来源 + 模型卡，右上角有"长记忆"快捷入口按钮
- 元数据：日期时间 + tokens
- 组件摘要列表：参与的组件 + 摘要值（圆角图标 + label + value）
- 彩色竖线分段内容块（每段左侧 3dp 彩色竖线 + 内容 + 底部 `sourceType.label.lowercase()` + tokens）
- 底部彩色圆点标签栏：`角色卡 / 聊天历史 / 预设 / 世界书 / 长记忆`
- 长记忆类日志：详情只有一个 system 段，其他分段标签置灰

### 长记忆提示词结构
- 角色定义、提取重点、提取范围、已知信息处理、去重规则、输出规范、输出示例
- **关键**：`# 已知信息处理` 段把当前所有已存记忆原文打包传入做去重
- system 段（实测约 479 tokens）
