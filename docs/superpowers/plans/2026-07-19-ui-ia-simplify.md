# UI 信息架构精简 + 交互打磨 Implementation Plan

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 去掉重复入口（消息三点、今日动态、设置内预设入口），把本会话配置收进侧栏（含会话级预设与视觉），精简新建角色，并同步完成图标/交互打磨。

**架构：** 全局（「我」/设置）只做资料库与应用配置；对话内 `RoleplaySettings` 侧栏做本会话运行时配置。`RoleplayScenario.presetId` 空串表示跟随角色→全局；`resolveActivePresetId` 扩展为四级回退；`PromptContextAssembler.assemble` 增加可选 `scenarioPresetId` 供 RP 路径注入。

**技术栈：** Kotlin、Jetpack Compose、Material 3、Room（v47→v48）、手写 DI（AppGraph）、JUnit4、Navigation Compose。

**规格：** `docs/superpowers/specs/2026-07-19-ui-ia-simplify-design.md`

## 外部审查补丁（2026-07-19，非阻塞）

1. **侧栏预设摘要三种来源**：`本会话` / `跟随角色` / `跟随全局`（勿笼统只写「跟随角色」）。
2. **已删预设名称兜底**：`scenario.presetId` 指向不存在的预设时，摘要显示 `已失效，回退默认`（或等价），禁止空白。
3. **Task 2 删 phoneEcosystem**：先 `grep` 引用再删 NavGraph/flow；只删真正无用链路。
4. **可选**：顺带更新 `Claude.md` 中过时的「Room v27 / 迁移在 ChatDatabase.kt」表述。

## 全局约束

- 用户可见文案、注释、提交信息：简体中文。
- Compose 订阅：`collectAsStateWithLifecycle()`。
- Gson：`AppJson.gson`。
- Room 版本一次只 +1；迁移登记进 `ChatDbMigrations.ALL`。
- Windows 构建：`.\gradlew.bat`。
- 外科手术式改动：只改本计划点名文件；不顺手重构无关代码。
- UI 打磨与对应阶段**同 PR/同任务交付**，不单独拖尾。
- Commit：每任务完成后提交一次（若用户当轮禁止 commit，则攒到用户允许时再提交）。

---

## 文件结构

| 文件 | 责任 | 任务 |
| --- | --- | --- |
| `ui/screen/immersive/ImmersivePhoneShell.kt` | 消息行去三点、左滑反馈、发现页去今日动态、磁贴交互、「我」分组+预设库入口 | 1、2、3 |
| `ui/screen/settings/SettingsScreen.kt` + `SettingsNavGraph.kt` | 去掉设置页预设管理入口 | 3 |
| `ui/navigation/RoleplayNavGraph.kt` | 「我」打开预设库导航；侧栏预设/视觉回调接线 | 3、5、6 |
| `model/PresetSelection.kt` | 四级 `resolveActivePresetId` + 跟随判断辅助 | 4 |
| `model/RoleplayScenario.kt` | 字段 `presetId` | 4 |
| `data/local/roleplay/RoleplayScenarioEntity.kt` | 列 `presetId` | 4 |
| `data/local/roleplay/RoleplayChatSummaryRow.kt` + `RoleplayDao.kt` | 摘要查询带上 `presetId` | 4 |
| `data/local/chat/ChatDatabase.kt` | `CURRENT_VERSION = 48` | 4 |
| `data/local/chat/migrations/ChatDbMigrations.kt` | `MIGRATION_47_48` + ALL 登记 | 4 |
| `data/repository/roleplay/RoleplayRepository.kt` | entity↔domain 映射 `presetId` | 4 |
| `context/PromptContextAssembler.kt` | `assemble(..., scenarioPresetId)` | 4 |
| 调用方：`RoleplayRoundTripExecutor` / `RoleplayViewModel` / 其它 RP assemble | 传入 `scenario.presetId` | 4 |
| `viewmodel/RoleplayViewModel.kt` | `updateScenarioPresetId`（或复用 upsert） | 5 |
| `ui/screen/roleplay/RoleplaySettingsSidebarPanel.kt` + `RoleplaySettingsScreen.kt` | 本会话预设行+Sheet、VISUAL 页 | 5、6 |
| `ui/screen/roleplay/RoleplayScenarioEditScreen.kt` | 去掉视觉资源区 | 6 |
| `ui/screen/settings/AssistantBasicScreen.kt` + `SettingsAssistantNavRoutes.kt` | 新建角色最小集 | 7 |
| 测试见各任务 | 解析链、migration 注册、映射 | 4 等 |

