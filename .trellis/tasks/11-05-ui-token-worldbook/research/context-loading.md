# P06 上下文读取索引

## 用途与边界

本索引与核心规范、捕获契约由 implement/check JSONL 直接注入；完整研究保留在原文件，按下表读取。它们是必读证据，不是可选参考，也不是已执行的产品验证。不要根据摘要重新猜测保存、取消、消息过滤、数据身份或文件读写契约。

2026-11-06最终规划摘要已获用户“ok”批准，主会话已运行 task.py start，当前为 in_progress。实现和检查各由一个所有者执行，Gradle 串行；保留其他人的工作区改动，不递归派发同类代理。历史planning研究描述当时状态，当前授权以task.json及本次派发为准。

## 加载完整性

- 优先使用原生注入，确认 prd.md、design.md、implement.md 的正文均已加载；若有保存完整输出的提示，先读该文件。
- 出现 `not inlined` 或 `truncated` 时，逐个读取所列文件全文；不能只凭 `trellis-hook-injected` 标记推定正文完整。无注入时按当前角色 JSONL 及三份规划正文执行既有 fallback。
- 在每个检查点动手前，读完下表对应文件；读取工具自身截断时分段继续，直至读完。实现交接记录已读取的路径，检查代理独立重读全部研究，不能沿用实现代理的阅读声明。
- JSONL 只登记规范与研究。不能为满足大小限制删除研究正文、提高项目注入上限，或把产品代码加入注入清单。

## 检查点必读

以下链接均相对本文件；任务根目录为 `.trellis/tasks/11-05-ui-token-worldbook`。

| 检查点 | 必读证据 | 读取目的 |
| --- | --- | --- |
| 建立测试宿主与完整 before，产品未改 | [捕获与共享契约](capture-and-shared-contracts.md)、[63 场景矩阵](screenshot-matrix.json)，以及下列三组主契约和验证文件 | 先掌握所有真实状态、窗口、Fake/平台接缝及原行为；再登记 47+16 个捕获用例，不能凭矩阵标签捏造状态 |
| 共享接口接入 | [列表/详情主契约](entry-list-detail.md)、[列表/详情验证与消费者](entry-list-detail-validation.md)、[编辑主契约](editor-contracts-tests.md)、[编辑验证](editor-validation.md)、[资料库主契约](context-transfer.md)、[资料库验证与消费者](context-transfer-validation.md) | 对照完整消费者与旧默认，统一维护必要参数和透传，不改其他页面默认 |
| 列表与书目详情 | [主契约](entry-list-detail.md)、[验证矩阵](entry-list-detail-validation.md) | 入口、分组/筛选、整书管理、消息过滤、48dp/200%与静态导航接线边界 |
| 编辑、关键词、正文与 Popup | [主契约](editor-contracts-tests.md)、[验证矩阵](editor-validation.md) | ID/extras、数字规范化、关键词 draft、保存立即返回、全屏二级草稿、作用域/试命中及真实窗口 |
| 共享数据导入/导出整页 | [主契约](context-transfer.md)、[验证矩阵](context-transfer-validation.md) | 五组 section、三类 launcher、null 取消、URI/16MiB、预览/冲突、头像与合并受捕获失败、busy 及权限边界 |
| 独立检查及最终整合 | 本表全部文件与三份规划正文 | 重核共享兼容、全部回调与隔离、18 个帮助入口、63 个真实 before/after、重复 after、旧 158 图及人工接纳门禁 |

## 已收敛的研究限定

- “确认失败保留预览”仅指头像应用/合并被现有 runCatching 捕获的失败；不承诺成功合并后 refreshCounts 失败恢复或跨资源回滚。
- 不改 AppGraph/导航 DI。真实 Screen 回调、独立 VM/Fake 与静态生产接线是不同证据，不称为已执行的生产导航集成。
- extras 现有上限按 String.length 的 16384 个字符计量；导入文件的 16MiB 按字节计量，不互换单位。
- 63 场景中的 Popup、ActivityResult/Resolver 与时钟探针仍须在获批实施后运行；静态加载成功不能替代这些验证。
