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

## Constraints

- **不允许升 Room 版本**：仅新增 `@Query` 删除语句，不改 schema。
- 复用既有 `MomentsRepository` -> `MomentsViewModel` -> `MomentsScreen` 链路与命名风格（对齐 `deletePost` 的写法）。
- Compose 订阅继续使用 `collectAsStateWithLifecycle()`。

## Acceptance Criteria

- [ ] `MomentDao.deleteComment(commentId)` 存在且为纯 `@Query` 删除，未改动任何实体定义。
- [ ] `MomentsRepository` 接口与实现新增 `deleteComment(commentId)`。
- [ ] `MomentsViewModel.deleteComment(commentId)` 可用；单测断言删除后 `observeTimeline()` 输出的对应帖子中不再包含该评论，且同帖其他评论与其他帖子不受影响。
- [ ] 朋友圈页长按任一评论 → 出现确认框 → 确认后该评论消失，取消后不变。
- [ ] `./gradlew.bat app:testDebugUnitTest` 与 `./gradlew.bat app:compileDebugKotlin` 通过。
- [ ] Room 版本号（`ChatDatabase.CURRENT_VERSION`）未变化。

## Out of Scope

- 评论编辑、批量删除、撤回时限。
- 权限/作者校验（本应用为单机单用户）。
