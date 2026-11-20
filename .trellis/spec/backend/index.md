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
- [默认 UI 令牌与个性化](./ui-token-contract.md)：统一形状 / 间距、主题配对、用户几何优先级、图片缓存与截图接纳。
- [角色扮演主题与背景气泡](./roleplay-theme-settings.md)：全局线上材质开关、旧 JSON 兼容及消息视觉状态。
- [系统悬浮翻译 UI](./floating-translation-ui-guidelines.md)：原生窗口、共享主题、授权编号与取消边界。
- [网络与协议](./network-guidelines.md)：Provider、Retrofit、流式响应与 URL 安全。
- [dev 云端发布与更新](./dev-release-guidelines.md)：Actions、原签名、桶级 S3、清单门禁与 Pages 发布重试。
- [持久化规范](./database-guidelines.md)：Room、DataStore、Keystore 与迁移。
- [陪伴场次与可靠回合](./companion-foundation-guidelines.md)：场次/请求、产物补存、完成证据、CAS、来源资格与旧路径兼容。
- [陪伴入口与授权上下文](./companion-entry-context-guidelines.md)：三开场、持久选择/返回、逐请求历史/记忆、最后发送校验和时区快照。
- [陪伴正文、表现与观测](./companion-presentation-guidelines.md)：raw保真、历史配置快照、版本化投影、文风/人称与设置CAS。
- [陪伴演出、素材与连续阅读](./companion-stage-reading-guidelines.md)：本地播放器、素材引用/CAS/取消、独立阅读/keyset/锚点与声音选段。
- [陪伴语音、翻译与独立收藏](./companion-voice-guidelines.md)：独立开关/冻结请求键、唯一声音owner、文件/Room回执、收藏及有限音频缓存。
- [陪伴历史、原文与资料恢复](./companion-history-transfer-guidelines.md)：完整授权查询/原文资格、批次CAS、TXT/SAF、公开白名单、私人v3及分阶段恢复。
- [RP 记忆空间与持久任务](./memory-system-guidelines.md)：运行/身份、来源版本、事务、恢复、长期认知、时效状态与档案生命周期。
- [记忆档案管理与处理控制](./memory-management-guidelines.md)：真实管理入口、历史回执、暂停/继续、CAS、一次激活与上层重开。
- [记忆资料导入导出](./memory-transfer-guidelines.md)：完整图、目标/出处、幂等与外层事务、删除闭包和向量授权。
- [错误处理](./error-handling.md)：异常传播、协程取消、UI 反馈与脱敏。
- [合规门禁](./compliance-guidelines.md)：18+ 条款确认、DataStore 契约和后台 AI 守卫。
- [日志规范](./logging-guidelines.md)：`AppLogger`、日志级别和敏感信息。
- [性能基准与可观测性](./performance-benchmark-guidelines.md)：baseline-only 会话、fixture、
  ready / action、Profile、系统栏安全点击和结果诚实性。
- [质量规范](./quality-guidelines.md)：编码、测试、Lint、构建与审查门禁。

## Pre-Development Checklist（开发前检查清单）

所有任务至少阅读：

- `directory-structure.md`
- `quality-guidelines.md`
- `../guides/index.md`

按改动范围继续阅读：

- Compose、导航、ViewModel：`ui-state-guidelines.md`、`error-handling.md`
- 默认令牌、皮肤配色、玻璃缓存或试点无障碍：`ui-token-contract.md`、`roleplay-theme-settings.md`
- 系统悬浮翻译窗口、服务或截屏授权：`floating-translation-ui-guidelines.md`、`error-handling.md`
- Room、DataStore、导入导出：`database-guidelines.md`、`error-handling.md`
- RP 记忆、剧情运行、提取/召回或管理：`memory-system-guidelines.md`、
  `database-guidelines.md`、`error-handling.md`
- 单角色陪伴场次、主回复恢复或 linked 消息：`companion-foundation-guidelines.md`
- 陪伴开场/导航、上下文、时区或请求表现扩展：`companion-entry-context-guidelines.md`
- 陪伴正文/观测、表现配置、解析投影或后续演出/声音消费：`companion-presentation-guidelines.md`
- 陪伴播放器、素材、跨场阅读或声音选段接线：`companion-stage-reading-guidelines.md`
- 陪伴朗读、翻译/MiMo参数、声音发布恢复、收藏或缓存：`companion-voice-guidelines.md`
- 陪伴历史/原文改删、TXT/SAF、装扮与私人资料恢复：`companion-history-transfer-guidelines.md`
- 记忆管理、暂停/继续、用途或生命周期：`memory-management-guidelines.md`
- 记忆资料/目标映射、恢复或导入向量：`memory-transfer-guidelines.md`
- Provider、AI、搜索、更新、TTS：`network-guidelines.md`、
  `logging-guidelines.md`、`error-handling.md`
- dev 发版工作流、签名、R2、Pages 或发布工具：`dev-release-guidelines.md`、
  `network-guidelines.md`、`logging-guidelines.md`
- 首次启动、年龄门槛、条款确认或合规状态跨 UI/存储/后台任务传播：
  `compliance-guidelines.md`、`database-guidelines.md`、`error-handling.md`
- Macrobenchmark、Baseline Profile、benchmark fixture / ready、Trace 或性能报告：
  `performance-benchmark-guidelines.md`、`quality-guidelines.md`
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
