# 朋友圈删除单条回复

## Goal

朋友圈时间线上的每条评论（回复）都可以被单独删除，用于清掉不满意的 AI 生成回复或误发的用户回复。

## Background

`MomentDao` 目前只有 `upsertComment` / `upsertComments`，没有任何评论删除入口；只能整条删帖（`deletePost`）或清空全部（`clearMoments`）。
`moment_comments` 表已有字符串主键 `id`，且对 `moment_posts` 建了 `CASCADE` 外键，删除单条评论不涉及表结构变更。

## Requirements

1. 数据层提供按 `commentId` 删除单条评论的能力。
2. `MomentsScreen` 评论行支持长按触发删除，长按时给出触感反馈。
3. 删除前必须二次确认（对话框标题「删除这条回复？」，确认按钮为危险色）。
4. 删除范围不区分作者：用户评论与 AI 评论都允许删除。
5. 删除后时间线自动刷新（依赖既有 `observeTimeline()` Flow，不手工改本地 state）。
6. 失败时通过既有 `errorMessage` 通道提示，文案为简体中文。
7. **时间线卡片上的评论预览行也支持长按删除**（2026-09-03 追加）：
   - `MomentEngagementBlock` 中 `post.comments.take(4)` 渲染的每一行都可单独长按删除，
     复用与详情页相同的触感反馈与确认框；
   - 预览行原本依赖父 `Row` 的点击进入帖子详情，加长按手势会抢掉该点击，
     因此必须给预览行显式接回「打开帖子」的单击行为，交互不得退化；
   - 第 5 条及之后的评论不在预览中渲染，仍只能进详情页删除，这是可接受的。

## Constraints

- **不允许升 Room 版本**：仅新增 `@Query` 删除语句，不改 schema。
- 复用既有 `MomentsRepository` -> `MomentsViewModel` -> `MomentsScreen` 链路与命名风格（对齐 `deletePost` 的写法）。
- Compose 订阅继续使用 `collectAsStateWithLifecycle()`。

## Acceptance Criteria

- [ ] `MomentDao.deleteComment(commentId)` 存在且为纯 `@Query` 删除，未改动任何实体定义。
- [ ] `MomentsRepository` 接口与实现新增 `deleteComment(commentId)`。
- [ ] `MomentsViewModel.deleteComment(commentId)` 可用；单测断言删除后 `observeTimeline()` 输出的对应帖子中不再包含该评论，且同帖其他评论与其他帖子不受影响。
- [ ] 朋友圈页长按任一评论 → 出现确认框 → 确认后该评论消失，取消后不变。
- [ ] 时间线卡片的评论预览行长按可删除；**单击该预览行仍能打开帖子详情**（手势不得互相抢占）。
- [ ] `./gradlew.bat app:testDebugUnitTest` 与 `./gradlew.bat app:compileDebugKotlin` 通过。
- [ ] Room 版本号（`ChatDatabase.CURRENT_VERSION`）未变化。

## Out of Scope

- 评论编辑、批量删除、撤回时限。
- 权限/作者校验（本应用为单机单用户）。
