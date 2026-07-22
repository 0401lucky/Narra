# Android 应用开发规范

> 本目录由 Trellis 初始化为 `backend` 层。Narra 实际是 Android 应用，
> 为兼容当前 Trellis 层配置暂时保留目录名，正文以 Android/Kotlin 代码为准。

## 项目概览

Narra 是双模块 Android 工程：`:app` 承载业务，`:baselineprofile` 承载
启动基线与宏基准。主技术栈为 Kotlin、Jetpack Compose、Lifecycle
ViewModel、StateFlow、Room、DataStore、Retrofit、OkHttp、Gson 与 KSP。

主依赖方向为：

```text
Compose Screen
    ↓ 状态与 onXxx 回调
Navigation / ViewModel
    ↓ 领域用例
Repository / Coordinator / Assembler
    ↓
Store / Room DAO / Retrofit / Android system adapter
```

依赖装配集中在 `di/AppGraph.kt`，不要在 Activity、Screen 或 ViewModel 中
直接创建 Repository、Room 数据库或网络客户端。

## 规范索引

- [目录与架构](./directory-structure.md)：模块、包职责、依赖边界与命名。
- [UI 与状态管理](./ui-state-guidelines.md)：Compose、导航、ViewModel、StateFlow。
- [网络与协议](./network-guidelines.md)：Provider、Retrofit、流式响应与 URL 安全。
- [持久化规范](./database-guidelines.md)：Room、DataStore、Keystore 与迁移。
- [错误处理](./error-handling.md)：异常传播、协程取消、UI 反馈与脱敏。
- [合规门禁](./compliance-guidelines.md)：18+ 条款确认、DataStore 契约和后台 AI 守卫。
- [日志规范](./logging-guidelines.md)：`AppLogger`、日志级别和敏感信息。
- [质量规范](./quality-guidelines.md)：编码、测试、Lint、构建与审查门禁。

## Pre-Development Checklist（开发前检查清单）

所有任务至少阅读：

- `directory-structure.md`
- `quality-guidelines.md`
- `../guides/index.md`

按改动范围继续阅读：

- Compose、导航、ViewModel：`ui-state-guidelines.md`、`error-handling.md`
- Room、DataStore、导入导出：`database-guidelines.md`、`error-handling.md`
- Provider、AI、搜索、更新、TTS：`network-guidelines.md`、
  `logging-guidelines.md`、`error-handling.md`
- 首次启动、年龄门槛、条款确认或合规状态跨 UI/存储/后台任务传播：
  `compliance-guidelines.md`、`database-guidelines.md`、`error-handling.md`
- 跨越 UI、状态、仓储和存储的功能：
  `../guides/cross-layer-thinking-guide.md`
- 新增 Helper、Support、Coordinator 或常量：
  `../guides/code-reuse-thinking-guide.md`

## Quality Check（质量检查）

- 确认改动遵守对应专题规范，并重新阅读 `quality-guidelines.md`。
- 代码改动运行匹配范围的单测、`lintDebug` 和 `assembleDebug`；设备、Room
  或 baseline 改动补充对应设备/性能检查。
- 规范改动扫描模板占位、无效链接、索引遗漏和不存在的源码引用。
- 检查 `git status`，只交付任务范围文件，不覆盖工作区已有改动。
- 超时或因设备/外部条件未执行的验证必须明确报告，不能写成通过。

## 事实来源

规范优先级如下：

1. 当前源码与测试。
2. `AGENTS.md`、`README.md`、`CLAUDE.md` 和 `docs/` 中的项目约定。
3. `app/build.gradle`、`gradle/libs.versions.toml`、CI 配置。

文档、代码注释和用户可见文案默认使用简体中文。若规范与源码出现偏差，
先核对近期改动，再同步更新规范，禁止用通用 Android 建议覆盖项目事实。
