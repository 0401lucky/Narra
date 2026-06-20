# 朋友圈微信风格 UI 改造（Spec 2）

- 日期：2026-06-20
- 分支：feat/discover-ui-redesign
- 范围：朋友圈界面贴近微信 + 自选封面 + 独立发布页（配图/位置/身份）+ 下拉刷新
- 依赖：**Spec 1**（`2026-06-20-moments-life-sharing-design.md`）已实现 `refreshWithRandomPost()`
- 状态：设计已确认，待写实现计划

## 背景

参考微信/叙说朋友圈，让界面更有代入感。当前 `MomentsScreen.kt` 是 Material 圆角卡片列表 + 顶部内嵌输入框，不够"像微信"。本 spec 只做 **UI 与配套数据**，生成逻辑见 Spec 1。

## 目标

1. 时间线改为**微信平铺流**：顶部封面头图 + 平铺帖子（左头像、右彩色昵称/正文/单图/位置/时间/点赞评论浅灰区）。
2. 顶部封面头图**用户可自选相册图片**。
3. 用户发帖走**独立发布页**（相机图标进入），含：想法输入、单图配图、所在位置、选择身份。
4. 时间线支持**下拉刷新**触发 Spec 1 的 `refreshWithRandomPost()`。

## 非目标（YAGNI）

- 不做九宫格多图（仅单图，已确认）。
- 不做"谁可以看 / @提醒谁看 / 提醒谁发 / 指定角色发帖"。
- 封面仅用户自己朋友圈的头图，不给角色帖子做封面。
- 不改生成逻辑（Spec 1 负责）。

## 设计

### 组件 1：数据层

**1a. `MomentPost.location`（位置标签）**
- `model/Moment.kt`：`MomentPost` 增加 `val location: String = ""`。
- Room：`moment_posts` 加 `location TEXT NOT NULL DEFAULT ''`。
  - `MomentPostEntity` 加字段；mapper（entity↔model）补 location。
  - 数据库版本 **+1**（当前 `ChatDatabase` v27 → v28），迁移写在 `ChatDbMigrations.kt`，
    并更新 `ChatDatabaseMigrationRegistryTest`（断言 `ALL_MIGRATIONS` 连续覆盖）。
  - 遵循 CLAUDE.md：Room 版本一次只 +1。

**1b. `MomentsSettings.coverImageUri`（封面）**
- `model/Moment.kt`：`MomentsSettings` 增加 `val coverImageUri: String = ""`。
- DataStore：`AppSettingsStore` 序列化/反序列化补该字段；提供更新入口（仿现有 settings editor 风格）。
- 不入 Room（属于用户设置，非帖子数据）。

**1c. 用户发帖的图片与位置/身份**
- 复用现有单图 `MomentMedia`：用户选图保存后，建 `MomentMedia(status=SUCCEEDED, imageUri=本地路径, prompt="")`。
- `MomentsGenerationCoordinator.publishUserPost` 扩展签名：
  `publishUserPost(content, imageUri: String = "", location: String = "", userPersona: ResolvedUserPersona? = null)`。
  - `userPersona` 已支持（选择身份即传不同 maskId 解析出的 persona）。
  - 有 imageUri 时落 `MomentMedia`；写入 `post.location`。

### 组件 2：独立发布页

- 入口：`MomentsScreen` 顶栏右上角**相机图标**（替换现在的 ⟳ 刷新图标，刷新改下拉）。
- 实现方式：作为 `MomentsScreen` 内部状态页（与现有 timeline/detail 的 `Crossfade` 同构，新增 `composerMode` 状态）——避免新增导航路由、改动最小；若计划阶段认为路由更清晰可改为独立 `composable`。
- 页面元素（仿叙说，精简版）：
  - 顶栏：返回 + "发表"按钮（内容空时禁用）。
  - 想法输入框（多行，placeholder"这一刻的想法…"）。
  - 配图：一个"+"占位，点开系统相册 PhotoPicker（`ActivityResultContracts.PickVisualMedia`），选 1 张；已选显示缩略图+可删除。
  - 所在位置：行项，点开输入文本标签（可留空）。
  - 选择身份：行项，弹出用户人设面具列表（复用 `settings.userPersonaMasks` / `resolveUserPersona(maskId)`），默认当前身份。
  - 发表 → `onPublishPost(content, imageUri, location, maskId)`；发布后回到 timeline。

### 组件 3：时间线微信平铺流重排

- 顶部**封面头图区**（`item` 置顶，约 `aspectRatio` 16:10）：
  - 背景：`coverImageUri` 的图；空则默认渐变色。
  - 右下：用户头像（大圆/圆角）+ 昵称（叠在封面下沿）。
  - 点击封面 → 换封面（PhotoPicker，写入 `coverImageUri`）。
- **帖子项**（去圆角卡背景，改平铺 + 细分隔）：
  - 左：作者头像（复用 `MomentAvatar`）。
  - 右列：昵称（accent 彩色，`SemiBold`）→ 正文 → 单图（`MomentMediaContent`，限制最大宽/方图）→ 位置（muted，带定位小图标，空则不显示）→ 行尾：时间（muted）+ 更多/删除。
  - 点赞评论区：浅灰底块（`MomentsBackground`），❤ + 点赞昵称行；评论"昵称：正文"列表；与微信一致。
- 详情页 `MomentsDetailContent`、评论输入交互**保留**，仅同步配色/排版微调以贴近平铺风格。

### 组件 4：下拉刷新

- 用 Material3 `PullToRefreshBox` 包住 `MomentsTimelineContent` 的 `LazyColumn`。
- 下拉触发 `MomentsViewModel.refreshWithRandomPost()`（Spec 1 实现）；`uiState.isRefreshing` 驱动指示器。
- 无开启发帖角色时，刷新结束并提示（沿用 errorMessage）。

## 数据流

```
顶栏相机 → 发布页 → onPublishPost(content,imageUri,location,maskId)
   → VM.publishUserPost → coordinator.publishUserPost(... imageUri,location,persona)
        → upsertPost(含 location) + (可选)MomentMedia(用户图)
        → generateRepliesForPost   // 不变
下拉刷新 → VM.refreshWithRandomPost → coordinator.generateRandomAssistantPost   // Spec 1
封面区点击 → PhotoPicker → settingsEditor.setMomentsCover(uri)
```

## 错误处理

- 选图/存图失败：提示"配图保存失败"，仍可发纯文字帖。
- 封面图读取失败：回退默认渐变封面。
- Room 迁移：旧数据 `location` 默认空串，不影响既有帖子。

## 测试

- coordinator：`publishUserPost` 带 imageUri/location/maskId → 帖子含 location、媒体落库、身份正确。
- Room 迁移：`ChatDatabaseMigrationRegistryTest` 覆盖 v27→v28；迁移后旧帖 location 为空串。
- DataStore：`AppSettingsStore` 读写 `coverImageUri` round-trip。
- ViewModel：发布态、刷新态流转。
- UI（可选 androidTest）：发布页发表流程、下拉刷新触发。
- 命令：`./gradlew.bat app:testDebugUnitTest`、`./gradlew.bat app:compileDebugKotlin`。

## 验证标准

1. 新增/修改单测 + 迁移注册测试通过。
2. `app:compileDebugKotlin` 通过。
3. 人工抽查：时间线呈微信平铺流且有封面头图；封面可自选相册图；相机进发布页能配单图/填位置/选身份并发出；下拉刷新会随机让一个开启发帖的角色发一条。
