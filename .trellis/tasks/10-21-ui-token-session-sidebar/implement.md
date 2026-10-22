# P05 执行计划

先决条件：父摘要获批，P04 已完成共享回归和独立验收。当前 `planning`。

1. JSONL → PRD → design → implement 加载上下文，重新核对 P04 共享结果、工作区和现有基准哈希；派 `trellis-implement`，明确本批五个生产文件所有权。
2. 使用真实 Screen / Content 与确定性长规则、preset、mask、成员 / provider fixture，注册 E05 18 个用例。先证明 ModalBottomSheet / AlertDialog 真实窗口捕获及缺失窗口失败，冻结迁移前证据。
3. 迁移局部默认几何 / 语义，逐页验证 320×640 / 200%、选项状态、帮助 / 行 / Switch、48dp 与实际承载面对比；更新精确源清单。
4. 经实际入口打开独立窗口，验证末项、管理 / 确认 / 关闭、选择参数和单次回调；使用真实 SettingsViewModel + Fake 验证完整 skin 成功 / 失败、留稿 / 不离页及场景即时回调兼容。
5. 派 `trellis-check` 定向检查与修复本批，不得 record / 自接纳。展示 18 个候选及共享旧图变化；用户确认后接纳 PNG / manifest 新记录。
6. 全部已接纳截图 verify，加受影响单测、源码守卫、Lint / dev 构建与 AndroidTest 编译；明确窗口设备行为、CI / D01 / 性能未取得结果，不能用编译代替运行。
7. 主会话按父执行编排完成 spec / 版本记录、具体提交审阅与本批归档 / journal，再进入 P10。

复用 `SettingsViewModelTest`、`SettingsPersistenceCoordinatorTest`、`AppSettingsStoreTest`、`RoleplayNoBackgroundSkinSettingsTest` 与已有 P05 AndroidTest 的实际行为；新增完整 Screen / 窗口 / 保存桥接宿主测试。截图类建议 `SessionSidebarScreenshotE05Test`，逐图独立方法。

必要 ModelPickerSheet / SettingsHelpButton 共享改动必须先核定所有消费者与唯一所有者；无法稳定捕获真实窗口时，该批视觉验收未完成，先解决捕获，不伪造弹层或放松门禁。其他命令、失败与回退规则见 [父计划](../10-21-ui-token-accessibility-expansion/implement.md)。
