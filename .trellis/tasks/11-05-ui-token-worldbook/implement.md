# P06 执行计划

2026-11-06用户在最终摘要后回复“ok”，已批准本计划并启动现有P06，当前in_progress。先完成隔离探针与完整before，再进入产品修改；新视觉与具体提交分别审阅。完整入口/契约见三份研究，截图与Fake原则见[capture-and-shared-contracts](research/capture-and-shared-contracts.md)。

## 当前进度

- [x] 捕获检查点：11项设施探针通过，63张真实before完整冻结。
- [x] 续接复核：原始JUnit、窗口与语义、图片/源码哈希一致；主会话已放行产品修改。见[续接记录](research/continuation-20261106.md)。
- [x] 共享接入、列表/详情、编辑与资料库UI及行为测试。
- [x] 全部after与重复after、独立检查、Lint/dev/AndroidTest编译；最终使用`*-corrected`三阶段，三张预览同策略隔离补before，其余60张保持，详见[验证记录](verification.md)。
- [x] 用户回复“可以”，63张新视觉按审阅SHA接纳，原158张不变，共221张PNG。
- [x] 接纳后222/222完整截图、1973/1973无selector JVM通过；221 PNG及14+9来源哈希不变。
- [ ] 具体提交审阅与工作提交、归档和journal。

## 1. 审阅后启动与所有权

1. 核对最新HEAD、暂存区、已有历史状态、task.json和已接纳PNG，保留无关修改。仅启动本任务，不重开旧父任务，不创建重复任务。
2. 派trellis-implement，首行为`Active task: .trellis/tasks/11-05-ui-token-worldbook`；已派发代理直接实现，不递归派同类代理。它独占全部目标UI、必要SettingsComponents参数、Keyword UI、E06登记/宿主及SourceGuard。
3. 主会话负责版本/spec/任务资料/审阅与接纳；独立trellis-check后接管自修。Gradle始终只有一个执行者，先保存本轮报告再运行下一轮。

本次续接在世界书与共享接口已落盘后并行补测：检查代理只拥有新增的世界书行为/无障碍/颜色及共享兼容测试文件，实现代理继续统一拥有生产UI、截图源、SourceGuard、资料库测试及全部Gradle执行。检查代理此时不改产品、不运行Gradle；整合后仍需完成独立全范围检查并接管必要自修。

派发时必须附上[上下文读取索引](research/context-loading.md)的要求：原生注入优先，确认三份规划正文完整；出现未内联/截断提示时逐文件补读，无注入则执行角色fallback。实现代理在每个检查点前读取索引中的对应完整研究，检查代理独立重读全部研究；不能仅凭hook标记认定已读完。两份JSONL保留核心规范、该索引与捕获契约，避免完整研究挤掉设计和执行计划，不修改注入上限或丢弃原研究。

## 2. 捕获检查点（产品未改）

- 冻结当时全部已接纳PNG与目标源哈希；当前规划快照为158张，但开工时重新核对，前序结果不算P06已通过。
- 建真实Screen固定状态及Fake，新增WorldBookScreenshotE06Test 47个、ContextTransferScreenshotE06Test 16个独立用例与E06=63登记；使用规划矩阵稳定ID。
- 先验证一个普通页、一个Dialog、一个Popup与数据页ActivityResult/Resolver接缝：不启动外部Activity、不读真实文件或用户库，root缺失会失败。
- 固定时间/locale/时区并恢复；同名文字限定真实root，末项用ID/section和真实滚动值/max、unclipped/touch bounds；捕获只进入稳定状态，真实触控另测。
- 用verify缺图actual取得全部63张before、18个独立窗口及源/参数/日志/XML/哈希；64项仅新增图+计数守卫缺图可接受，真实行为/窗口失败须先解决，不能拍底页或改产品凑before。

## 3. 实现检查点