**可选不改：** `RoleplayPhoneEcosystemSnapshot` 数据层可保留；本轮 UI 不再调用。若 Sheet/Entry 无引用则删除死代码。

---

## Task 1: 消息列表去掉三点 + 行交互（C1 前半）

**Files:**
- 修改：`app/src/main/java/com/example/myapplication/ui/screen/immersive/ImmersivePhoneShell.kt`（`ChatSummaryRow`、`SwipeActions`、调用处）

- [ ] **步骤 1：去掉三点入口**

1. 删除 `ChatSummaryRow` 的 `onEdit` 参数与右侧 `IconButton(MoreVert)`。
2. 调用处删除 `onEdit = { callbacks.onOpenChatEdit(...) }` 分支（保留 `onOpenChatEdit` 供会话管理等其它入口使用，不要删回调定义）。
3. 行布局：头像 + 标题/时间 + 摘要/置顶免打扰图标；文字区吃满右侧空间。

- [ ] **步骤 2：行按压与左滑动作样式**

1. 行 `clickable` 使用默认涟漪（或 `MutableInteractionSource` + 按压时 `surfaceVariant` 背景）。
2. `SwipeActions` 四色语义：置顶 primary、免打扰 tertiary、清空 outline 系、删除 error；圆角色块 + 白/on 色图标。
3. 动作执行时：`LocalHapticFeedback.current.performHapticFeedback(HapticFeedbackType.LongPress)`（或项目已有更轻类型）。

- [ ] **步骤 3：编译校验**

```powershell
.\gradlew.bat app:compileDebugKotlin --console=plain
```

预期：成功。

- [ ] **步骤 4：Commit（若允许）**

```text
refactor(immersive): 消息列表去掉三点并打磨左滑交互
```

---

## Task 2: 发现页移除今日动态 + 磁贴交互（C1 后半）

**Files:**
- 修改：`ImmersivePhoneShell.kt`（`ImmersiveDiscoverPage`、`PhoneEcosystemEntry`、`PhoneEcosystemSheet`、相关 state）

- [ ] **步骤 1：移除入口与 UI**

1. `ImmersiveDiscoverPage` 删除顶部 `PhoneEcosystemEntry` item。
2. 删除 `showPhoneEcosystemSheet` 状态、`PhoneEcosystemSheet` 调用。
3. 若 `PhoneEcosystemEntry` / `PhoneEcosystemSheet` / `phoneEcosystemSummaryText` 等仅被发现页使用 → 删除这些 private composable，避免死代码。
4. `phoneEcosystem` 参数若仅服务该卡：可从 `ImmersivePhoneShell` 签名与 `RoleplayNavGraph` 传参中移除（注意只删真正无用的链路，数据构建函数可暂留）。

- [ ] **步骤 2：发现页 UI 收尾**

1. 保留「AI 创作」双磁贴 + 「角色空间」网格；顶部 padding 正常（无大卡后不要留巨大空白）。
2. `DiscoverAiTile` / `DiscoverSpaceTile`：按压缩放约 `0.97`（`collectIsPressedAsState` + `graphicsLayer`），保留涟漪。
3. 确认无用户可见「今日动态」字符串残留（全文件 grep）。

- [ ] **步骤 3：编译校验**

```powershell
.\gradlew.bat app:compileDebugKotlin --console=plain
```

- [ ] **步骤 4：Commit**

```text
refactor(discover): 移除今日动态大卡并强化磁贴按压反馈
```

---

## Task 3: 「我」分组 + 预设库入口；设置去预设行（C2）

**Files:**
- 修改：`ImmersivePhoneShell.kt`（`ImmersiveProfilePage`、`FeatureRow`）
- 修改：`ImmersivePhoneCallbacks` 同文件内回调 data class（增加 `onOpenPresetLibrary`）
- 修改：`RoleplayNavGraph.kt`（接 `AppRoutes.SETTINGS_PRESETS`）
- 修改：`SettingsScreen.kt`、`SettingsNavGraph.kt`（去掉设置内预设入口）

- [ ] **步骤 1：升级「我」页结构**

目标结构：

```
[头像 Hero + 面具切换]

资料库（分组卡片）
  会话管理 / 角色资料 / 世界书 / 记忆档案 / 预设库 / 资料导入导出

应用（分组卡片）
  设置 / 关于
```

