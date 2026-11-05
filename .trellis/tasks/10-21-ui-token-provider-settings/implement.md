# P11 执行计划

先决条件已满足：父摘要已批准，P10 的 opt-in 接口、窗口能力、视觉接纳与独立验收已完成并提交归档。2026-11-05 按用户明确授权启动现有 P11，当前 `in_progress`。

1. JSONL → PRD → design → implement 加载；核对共享 API、消费者、工作区与基准哈希。派 `trellis-implement`，只拥有 Provider 七个局部文件及测试。
2. 建真实列表 / 详情 / 弹层 fixture，显式隔离自动 health check 网络、用户数据和密钥；注册 E11 32 个用例，先验证真实窗口 root 与迁移前 actual。
3. 顺序迁移列表 / 模板、Config / chrome、Models / 弹层，消费既有令牌和 P10 参数；验证 320×640 / 200%、48dp、语义 / 实际颜色配对，精确扩源码守卫。
4. 复用 ProviderSettingsUiTest 的协议 / 模型卡 / 能力行为，并补整页和真实窗口；真实 VM 集成复用 SettingsViewModelTest、Draft/ProviderDraft/Load/Health/Persistence 对应 Fake / 回归模式，不重写领域实现。
5. 检查 P10 与 P05 入口、等待新 provider ID 后单次导航、保存 / 草稿失败、字段 / tab / pending fetch 归属和模型动作隔离；不改变立即 pop 返回现状。
6. 派 `trellis-check` 检查 / 定向修复；展示 32 个 after / diff 与共享旧图变化，人工确认后才接纳 PNG / manifest。
7. 全包已接纳截图 verify、所需单测、Lint / dev 构建与 AndroidTest 编译。主会话更新版本记录 / spec，审阅具体提交后独立归档 / journal，再由父任务跨批收口。

建议截图类 `ProviderSettingsScreenshotE11Test`，每图独立 `@Test`。不在图中录真实 key / key 明文，不以更改自动检测触发条件换确定性。其他验证命令、失败 / 回退与设备 / CI / 性能边界见 [父计划](../10-21-ui-token-accessibility-expansion/implement.md)。

## 执行记录（2026-11-05）

- 已核对 `main / ac22bb271007c814b6ef49b8499858acc815661c`、空暂存区及既有历史改动；manifest 旧 CI 段落差异另行保存，不纳入本批。
- `task.py validate` 通过，随后 `task.py start .trellis/tasks/10-21-ui-token-provider-settings` 成功；未创建重复任务，未启动父任务。
- 当前126张已接纳PNG逐项匹配P10最终清单，已冻结于 [accepted-png-hashes-before-p11.txt](research/accepted-png-hashes-before-p11.txt)；清单SHA-256为 `8a63c66d92c94dcd9f19fbcf38980a5bd5f9df436b6b9eb9198dbc1850b7a7fc`。
- 实现代理独占七个Provider局部文件、对应测试/截图登记和Gradle；先取得完整32张before并核对真实窗口与滚尾前置，再修改产品。独立检查在实现交还后串行进行。
- 本批结果与P10前置结果分别记录；完整截图和JVM按计划在人工接纳后执行，实际设备/新视觉CI/物理性能仍待补。
- 已取得完整32张before、12个真实独立窗口；33项仅缺基准失败，0 errors/skipped，退出1，37秒实际执行。主会话核对32张哈希、六张窄屏的实际滚动值=max与未裁切/touch双边界，并目视检查重点原图；[检查点](research/before-preflight-20261105.json)已通过，进入产品实现。
- 首轮只取得30图，能力弹窗两项因入口被浮动操作遮挡未真正打开而被root守卫拒绝；该轮保留，最终在产品修改前修正捕获步骤并完整复捕，未使用底页代替弹窗。
- 同一实现所有者追加唯一必要共享扩展：`NarraAlertDialog`可选shape，旧默认、布局和关闭语义保留；原源码在修改前补冻，P11显式消费large并验证旧调用兼容。SettingsComponents不追加修改。
- 首轮行为37/37通过后，补充颜色/真实保存/共享检查共48项，其中43项通过，5项复现fixed dynamic-dark的普通文字动作或焦点标签对比不足。局部改用实际onSurface；同一共享所有者追加`NarraAlertDialog.actionContentColor`可选参数，默认仍为primary，破坏性确认优先error。原before不变，补默认/显式颜色回归后继续复验。
- 实现与最终质量已完成，当前独立trellis-check已接手。最新P11/守卫52/52通过；144个受影响用例按实际分轮结果全部消除失败。最终质量`quality-verified/`为0 errors/55旧warnings/3 hints，无新增，dev/AndroidTest实际打包编译通过。32组前后/差异就绪；159项真实截图中126旧图通过，33项仅缺新基准。检查代理独占Gradle，不接纳或提交，主会话等待独立结果后交用户视觉审阅。
- 独立检查已完成实质审查与原始证据审计，无未修复代码/测试问题，未重复已通过且源码未变的Gradle检查，执行权已交还主会话。已向用户展示具体32组前后/差异并发起视觉接纳确认；尚未收到确认时保持126张基准、in_progress和PA4未完成，不复制PNG或提前提交。
- 用户明确回复“接纳这32组效果（推荐）”。已按审阅after精确新增32张E11，原126张未变，共158张，接纳清单SHA为`6178162c6955c476905de98897e0ffdc81ea8726c2808ae6882844ee58d31c49`；未record。检查代理现串行执行接纳后完整截图与无selector完整JVM，报告逐轮保存；具体工作提交清单等待质量通过后单独审阅。
- 接纳后首轮159截图中155通过，4项差异仅在Models底部Tab水波纹区域，页面/FAB其余像素一致；保留旧158基准与失败actual/compare，检查代理修复捕获稳定性。若修正后图不同，按原始提交隔离补before并只审阅变化图，不以after充before、不record、不改产品动画或阈值。完整JVM和提交仍待最终截图门禁收口。
- 稳定捕获已通过两轮32图SHA一致，用户明确接纳5张修正，其他153张不变。最终159/159截图、1826/1826完整JVM和必要Lint通过，无新增警告；源码/158图/保护文件与来源一致，隔离目录已清理。现已回到Phase3.4准备具体工作提交清单，获批后先两笔工作提交，再独立归档/journal和父任务总验收。
