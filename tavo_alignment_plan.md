# Tavo 对标后续任务

> 上一轮已完成内容（任务 A、任务 B）已归档至 [`docs/tavo-alignment-completed-2026-04-25.md`](docs/tavo-alignment-completed-2026-04-25.md)。
> 本文件仅维护当前未完成任务。

---

## 任务 P1：API 连接合并到提供商页（已完成，1.6.3-dev）

### 背景
当前 `SettingsScreen.kt` 的"模型与服务"分组同时存在两个语义高度重叠的入口：

| 路由 | 屏幕 | 实际职责 |
|------|------|----------|
| `SETTINGS_PROVIDERS` | `ProviderSettingsScreen` | 多 provider 管理（增删/列表/详情，承载真正可用的连接配置） |
| `SETTINGS_CONNECTION` | `SettingsConnectionScreen` | 单层 BaseUrl + ApiKey 表单（fallback 凭据） |

入口冗余、职责拆分混乱。Tavo 的标准做法是"API 连接 → provider 列表 → 单个 provider 详情"——单一入口树。

### 目标
颠覆式重构：删除 `SETTINGS_CONNECTION` 路由及其 Composable，主页"连接与凭据"入口直接对接 Provider 列表。fallback 凭据字段（`AppSettings.baseUrl` / `AppSettings.apiKey`）保留作为数据兜底，但不再在 UI 上单独暴露——provider 列表本身已是其超集。

### 落地记录
- 已删除 `SETTINGS_CONNECTION` 路由、`SettingsConnectionScreen` 页面和相关 `onOpenConnectionSettings` 布线。
- 设置主页"模型与服务"分组已收口为默认模型 / 提供商 / 搜索与工具。
- 沉浸式设置面板的连接入口已改为优先进入当前提供商详情；无当前提供商时进入提供商列表。
- `SettingsViewModel.updateBaseUrl` / `updateApiKey` 已下线，旧 fallback 凭据仅保留底层读取兜底。

### 影响面（需要改动的文件）

#### 删除
- `app/src/main/java/com/example/myapplication/ui/screen/settings/SettingsConnectionScreen.kt`
- `AppRoutes.SETTINGS_CONNECTION` 路由常量
- `SettingsProviderNavRoutes.kt:150-178` 中 `SETTINGS_CONNECTION` 的 `composable { ... }` 注册块

#### 改动
| 文件 | 改动点 |
|------|--------|
| `ui/screen/settings/SettingsScreen.kt` | 移除 `onOpenConnectionSettings` 参数；将"连接与凭据"入口改为直接 `onOpenProviderSettings`（视情况合并入口） |
| `ui/navigation/SettingsNavGraph.kt:69-73` | 移除 `onOpenConnectionSettings` lambda |
| `ui/navigation/RoleplayNavGraph.kt:347-351` | 移除指向 `SETTINGS_CONNECTION` 的 `onOpenConnectionSettings`；视情况改为跳 `SETTINGS_PROVIDERS` 或剥离参数 |
| `viewmodel/SettingsViewModel.kt` | 检查 `updateBaseUrl` / `updateApiKey` 是否仍被外部调用；如仅供 `SettingsConnectionScreen` 使用则一并下线 |
| `model/AppSettings.kt` & `data/local/AppSettingsStore.kt` | 字段保留，仅作 provider 列表为空时的 fallback 凭据；不再做 UI 写入 |

### 改动顺序建议
1. 全仓库 `Grep` 一次 `SETTINGS_CONNECTION`、`SettingsConnectionScreen`、`onOpenConnectionSettings`、`updateBaseUrl`、`updateApiKey` 找全调用方
2. 先调整 `SettingsScreen.kt` 的 UI 与参数签名（移除"连接与凭据"行）
3. 再删 `SettingsNavGraph.kt`、`SettingsProviderNavRoutes.kt`、`RoleplayNavGraph.kt` 中相关布线
4. 删 `SettingsConnectionScreen.kt`、`AppRoutes.SETTINGS_CONNECTION`
5. 评估 `SettingsViewModel.updateBaseUrl/updateApiKey` 是否需要保留（取决于 fallback 数据流）
6. 编译 + 单测 + 安装到机器人工验证：设置首页只剩"提供商"入口；进入后能正常增删/编辑 provider；首次安装、provider 列表为空的情况下不会出现死路（必要时 ProviderSettingsScreen 加引导态）

### 验收清单
- [x] 设置主页"模型与服务"分组只有 3 行：默认模型 / 提供商 / 搜索与工具（"连接与凭据"消失）
- [x] `Grep -r "SETTINGS_CONNECTION"` 在 `app/src/main` 无残留
- [x] `Grep -r "SettingsConnectionScreen"` 在 `app/src/main` 无残留
- [x] `Grep -r "onOpenConnectionSettings"` 在 `app/src/main` 无残留
- [x] `./gradlew.bat app:compileDebugKotlin` 通过
- [x] `./gradlew.bat testDebugUnitTest` 全绿
- [ ] 真机验证：当前终端未识别 `adb`，待有设备环境后补测从无 provider 到发送聊天的完整流程

### 风险点
- **fallback 凭据迁移**：`AppSettings.baseUrl` / `AppSettings.apiKey` 在某些代码路径上可能被旧逻辑读取；需要确认所有读取点都已优先取 `currentProvider`，fallback 只用于无 provider 的极端情况
- **Roleplay 现存调用**：`RoleplayNavGraph.kt:347-351` 把 `onOpenConnectionSettings` 透传给 RoleplaySettingsScreen，需确认 Roleplay 设定面板是否还在用这个回调，没在用就一起删

---

## 任务 P2：真预设系统地基（阶段 1 已完成，1.6.3-dev）

`ContextLogSourceType.PRESET("预设")` 在项目里实际承载的是**角色卡的边角字段**（scenario / greeting / example_dialogues），不是 SillyTavern/酒馆意义上的"Preset"。完整的 Preset 应当是 prompt 工程模板包：system prompt 格式 / 上下文模板 / sampler 参数 / instruct 模式。

### 落地记录
- 已新增 `Preset` / `PresetSamplerConfig` / `PresetInstructConfig` 模型。
- 已新增 `PresetDao`、`PresetEntity`、`PresetRepository` 和 `presets` Room 表，数据库版本推进到 35。
- `AppGraph` 启动时会写入 2 个内置预设：中文角色扮演通用、思考模型稳态。
- `Assistant.defaultPresetId` 字段已加入，但当前不改变 Prompt 组装和模型请求行为。
- 资料库已支持自定义预设包导入导出，内置预设不会被资源包覆盖。

### 下一阶段
- 阶段 2：做占位符引擎扩展和 `PromptContextAssembler` 双轨对照。
- 阶段 3：做预设列表 / 编辑页、Sampler 透传和助手绑定 UI。

完整设计文档位置：`docs/真预设功能设计.md`。
