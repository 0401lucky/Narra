# 朋友圈"分享自己生活"优化（混合合并生成）设计

- 日期：2026-06-20
- 分支：feat/discover-ui-redesign
- 范围：角色自动发布的朋友圈内容质量 + 发帖时的人气填充
- 状态：设计已确认，待写实现计划

## 背景与问题

参考"叙说"App 的朋友圈，角色发的内容更像**真人在分享自己的生活**；而我们当前角色发的朋友圈"有点像在和用户对话"。

定位到根因在 `PhoneContentPromptService.generateMomentPost`（`app/src/main/java/com/example/myapplication/data/repository/ai/PhoneContentPromptService.kt:307`）：

| 维度 | 叙说（像真人生活） | 我们现状（像在对话） |
|---|---|---|
| 内容指向 | "不必每条都与用户直接相关" | "可以轻微暗示和用户的关系" ← 把模型往"对用户说话"带 |
| 画面要求 | "必须具体、有画面、有细节，情绪真实，避免空话套话" | 只说"短、口语、带生活碎片" |
| 时间语境 | 注入当前时间，引导作息/节日 | 无 |
| 发帖人气 | 发帖即带虚构点赞+评论，是"完整一条" | 新帖点赞为空，评论靠后续真实角色补 |

数据模型现状（`app/src/main/java/com/example/myapplication/model/Moment.kt`）：
- `MomentPost` 已有 `likedByNames`，但角色自动发帖时为空。
- 评论由 `MomentsGenerationCoordinator.generateRepliesForPost` 让**真实配置的角色 + NPC** 生成，带头像、跨帖身份延续、防 OOC/防串戏。这套**比叙说更强**，必须保留。

## 目标

1. 重写发帖 prompt，让内容**聚焦角色自己的生活**、有具体画面/细节/真实情绪，去掉"对用户说话"的引导。
2. 发帖时**一次生成**完整一条：正文 + 虚构点赞 + 0~2 条虚构 NPC 种子评论，让新帖立刻"有人气、有生活感"。
3. **完整保留**真实角色评论系统（`generateRepliesForPost` 及其防 OOC/防串戏/头像/跨帖逻辑）不变。

## 非目标（YAGNI）

- 不改动真实角色评论、防 OOC、防串戏、NPC 名单逻辑。
- 不改朋友圈 UI，不新增设置项。
- 不动"查手机玩法"里的 `social_posts` 生成（那是 `generatePhoneSnapshotSections`，与本次无关）。
- 不合并/替换真实角色评论为纯虚构评论。

## 设计

### 组件 1：重写 `generateMomentPost` 的 prompt 并扩展输出

文件：`PhoneContentPromptService.kt`（`generateMomentPost`，行 307-368）。

**新 prompt 关键点：**
- 框定身份："你就是『$assistantName』本人，在发自己的朋友圈，记录/分享自己生活的一部分。"
- 聚焦自己：内容来自角色自己的日常——工作、饮食、独处、爱好、见闻、情绪等；**不必和用户相关，更不要像在对用户说话或解释设定**。
- 强制质感：必须**具体、有画面、有细节、情绪真实**；禁止空话套话、鸡汤、广告腔、旁白式叙述。
- 贴合人设：从注入的人设（职业/性格/爱好/生活环境）挖素材，让内容像"这个具体的人"会发的。
- 时间语境：注入新增的 `timeContext` 字符串（白天/夜晚/工作日/周末/节日/作息），自然融入，**不机械复述完整日期或时刻**。
- 保留约束：不要 Markdown、不要标题、不要 hashtag；`image_prompt` 可选配图。

**输出 JSON 从 `{content, image_prompt}` 扩展为：**
```json
{
  "content": "...",
  "image_prompt": "...",
  "likes": ["虚构昵称×2-5"],
  "comments": [ { "user": "虚构昵称", "text": "口语化≤15字" } ]
}
```
- `likes`：2-5 个**虚构好友风格昵称**。
- `comments`：0-2 条虚构 NPC **种子评论**，`user` 为昵称、`text` 口语化 ≤15 字。
- 昵称禁止占位名，复用已有 `String.isPlaceholderMomentName()` 校验过滤。
- 解析需容错：缺字段时 `likes`/`comments` 视为空，不影响正文落库（沿用现有 `runCatching` 容错风格）。