1. 将扁平 `FeatureRow + Divider` 改为分组 `Surface` 卡片（圆角 18–20dp，组内 `HorizontalDivider`，组外间距 16–20dp）。
2. 行组件升级（可重命名为 `ProfileLibraryRow`）：
   - leading 图标容器 44dp、圆角 12–14dp
   - title SemiBold + subtitle
   - trailing `KeyboardArrowRight`
3. 图标语义（规格 §5.1）：
   - 会话管理 `Chat`、角色 `Person`、世界书 `MenuBook`、记忆 `AutoStories`、**预设库 `LibraryBooks`**、导入导出优先 `ImportExport`、设置 `Settings`、关于 `Info`
4. 资料库图标容器可用 `secondaryContainer`；应用组可用 `surfaceVariant` / `tertiaryContainer` 区分。

- [ ] **步骤 2：导航到预设库**

1. `ImmersivePhoneCallbacks` 增加 `onOpenPresetLibrary: () -> Unit`。
2. `RoleplayNavGraph` 中：

```kotlin
onOpenPresetLibrary = {
    navController.navigate(AppRoutes.SETTINGS_PRESETS) {
        launchSingleTop = true
    }
}
```

3. 「预设库」行 `onClick = onOpenPresetLibrary`。

- [ ] **步骤 3：设置页去掉「预设管理」**

1. `SettingsScreen` 删除「预设管理」`SettingsListRow` 及 `onOpenPresetSettings` 参数（若仅此一处使用）。
2. `SettingsNavGraph` 删除对应 lambda 传参。
3. **保留** `AppRoutes.SETTINGS_PRESETS` 路由与 `PresetListScreen` 注册（「我」与侧栏仍要跳转）。
4. 角色详情「默认预设」**不要动**。

- [ ] **步骤 4：编译校验**

```powershell
.\gradlew.bat app:compileDebugKotlin --console=plain
```

- [ ] **步骤 5：Commit**

```text
feat(profile): 我页分组资料库与预设库入口，设置页去重
```

---

## Task 4: 会话级 presetId 数据层 + 解析链（C3 数据）

**Files:**
- 修改：`model/PresetSelection.kt`
- 修改：`model/RoleplayScenario.kt`
- 修改：`data/local/roleplay/RoleplayScenarioEntity.kt`
- 修改：`data/local/roleplay/RoleplayChatSummaryRow.kt`
- 修改：`data/local/roleplay/RoleplayDao.kt`（`observeChatSummaryRows` SELECT 增加 `s.presetId`）
- 修改：`data/repository/roleplay/RoleplayRepository.kt`（三处 entity 映射）
- 修改：`data/local/chat/ChatDatabase.kt`（`CURRENT_VERSION = 48`）
- 修改：`data/local/chat/migrations/ChatDbMigrations.kt`（`MIGRATION_47_48` + ALL）
- 修改：`context/PromptContextAssembler.kt`
- 修改 RP 调用方：`RoleplayRoundTripExecutor.kt`、`RoleplayViewModel.kt` 中 `assemble` 调用；其它仅 CHAT 路径保持默认
- 修改测试 Fake / Entity 构造：`RoleplayRepositoryTest.kt` 等凡 `RoleplayScenarioEntity(...)` 处（默认参数可减少改动）
- 测试：新建 `app/src/test/java/com/example/myapplication/model/PresetSelectionTest.kt`
- 测试：`ChatDatabaseMigrationRegistryTest` 会随 CURRENT_VERSION 自动要求 `MIGRATION_47_48` 为 last

- [ ] **步骤 1：写失败测试（Preset 解析）**

创建 `PresetSelectionTest.kt`：

```kotlin
package com.example.myapplication.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetSelectionTest {
    @Test
    fun resolve_prefersScenarioThenAssistantThenGlobal() {
        assertEquals(
            "scenario-preset",
            resolveActivePresetId(
                globalDefaultPresetId = "global-preset",
                assistantDefaultPresetId = "assistant-preset",
                scenarioPresetId = "scenario-preset",
            ),
        )
        assertEquals(
            "assistant-preset",
            resolveActivePresetId(
                globalDefaultPresetId = "global-preset",
                assistantDefaultPresetId = "assistant-preset",
                scenarioPresetId = "",
            ),
        )
        assertEquals(
            "global-preset",
            resolveActivePresetId(
                globalDefaultPresetId = "global-preset",
                assistantDefaultPresetId = "",
                scenarioPresetId = null,
            ),
        )
        assertEquals(
            DEFAULT_PRESET_ID,
            resolveActivePresetId(
                globalDefaultPresetId = "",
                assistantDefaultPresetId = null,
                scenarioPresetId = "   ",
            ),
        )
    }

    @Test
    fun isScenarioPresetFollowingAssistant_whenBlank() {
        assertTrue(isScenarioPresetFollowingAssistant(""))
        assertFalse(isScenarioPresetFollowingAssistant("custom"))
    }
}
```

