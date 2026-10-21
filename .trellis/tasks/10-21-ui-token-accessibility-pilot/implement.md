# v2.3 统一设计令牌与无障碍试点：执行计划

## 当前阶段与启动条件

2026-10-21：主会话已完成 PRD 收敛、路径与 JSONL 校验并展示最终摘要；用户后续回复“继续”，批准按此实施，现执行 `task.py start`。实现 / 检查子代理均使用当前任务路径，不使用已归档旧草案作为实施目标。

本任务采用串行批次：共享契约先落地，再迁移调用方、做集成检查。三处页面共用主题和皮肤解析，不拆成独立并行任务，以避免多处同时修改 shared owner。

## Wave 0：冻结现状

- [x] 读取当前 `git status`，记录工作区原有文件，产品改动仅落在下表范围；不处理旧任务搬移或其他未跟踪目录。（`research/worktree-before.json`）
- [x] 运行 `get_context.py --mode packages`，加载 JSONL 规范和当前 PRD / design / implement；核对所有迁移文件的调用方。（单仓 backend；实现代理已加载关联规范）
- [x] 校验旧 78 张 PNG 哈希，运行现有截图 verify，记录真实测试数、退出码、宿主和基准来源；失败先区分环境 / 既有问题，不能先 record。（真实 `--rerun-tasks`：79 项测试全通过，哈希 78/78 不变）
- [x] 执行时检查可用 adb 设备，区分真实物理设备、模拟器与无设备；历史无设备状态不能直接当作本轮事实。（2026-10-21 重新探测，无连接设备）
- [x] 建立迁移与例外清单，记录 Material 隐式形状、共享皮肤解析、设置预览和线下气泡的实际调用方。（`research/source-guard.md`、`research/check-report.md`、`research/expansion-list.md`）

## Wave 1：共享令牌与颜色契约

- [x] `UiTokens.kt` 增加六档 spacing / 四档 shape / 触控及用途定义；`ChatAppTheme.kt` 安装共享 Material shapes。保留非试点旧别名，不通过修改旧尺寸批量改版。
- [x] 更新现有皮肤 / `ImmersiveRoleplayColors` 解析：Narra 默认使用 Material 配色；12 套品牌皮肤集中为精确例外，补成对的内容色和对比校验。
- [x] 确认所有 remember / 调色缓存包含实际主题输入；保留用户保存几何及玻璃谓词。（缓存 key 为 URI / 图片 identity / generation / size / 完整 ColorScheme / highContrast，无强图片引用）
- [x] 在主题解析 / 配色测试中覆盖主题切换、不同角色、透明合成面和低对比样本；复用现有 settings / glass policy 回归，不为常量 getter 编写镜像测试。（新颜色 4 项、主题 / 缓存 / 玻璃 5 项已定向通过；最终集成检查另记）

## Wave 2：P01 / P02 实际迁移

- [x] `ImmersivePhoneShell.kt` 的首页标题、底栏、会话行及本批可达主要操作迁移默认 shape / spacing / 语义色，修复大字体底栏裁切。
- [x] `AssistantListScreen.kt` 的搜索框、角色卡、空 / 无搜索结果与更多菜单迁移；修改 `SettingsComponents.kt` 时明确最小共享影响。
- [x] 检查语义合并、选中状态、48dp 命中和回调；保留角色选择、编辑 / 删除确认等产品行为。
- [x] 执行定向颜色 / UI 检查和 H01 / H02 / H04 对照，将预期差异登记后继续集成，旧基准不改。（候选待人工接纳）

## Wave 3：P03 实际迁移

- [x] 线上头部 / 输入区、消息气泡、回顶部 / 底部按钮使用统一默认令牌与语义角色；42dp 控件扩大命中。
- [x] `RoleplayNoBackgroundSkin.kt`、`RoleplayDialoguePanelParts.kt`、`RoleplayDialogueBubbles.kt`、`RoleplayMessageBubbles.kt`、`RoleplayOnlinePhoneBubbles.kt` 和 `RoleplayInputBarComponents.kt` 只修改该契约所需部分；不改变稳定列表 key / 自动贴底 / 流式状态机。（`RoleplayOnlinePhoneBubbles.kt` 为实际线上旁白 / 心声渲染调用方，已在实施核对中补入）
- [x] 修复 P03 200% 字体头部遮挡和输入提示溢出，验证可滚动到达真实消息和主要动作。
- [x] 验证 13 套皮肤、玻璃开关、上下文模式和 B03 用户几何边界；共享线下气泡 / 设置预览做必要回归。（宿主 / JVM 与设备用例编译，不含真机运行）
- [x] 复验 H03 / H04 / B01–B04 / C01，补真实 P03 的固定动态色 / 同组件主题切换测试；测试 fixture 跟随生产令牌。（普通测试通过；截图像素变化待人工接纳）

## Wave 4：违规门禁、视觉候选与检查