**签名变更：**
- `generateMomentPost` 新增参数 `timeContext: String`。
- 同步更新接口 `AiPromptExtrasService.generateMomentPost`（声明在 `AiPromptExtrasService.kt:213`，override 在 `:627`）与默认实现。

### 组件 2：扩展 `MomentPostDraft`

文件：`app/src/main/java/com/example/myapplication/model/Moment.kt`。

```kotlin
data class MomentPostDraft(
    val content: String = "",
    val imagePrompt: String = "",
    val likedBy: List<String> = emptyList(),
    val seedComments: List<MomentCommentDraft> = emptyList(),
)
```
- 种子评论复用现有 `MomentCommentDraft`，`authorType = MomentAuthorType.NPC`，`authorId = "npc:<昵称>"`。

### 组件 3：发帖落地（`MomentsGenerationCoordinator.publishAssistantPost`）

文件：`app/src/main/java/com/example/myapplication/data/repository/moments/MomentsGenerationCoordinator.kt`（行 193-254）。

- 构造 `timeContext`：在 coordinator 用 `nowProvider()` 生成中文时间语境字符串（白天/夜晚、工作日/周末、节日/作息提示），传入 `generateMomentPost`。
- 发帖时：
  - 用 `draft.likedBy`（经 `sanitizeMomentDisplayName` / 占位名过滤 / 去重 / 上限，建议上限 6）写入 `MomentPost.likedByNames`。
  - 把 `draft.seedComments` 落为 `MomentComment`（`authorType=NPC`），与正文同批 upsert。
- 保留其后的 `generateRepliesForPost(isUserCommentTrigger=false)`：真实角色照常评论。
- **去重**：种子评论/点赞的 NPC 昵称要并入 `buildMomentNpcCandidates` 的 `usedNames`，避免后续真实评论环节撞名。

### 组件 4：时间语境构造（新私有 helper）

在 `MomentsGenerationCoordinator` 内新增 `buildMomentTimeContext(now: Long): String`：
- 输出例："现在是周六凌晨，深夜时段"之类的简短语境，仅供模型把握作息/节日氛围。
- 用标准 `java.util.Calendar`/`LocalDateTime`，遵循项目既有时间处理风格（应用代码内可用系统时间）。

## 数据流

```
generateDueAssistantPosts
  └─ publishAssistantPost(settings, assistant)
       ├─ buildMomentTimeContext(now)            // 新
       ├─ generateMomentPost(..., timeContext)   // 改：聚焦自己 + 输出 likes/comments
       │     → MomentPostDraft(content, imagePrompt, likedBy, seedComments)  // 改
       ├─ upsertPost(post.copy(likedByNames=draft.likedBy净化))              // 改
       ├─ addComments(种子NPC评论)                                           // 新
       └─ generateRepliesForPost(isUserCommentTrigger=false)                // 不变：真实角色评论
```

## 错误处理

- prompt 输出解析失败时：正文回退到原 `content` 文本清洗逻辑；`likedBy`/`seedComments` 回退为空列表，发帖流程不中断。
- 占位名/空名一律过滤；去重后再落库。
- `generateRepliesForPost` 失败不影响已发布的帖子与种子内容（沿用现状）。

## 测试（TDD）

- `AiPromptExtrasServiceTest`（MockWebServer）：
  - 新 prompt 返回含 `likes`/`comments` 的 JSON → 正确解析进 `MomentPostDraft`。
  - 缺 `likes`/`comments` 字段 → 回退空列表，正文正常。
  - 占位名昵称被过滤。
- coordinator 测试（Fake service）：
  - 发帖后 `MomentPost.likedByNames` = 净化后的 `draft.likedBy`。
  - 种子评论以 `authorType=NPC` 落库。
  - 真实角色评论流程仍被调用、不被破坏；种子昵称不与后续 NPC 撞名。
- 全部命令：`./gradlew.bat app:testDebugUnitTest`、`./gradlew.bat app:compileDebugKotlin`。

## 验证标准

1. 新增/修改的单测全部通过。
2. `app:compileDebugKotlin` 通过。
3. 人工抽查：角色自动发的朋友圈正文聚焦角色自己生活、有画面细节、无"对用户说话"腔；新帖带 2-5 点赞与 0-2 条种子评论；真实角色评论仍正常出现。