1. 共享接入：复用已有TopBar/Group/Divider/ListRow/NarraAlert接口；只补PageIntro/SectionHeader/Help/StatusPill/AnimatedSettingButton/HintCard实际需要的兼容参数。原默认/转发/两个按钮分支与旧截图回归先稳定。
2. 列表/详情：显式令牌、长书名/筛选/管理、三类空态、帮助/删除和真实命中区。保持分组/过滤/排序、rename/删除后返回、消息过滤；不改WorldBookListStyle纯逻辑或未调用书脊函数。
3. 编辑：五折叠段、三关键词输入、11帮助、两Popup、正文Sheet和条目删除。保持完整payload/身份/extras、数字/作用域/关键词draft、主保存与删除立即back、全屏只回写草稿和试命中范围。
4. 数据页：5资源组/5只读计数、preview/notice/冲突、来源Dialog与6帮助；只读行保持无点击，文字可读，200%按钮可换行/到达。保留section、三launcher、取消、现有busy范围、预览/确认失败和格式/媒体/权限边界。
5. 每段补真实UI/配色/回调及相邻触控测试；必要源守卫精确扩围。实际风险复现才修本批UI，不把旧领域缺口变成隐式重构。

## 4. 整合与审阅

- 运行受影响JVM、SourceGuard、共享默认/新参数、时间/窗口负向和所有入口回调；不能仅写字段常量镜像测试。
- 同参数取得63张after，并重复全部after，逐张SHA一致后构建before/after/diff审阅页；若捕获前置改变，用原始提交隔离补before并记录覆盖/来源，不动主工作区还原产品。
- 派独立trellis-check全范围审查与定向自修；确认没有真实网络/用户数据、没有误改保存/合并/权限/协议、没有扫描豁免。未接纳期旧158图应通过，新63图及计数守卫继续如实缺图失败。
- 先完IDE格式化、Lint/dev构建和AndroidTest编译，再交用户视觉审阅。新PNG只按已审阅SHA接纳；若变化仅子集，只重新确认变更图，未变图沿用批准。
- 接纳后运行全部截图和无selector完整JVM，分别保存退出码、测试数、实际执行任务与前后基准哈希；不把UP-TO-DATE、编译或普通JVM当像素/设备运行。
- 主会话同步版本记录/必要spec，展示具体工作提交清单另行确认；先工作提交，再精确归档/journal。既有111项历史状态、manifest旧CI差异、APK/缓存/忽略日志不混入提交；不推送/发包。

## 5. 计划命令

下列为获批实施命令，validate/start已执行，产品验证结果按实际运行另记。selector最终按实际新增测试名收敛；既有基准的规则/阈值不改。

```powershell
python .trellis/scripts/task.py validate .trellis/tasks/11-05-ui-token-worldbook
# 仅在用户批准最终规划摘要后：
python .trellis/scripts/task.py start .trellis/tasks/11-05-ui-token-worldbook

.\gradlew.bat :app:testDebugUnitTest --tests 'com.example.myapplication.ui.screen.settings.worldbook.*' --tests 'com.example.myapplication.ui.screen.settings.ContextTransfer*Test' --tests 'com.example.myapplication.ui.component.worldbook.*' --tests 'com.example.myapplication.ui.theme.UiTokenSourceGuardTest' --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests 'com.example.myapplication.viewmodel.WorldBookViewModelTest' --tests 'com.example.myapplication.viewmodel.ContextTransferViewModelTest' --tests 'com.example.myapplication.data.repository.context.*Test' --tests 'com.example.myapplication.context.WorldBookMatcherTest' --console=plain
.\gradlew.bat :app:lintDebug :app:assembleDebug :app:compileDebugAndroidTestKotlin --console=plain
.\gradlew.bat :app:verifyRoborazziDebug --tests 'com.example.myapplication.ui.screenshot.*' --console=plain
.\gradlew.bat :app:testDebugUnitTest --console=plain
```

导入/导出涉及的平台选择器、真实IME/返回/系统栏、物理性能与新视觉CI保留独立待补记录。缺设备不启动不相关服务或用编译假报通过。
