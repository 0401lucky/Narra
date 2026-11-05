# P05 会话设置侧栏验证记录

状态：2026-11-05已完成用户视觉接纳、最终111项截图与1719项完整JVM验证、独立检查及两笔工作提交，并归档本批。工作提交为`4d32dabf`、`0c272255`；归属v2.3，未发布，构建配置保持2.2.12-dev。

## 2026-11-05 接纳与收尾

- 用户明确回复“接纳侧栏，完成收尾后做设置首页”，批准本批18张候选的视觉接纳及按序继续P10。
- 18张E05新增基准全部从已审阅after复制，逐项SHA-256匹配；旧92张PNG不变，当前共110张PNG。接纳后清单见[accepted-png-hashes-20261105.txt](research/accepted-png-hashes-20261105.txt)。
- 最终截图111/111通过，13 suites，0失败/错误/跳过，1分5秒；真实执行比较。完整JVM1719/1719通过，228 suites，0失败/错误/跳过，1分46秒；普通JVM不启用截图比较，因此两轮证据分别保留。
- Lint、dev构建与AndroidTest编译命令成功；本轮生产源码未变化，复用原有UP-TO-DATE分析/构建/编译产物，不把它们写成重新编译或设备运行。
- 110张PNG在上述验证后SHA-256全部不变。本次最终独立检查已完成，无需修改产品或测试代码，定向`git diff --check`通过；下方旧表格属于2026-10-23候选阶段历史结果。
- 沿用已批准规划和既有P05任务，未新建任务。P10在本批收尾后启动；未推送、发包或调整版本配置。
- 用户确认具体提交清单后，完成`4d32dabf`（实现/测试/规范/证据）与`0c272255`（18张基准及P05说明）；manifest原有历史CI段落改动及其他108项既有状态未纳入。当前任务已迁入2026-11归档目录。

| 本次检查 | 结果与证据 |
| --- | --- |
| 最终截图比较 | [13份JUnit XML与摘要](research/final-screenshots-20261105/summary.json)，111/111，退出0 |
| 完整JVM集成 | [228份JUnit XML与摘要](research/final-jvm-20261105/summary.json)，1719/1719，退出0 |
| 视觉接纳 | [用户确认与18张精确哈希](research/visual-acceptance.json)，审阅页已同步为已接纳 |
| 全部已接纳PNG | [110张冻结清单](research/accepted-png-hashes-20261105.txt)，验证后未改变 |
| 最终独立检查 | [完整结论](research/check-summary-20261105.md)，无未修复代码问题，可进入提交审阅 |
| Lint / 构建 / AndroidTest编译 | [质量摘要](research/final-quality-20261105/summary.json)，复用有效产物；55 warnings / 3 hints / 0 errors |

## 已核实边界

- P04 已独立提交归档；其 1677 / 93 通过结果只属于 P04。
- 本轮基点为 main / f29f0c98；暂存区为空，既有 dirty 状态已单独冻结。见 [执行恢复核对](research/session-start.md)。
- 已接纳 PNG 共92张，冻结哈希见 [清单](research/accepted-png-hashes-before-p05.txt)。P05设计新增18张，接纳后预期110张PNG / 111项截图检查，实际以登记与真实报告核对。
- `task.py validate` 已通过：implement / check JSONL各10条真实上下文；现有P05已启动，父任务未启动。
- `adb devices` 无连接设备；本轮不启动模拟器。AndroidTest编译不代表设备运行。

## 本批证据与检查

| 检查 | 当前结果 | 证据 |
| --- | --- | --- |
| 92张旧图冻结 | 已完成 | research/accepted-png-hashes-before-p05.txt |
| 产品修改前18图 | 已冻结并复核18张哈希/尺寸 | research/before/manifest.json |
| 真实窗口root与缺失负向 | before / after均有5窗口root；缺失窗口负向1/1通过 | research/after/manifest.json、check-behavior-final/ |
| 八页320×640dp / 200%、语义 / 命中 / 回调 | 通过；8项无障碍用例覆盖实际导航、末项、标题完整、命中与独立点击 | research/check-behavior-final/ |
| 13皮肤、用户原值、保存成功 / 失败 | 通过；13皮肤逐项选择、滑块与重置范围、真实VM成功/失败返回及阅读桥接 | SessionSidebarPersistenceTest 5/5 |
| 场景 / 模型 / 成员与实际弹层 | 通过；选择/管理/dismiss、确认/取消、成功回调、单次字数更新与立绘即时回调 | SessionSidebarWindowsTest 9/9 |
| 实际配色与源码守卫 | 三主题浅深及固定动态scheme实际像素/文本配对通过，源码守卫12/12 | SessionSidebarColorsTest、UiTokenSourceGuardTest |
| 本批与共享定向回归 | 119/119通过，18 suites，0失败/错误/跳过，46秒 | research/check-behavior-final/、check-20261023-final-behavior-082614.log |
| 18张after与对照 | 18对已齐全，17对有变化；主会话已目视检查总览及关键窄屏/窗口原图 | [审阅页](review/index.html)、review/review-data.json |
| 当前完整截图矩阵 | 111项真实执行，92旧图通过；18新增缺原图及1计数守卫按预期失败，0错误/跳过 | research/after/ 中JUnit XML、after-verify-20261023.log |
| 独立Trellis check | 完整diff与导航/保存复核结束；修正活动页标题换行，范围内无未修复代码问题 | [独立检查摘要](research/check-summary-20261023.md) |
| AndroidTest编译、Lint、dev构建 | 最新代码全部通过；Lint 0 errors / 55 warnings / 3 hints（与本轮首轮相同），3分42秒；dev构建30秒；AndroidTest实际编译7秒 | research/check-20261023-final-lint-082715.log、check-20261023-assemble-083115.log、check-20261023-androidtest-compile-083159.log |
| 人工视觉接纳与最终截图verify | 待用户接纳后执行 | 不record、不降低门禁 |
| 完整JVM集成 | 按父计划在接纳后执行一次 | 不沿用P04结果 |