说明：`isScenarioPresetFollowingAssistant` 可实现为 `presetId.isBlank()`；若规格用文案「跟随角色」即可，函数名可简化。

- [ ] **步骤 2：运行测试确认失败**

```powershell
.\gradlew.bat app:testDebugUnitTest --tests "com.example.myapplication.model.PresetSelectionTest" --console=plain
```

预期：编译失败或断言失败（当前 API 无 `scenarioPresetId`）。

- [ ] **步骤 3：实现 `PresetSelection.kt`**

```kotlin
fun resolveActivePresetId(
    globalDefaultPresetId: String,
    assistantDefaultPresetId: String?,
    scenarioPresetId: String? = null,
): String {
    val globalPresetId = globalDefaultPresetId.trim().ifBlank { DEFAULT_PRESET_ID }
    val scenarioId = scenarioPresetId?.trim().orEmpty()
    if (scenarioId.isNotEmpty()) return scenarioId
    val assistantPresetId = assistantDefaultPresetId?.trim().orEmpty()
    return when {
        assistantPresetId.isBlank() -> globalPresetId
        else -> assistantPresetId
    }
}

fun isScenarioPresetFollowingAssistant(scenarioPresetId: String?): Boolean =
    scenarioPresetId?.trim().isNullOrEmpty()
```

保持 `isAssistantPresetFollowingGlobal` 行为不变。

- [ ] **步骤 4：Room 字段与迁移**

1. `RoleplayScenario` / `RoleplayScenarioEntity` 增加：

```kotlin
val presetId: String = ""
```

2. `RoleplayChatSummaryRow` 增加 `presetId`，DAO SQL 增加：

```sql
s.presetId AS presetId,
```

（位置与其它 s.* 字段一起，建议在 `assistantId` 后或 `isMuted` 前。）

3. `RoleplayRepository` 的 `toScenarioDomain` / `toScenarioEntity` / `RoleplayChatSummaryRow.toScenarioEntity` 映射 `presetId`。

4. `ChatDatabase.CURRENT_VERSION = 48`。

5. `ChatDbMigrations`：

```kotlin
val MIGRATION_47_48 = object : Migration(47, 48) {
    override fun migrate(db: SupportSQLiteDatabase) {
        if (!hasColumn(db, "roleplay_scenarios", "presetId")) {
            db.execSQL(
                "ALTER TABLE roleplay_scenarios ADD COLUMN presetId TEXT NOT NULL DEFAULT ''",
            )
        }
    }
}
```

并加入 `ALL` 数组末尾。

6. 若 `app/schemas` 需要导出，按项目惯例在编译时生成；勿跳版。

- [ ] **步骤 5：Assembler 与 RP 调用**

1. `PromptContextAssembler.assemble` 增加默认参数：

```kotlin
scenarioPresetId: String? = null,
```

接口与实现、所有调用点补齐（CHAT 路径不传即可）。

2. `resolveActivePresetId(...)` 调用处传入 `scenarioPresetId`。

3. RP 路径（至少）：
   - `RoleplayRoundTripExecutor`
   - `RoleplayViewModel` 内 assemble
   - `RoleplaySuggestionCoordinator` / `PhoneContextBuilder` 若能拿到 scenario 则传入，否则保持 null（跟随角色/全局）

- [ ] **步骤 6：跑测试**

```powershell
.\gradlew.bat app:testDebugUnitTest --tests "com.example.myapplication.model.PresetSelectionTest" --console=plain
.\gradlew.bat app:testDebugUnitTest --tests "com.example.myapplication.data.local.chat.ChatDatabaseMigrationRegistryTest" --console=plain
.\gradlew.bat app:testDebugUnitTest --tests "com.example.myapplication.data.repository.roleplay.RoleplayRepositoryTest" --console=plain
```

预期：全部通过。Entity 构造若因无默认值失败，给 `presetId: String = ""` 默认参数。

- [ ] **步骤 7：Commit**

```text
feat(preset): 会话级 presetId 与四级解析回退
```

---

## Task 5: 侧栏「本会话预设」UI + VM（C3 UI）

