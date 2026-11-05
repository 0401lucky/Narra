# P05 执行计划

先决条件：父摘要获批，P04 已完成共享回归和独立验收。2026-10-22 已核对前置、工作区与最近提交，并对现有 P05 执行 `task.py start`；当前 `in_progress`，父任务不启动。

1. JSONL → PRD → design → implement 加载上下文，重新核对 P04 共享结果、工作区和现有基准哈希；派 `trellis-implement`，明确本批五个生产文件所有权。
2. 使用真实 Screen / Content 与确定性长规则、preset、mask、成员 / provider fixture，注册 E05 18 个用例。先证明 ModalBottomSheet / AlertDialog 真实窗口捕获及缺失窗口失败，冻结迁移前证据。
3. 迁移局部默认几何 / 语义，逐页验证 320×640 / 200%、选项状态、帮助 / 行 / Switch、48dp 与实际承载面对比；更新精确源清单。
4. 经实际入口打开独立窗口，验证末项、管理 / 确认 / 关闭、选择参数和单次回调；使用真实 SettingsViewModel + Fake 验证完整 skin 成功 / 失败、留稿 / 不离页及场景即时回调兼容。
5. 派 `trellis-check` 定向检查与修复本批，不得 record / 自接纳。展示 18 个候选及共享旧图变化；用户确认后接纳 PNG / manifest 新记录。
6. 全部已接纳截图 verify，加受影响单测、源码守卫、Lint / dev 构建与 AndroidTest 编译；明确窗口设备行为、CI / D01 / 性能未取得结果，不能用编译代替运行。
7. 主会话按父执行编排完成 spec / 版本记录、具体提交审阅与本批归档 / journal，再进入 P10。

复用 `SettingsViewModelTest`、`SettingsPersistenceCoordinatorTest`、`AppSettingsStoreTest`、`RoleplayNoBackgroundSkinSettingsTest` 与已有 P05 AndroidTest 的实际行为；新增完整 Screen / 窗口 / 保存桥接宿主测试。截图类建议 `SessionSidebarScreenshotE05Test`，逐图独立方法。

必要 ModelPickerSheet / SettingsHelpButton 共享改动必须先核定所有消费者与唯一所有者；无法稳定捕获真实窗口时，该批视觉验收未完成，先解决捕获，不伪造弹层或放松门禁。其他命令、失败与回退规则见 [父计划](../10-21-ui-token-accessibility-expansion/implement.md)。

## 当前执行记录

- [x] 读取父子规划、研究和 JSONL，核对 P04 提交与归档；分支 main、HEAD f29f0c98，暂存区为空。
- [x] 启动现有 P05，不新建任务，不启动父任务。
- [x] 冻结 92 张已接纳 PNG 哈希，保存既有 manifest 并发 diff 与起始工作区清单，见 [恢复核对](research/session-start.md)。
- [x] 产品修改前取得 E05 全部 18 张真实迁移前证据，证明5个窗口root与缺失窗口失败；before manifest哈希/尺寸已由主会话复核。
- [x] 五个局部生产文件迁移及定向行为 / 源守卫验证；2026-10-23 首轮 35 项中 32 通过，3 项宿主 / 定位问题修正后定向复验通过，独立检查再跑当前完整相关范围。
- [x] 独立 Trellis check、18 张 after 候选和前后对照；最终119项相关测试、Lint、dev构建及AndroidTest编译通过，92旧截图通过，新增候选待人工接纳。
- [x] 2026-11-05用户接纳18组视觉；新增18张E05基准逐项匹配已审阅SHA-256，旧92张不变，共110张PNG。
- [x] 最终111/111截图与1719/1719完整JVM真实执行通过；Lint/构建/AndroidTest编译复用有效产物，独立收尾检查无代码遗留，110张PNG哈希不变。
- [ ] Phase 3.4具体提交确认与归档 / journal，然后启动已批准的P10。

2026-10-23 恢复记录见 [本日核对](research/session-resume-20261023.md)。新增 `SessionSidebarAccessibilityTest`、`SessionSidebarPersistenceTest`、`SessionSidebarWindowsTest` 和 `SessionSidebarColorsTest`，以真实 Screen 验证八页、三组选项、13 皮肤与完整对象保存、实际窗口与即时场景回调。原始未打开窗口负向和源码守卫保留；行为测试不写基准。