- [x] 新增 JUnit 试点源码守卫及正反例，绑定迁移文件与精确例外；证明新硬编码会失败、合法用户几何 / 品牌色不会误报。（12 项定向通过，含生产绑定和陈旧 / 移位清单）
- [x] 汇总 affected case ID、expected diff、actual / compare 文件和旧基准哈希，整理本地审阅图。未经人工确认不接纳候选，也不放宽门禁。（`review/index.html`，78/78 图，无缺图）
- [x] 调用 `trellis-check` 子代理做全任务范围审查 / 修复；检查提示必须开始于 `Active task: .trellis/tasks/10-21-ui-token-accessibility-pilot`，禁止再派实现 / 检查孙代理。（最终无未修实现阻断，见 `research/check-report.md`）
- [x] 用户确认视觉候选后定向更新基准和 manifest，再运行全部截图 verify；若待确认，A6 保持未完成并报告准确的差异失败结果。（本轮用户确认，接纳后 79/79 真实通过，未 record）
- [x] 运行相关单测、AndroidTest 编译、Lint 与 dev 构建；设备可用时执行限定类 / 方法的语义交互测试。（1642 项单测及三项门禁通过；无设备，实际设备用例未运行）

## Wave 5：收口

- [x] 主会话更新 `版本功能添加/优化.md` 的 v2.3 实施状态、验证边界与扩面清单。
- [x] 评估并更新相关 `.trellis/spec/` 主题 / 无障碍 / 质量契约；没有新通用规则时记录判断。（新增 `backend/ui-token-contract.md` 并接入 index，最终审查复核实现一致性）
- [x] 输出 A1–A7 验收表，区分通过、待人工确认、待设备和未取得结果。（`verification.md`，A6 视觉接纳未完成）
- [x] 只就本次文件给出提交方案；其他原有改动保持不动，未经用户同意不提交 / 推送。（`commit-plan.md` / `.json` 已列两批具体路径；manifest 只暂存本轮新增段落，补丁 check 通过，待用户确认执行）
- [ ] 完成代码、视觉和所需检查后再归档 / journal；D01 / 性能不足保持独立待验收记录。

## 文件所有权

| 批次 / owner | 允许修改的主要文件 | 限制 |
| --- | --- | --- |
| 实现代理 Wave 1 | `ui/theme/UiTokens.kt`、`ChatAppTheme.kt`、同包皮肤语义调色文件；`RoleplayNoBackgroundSkin.kt`、`RoleplayDialoguePanelParts.kt`、`RoleplayUiTokens.kt`；必要的 `ImmersiveGlass.kt` 缓存键 | 搜索全部读取方；不重写完整玻璃渲染算法或存储模型 |
| 实现代理 Wave 2 | `ImmersivePhoneShell.kt`、`AssistantListScreen.kt`、最小范围 `SettingsComponents.kt` | 不改导航、角色持久化和试点外页面布局 |
| 实现代理 Wave 3 | `RoleplayOnlinePhoneContent.kt`、气泡 / 输入区共享组件、必要的 `NarraButton.kt` | 消息和媒体协议、发送状态、列表 identity 保持 |
| 实现代理测试 | `ui/theme/`、`ui/component/roleplay/` 相关 JVM 测试；`ui/screenshot/` fixture / 固定动态色测试；`androidTest/ui/` 试点交互；试点源码守卫 | 不能删除旧 ID、弱化守卫或自接纳基准 |
| 检查代理 | 同上代码 / 测试范围内发现的问题 | 不是独占工作区，不回滚他人修改；不修改用户视觉决定 |
| 主会话 | 当前任务 artifacts、版本记录、必要 spec、候选审阅和人工确认后的基准清单 | 审阅与提交 / 归档由主会话协调 |

候选目录参数若为必要，允许最小修改 `app/build.gradle` 的截图输出 / 注入配置，须证明默认 / CI 目录不变且输出隔离。该参数不是重录现有基准的授权。

## 验证命令与结果解释

按批次运行相关类；最后的全范围命令只在集成完成后执行一次，新的修改 / 失败再定向复验。

```powershell
python .trellis/scripts/task.py validate .trellis/tasks/10-21-ui-token-accessibility-pilot
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.ui.theme.*" --tests "com.example.myapplication.ui.component.roleplay.*" --tests "com.example.myapplication.model.RoleplayNoBackgroundSkinSettingsTest" --console=plain
.\gradlew.bat :app:compileDebugAndroidTestKotlin --console=plain
.\gradlew.bat :app:verifyRoborazziDebug --tests "com.example.myapplication.ui.screenshot.*" --console=plain
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain
git diff --check
```

语义与触控测试在设备 / 可行的现有 Robolectric 宿主上实际运行；只编译 AndroidTest 不计为交互通过。新增源码守卫的类名在实现后登记并加入定向命令，不能提前声称执行。

JVM 测试按现有规范分组并限制单次后台等待 60 秒；超时记录未取得结果。物理设备具备稳定温控、Profile 与对照构建时沿用 `:baselineprofile:runAndArchiveNarraBenchmarks` 的 S0–S3 / F1–F3 / N1 合约，结果与候选截图分别登记。没有有效前后同机数据不输出性能结论。

## 验收映射和回退点

| 验收 | 执行批次 | 关键证据 |
| --- | --- | --- |
| A1 | 1–3 | 生产调用迁移清单与精确例外 |
| A2 | 1、3 | 主题切换 / 对比度 / 真实 P03 动态色 |
| A3 | 1、3 | 设置回读 / glass policy / B01–B04 / 共享调用方 |
| A4 | 2、3 | 语义、边界、回调与窄屏 200% 布局 |
| A5 | 4 | 源码守卫正反例与扫描覆盖 |
| A6 | 0、4 | 哈希、人工确认、真实 verify 与构建测试 |
| A7 | 5 | 版本记录、验收表和扩面清单 |

共享形状、皮肤解析、大字体布局和基准接纳分别作为回退点。回退只针对本任务新增 diff；不使用 reset / clean / 全目录删除，不恢复工作区原有归档搬移。