**Files:**
- 修改：`RoleplaySettingsSidebarPanel.kt`（`RoleplaySettingsPanelPage` 若需；主列表行；Sheet）
- 修改：`RoleplaySettingsScreen.kt`（透传 presets、回调）
- 修改：`RoleplayViewModel.kt`（`updateScenarioPresetId`）
- 修改：`RoleplayNavGraph.kt` / 设置屏导航装配（observe presets、onOpenPresetLibrary）
- 可能修改：打开设置侧栏的 Composable 参数列表

- [ ] **步骤 1：VM 方法**

```kotlin
fun updateScenarioPresetId(presetId: String) {
    val scenario = /* 当前 scenario */ ?: return
    upsertScenario(scenario.copy(presetId = presetId.trim()))
}
```

（与现有 `updateScenarioNarrationEnabled` 同风格。）

- [ ] **步骤 2：侧栏主列表增加行**

在「会话设定」卡中，于「用户身份」与「主题」之间（或身份之后）增加：

```text
本会话预设
摘要：{activeName} · 跟随角色 | {activeName} · 本会话
```

- `activeId = resolveActivePresetId(global, assistant?.defaultPresetId, scenario?.presetId)`
- 跟随：`isScenarioPresetFollowingAssistant(scenario?.presetId)`

点击打开 ModalBottomSheet（或项目惯用 sheet）：

1. 首项：「跟随角色默认」→ `updateScenarioPresetId("")`
2. 列表：全部 `Preset`（`presetRepository.observePresets()`）
3. 当前生效项 Check
4. 底部 TextButton：「管理预设库」→ `onOpenPresetLibrary()`

交互：点选后关闭 Sheet；可选轻触觉。

- [ ] **步骤 3：接线**

- Nav/Settings 屏收集 `presets` 与 `globalDefaultPresetId`（`storedSettings.defaultPresetId`）。
- 传入侧栏；`onSelectScenarioPreset` / `onOpenPresetLibrary` 接到 VM 与导航。

- [ ] **步骤 4：编译**

```powershell
.\gradlew.bat app:compileDebugKotlin --console=plain
```

- [ ] **步骤 5：Commit**

```text
feat(roleplay): 侧栏支持切换本会话预设
```

---

## Task 6: 视觉迁入侧栏 + 会话资料瘦身（C4）

**Files:**
- 修改：`RoleplaySettingsSidebarPanel.kt` — `RoleplaySettingsPanelPage.VISUAL`（或 `SCENE` 内嵌，规格为独立 VISUAL）
- 修改：`RoleplaySettingsScreen.kt` — 标题「背景与立绘」/「视觉」
- 修改：`RoleplayScenarioEditScreen.kt` — 删除「视觉资源」整块
- 复用：编辑页现有 `ScenarioImagePickerCard` 逻辑可抽到 `internal` 共用，或侧栏内复制精简版（优先抽到同模块 private 以免大范围移动）

- [ ] **步骤 1：侧栏 VISUAL 子页**

字段与会话资料原逻辑一致：

- 背景图（本地）
- 用户立绘（本地 + URL）
- 角色立绘（本地 + URL）

保存：`upsertScenario(scenario.copy(backgroundUri=..., userPortraitUri=..., ...))`  
主列表摘要：`已设背景` / `已设立绘` / `默认视觉`（按非空字段组合短文案）。

- [ ] **步骤 2：会话资料页删除视觉区**

删除 `SettingsSectionHeader("视觉资源")` 及三个 `ScenarioImagePickerCard` 与仅服务于该区的 launcher（若开场等仍需要 launcher 则保留）。

新建/编辑页说明可加一行：`背景与立绘请在会话设定中修改`（可选，一句即可）。

- [ ] **步骤 3：编译**

```powershell
.\gradlew.bat app:compileDebugKotlin --console=plain
```

- [ ] **步骤 4：Commit**

```text
refactor(roleplay): 视觉配置迁入侧栏并精简会话资料页
```

---

## Task 7: 新建角色最小集（C5）

**Files:**
- 修改：`AssistantBasicScreen.kt`
- 修改：`SettingsAssistantNavRoutes.kt`（保存后逻辑，若需「创建并开始」再接 Roleplay）

- [ ] **步骤 1：isNew UI 精简**

当 `isNew == true`：