## 保持的产品契约

普通路由自适应面板保留94% / 66%、最大480dp及Insets意图；五个内部窗口需捕获真实root，不能只截底页面板。全局草稿成功保存才离页/进入阅读；失败留稿、成功回调为零，P05没有Settings message展示接线，不新增伪造错误界面。场景预设 / 面具 / 视觉即时回调与模型保存时机保持。系统返回按实际现状验证，不等同顶部返回图标。

当前八页顶部返回图标先回MAIN，再走原有成功保存回调；该宿主结果不代表系统返回事件已在设备实测。默认卡片/间距/选项使用现有令牌，三组选项按内容测量；大字体Switch分行放置，帮助与开关独立。活动页标题自然换行，长场景副标题保留原省略。皮肤预览仍消费用户18dp/14dp/10dp/78%等合法原值，不取整；选择皮肤、切换尾角/玻璃和重置范围沿用原行为。

## 2026-10-23 实施与复验

- 定向测试首轮35项中32通过，3项失败分别为标题重复定位、未先滚动LazyColumn及宿主缺少`LocalImagePersister`注入；修正测试宿主/定位后定向3/3通过。真实VM设置扩展函数补充了跨包导入。没有为通过测试绕开生产Screen。
- 独立检查发现页标题在200%窄屏下仍使用单行省略，已取消标题的`maxLines`/`Ellipsis`，保留副标题策略。标题断言逐行检查未省略、末尾覆盖完整文字、实际行边界位于布局内；允许1px浮点取整，避免Compose宽度取整标志的假阳性。该容差不改变截图零像素比较阈值。
- 补充已有立绘链接时选择/清除同时出现的命名和48dp实际命中回归。最终相关范围119/119通过：P05行为23 + 未打开窗口负例1 + 源码守卫12 + 共享回归83。完整JVM仍按父计划在视觉接纳后运行一次。
- 真实截图verify运行69秒、退出1，失败严格限于18个E05缺原图和计数守卫。已冻结18张after PNG、18份语义XML、5份独立窗口记录和整轮JUnit XML；前后字体、舞台、捕获目标与哈希均核对，92张旧PNG完全未改。模型Sheet候选像素不变，其余17对有变化。
- [对照页](review/index.html)提供原尺寸前后图和粉色差异图；[常规总览](review/main-overview.png)及[大字体/窗口总览](review/large-font-and-windows.png)可离线查看。候选尚未接纳，不复制到snapshots，不record，不豁免守卫。

## 仍待外部证据

设备TalkBack、系统返回/窗外触控事件、IME / 系统栏 / 真正玻璃材质、D01与物理性能、新视觉远端CI均待补。无设备不阻止宿主实现与候选整理，但不宣称这些项通过。

## 迁移前捕获结果

before最后一次真实verify退出码1：E05 18/18仅缺基准失败，计数守卫1项缺基准失败，窗口缺失负向1/1通过，共20检查、19预期失败、0错误/跳过。此时负向类仍在截图包，后续移到行为包以保持最终截图矩阵111项的口径。前期标题定位与模型操作误匹配底页的问题在产品修改前修正，最终参数包括THEME尾部、多provider长模型和窗口内操作断言。

P05实际帮助文案较短，before说明窗口的正文滚动maxValue=0，不能宣称真实长规则已滚动。E05保留实际生产说明；合成长文滚尾另用既有帮助组件行为验证，详见design实测修正。18张PNG、18份语义XML、5份独立窗口root文本、JUnit XML与日志已落盘，不属于已接纳基准。