- 保留：名字（必填）、头像/图标、简介、可选「核心人设」多行（保存时写入 `systemPrompt` 初值）
- 隐藏或不要展示：与「能聊起来」无关的大段（若当前页已只有基础字段，则确保人设短文本可编辑且保存进 prompt）
- 主按钮：「创建」；校验 `name.isNotBlank()`
- 成功：现有 `onSave` 流程 + Snackbar「已创建角色」（若宿主已有 message 流则复用）

**本轮最小交付：** 不强制实现「创建并开始会话」导航（规格为可选）。若实现成本低（创建后 `createChatForAssistant`），可做次按钮；否则只做创建回列表。

- [ ] **步骤 2：编辑已有角色**

`isNew == false` 保持现有基础设定字段，不强行砍功能。

- [ ] **步骤 3：编译**

```powershell
.\gradlew.bat app:compileDebugKotlin --console=plain
```

- [ ] **步骤 4：Commit**

```text
feat(assistant): 精简新建角色为最小必填集
```

---

## Task 8: 总验证与规格勾选

- [ ] **步骤 1：相关单测**

```powershell
.\gradlew.bat app:testDebugUnitTest --tests "com.example.myapplication.model.PresetSelectionTest" --console=plain
.\gradlew.bat app:testDebugUnitTest --tests "com.example.myapplication.data.local.chat.ChatDatabaseMigrationRegistryTest" --console=plain
.\gradlew.bat app:testDebugUnitTest --tests "com.example.myapplication.data.repository.roleplay.RoleplayRepositoryTest" --console=plain
.\gradlew.bat app:testDebugUnitTest --tests "com.example.myapplication.context.PresetPromptRendererTest" --console=plain
```

- [ ] **步骤 2：编译**

```powershell
.\gradlew.bat app:compileDebugKotlin --console=plain
```

- [ ] **步骤 3：人工对照验收（规格 §8）**

| # | 项 | 通过 |
|---|----|------|
| 1 | 消息列表无三点；左滑仍可用 | ☐ |
| 2 | 发现页无今日动态；朋友圈等入口在 | ☐ |
| 3 | 「我」分组+预设库；设置无预设管理行 | ☐ |
| 4 | 侧栏可切本会话预设且影响请求 | ☐ |
| 5 | 清空会话预设回退角色默认 | ☐ |
| 6 | 视觉在侧栏；会话资料无视觉块 | ☐ |
| 7 | 新建角色名字即可创建 | ☐ |
| 8 | 无「今日动态」文案残留 | ☐ |

- [ ] **步骤 4：更新规格状态**

将 `docs/superpowers/specs/2026-07-19-ui-ia-simplify-design.md` 状态改为「已实现」或「实现中」并注明完成任务号。

---

## 规格覆盖自检

| 规格章节 | 任务 |
|----------|------|
| 4.1 消息三点 | Task 1 |
| 4.2 今日动态 | Task 2 |
| 4.3 我/设置入口 | Task 3 |
| 4.4 会话预设数据+侧栏 | Task 4–5 |
| 4.5 视觉+资料瘦身 | Task 6 |
| 4.6 新建角色 | Task 7 |
| §5 图标/交互 | 绑定 Task 1–3、5–6，不单开 |
| §8 验收 | Task 8 |

## 风险

| 风险 | 处理 |
|------|------|
| `assemble` 签名变更导致测试 mock 编译失败 | 默认参数 `scenarioPresetId = null`；补测试编译 |
| DAO 投影漏列 | 同步改 SQL + `RoleplayChatSummaryRow` + Repository 映射 |
| 设置去掉预设后用户迷路 | 「我→预设库」+ 侧栏「管理预设库」双达 |
| ImmersivePhoneShell 文件过大 | 本轮不强制拆文件；若改动冲突严重可只抽 Profile 行组件 |

## 建议执行方式

1. **子代理驱动（推荐）**：每任务独立子代理 + 审查  
2. **本会话内联**：按 Task 1→8 顺序执行  

**不要在 main 上强推未验证的大改；** 可在当前分支直接做，或按用户要求开 worktree。

---

## 给审查者（GPT / 人工）的要点

1. **数据唯一新增字段**：`roleplay_scenarios.presetId`，空=跟随。  
2. **解析优先级**：session → assistant → global → 内置。  
3. **UI 删三项入口**：消息三点、今日动态卡、设置「预设管理」。  
4. **UI 增/迁**：侧栏本会话预设、侧栏视觉；「我」预设库。  
5. **Room 47→48** 必须登记且 migration 测试通过。  
6. **不改** 朋友圈/信箱业务、不改主题色板、不删预设库能力本身。
