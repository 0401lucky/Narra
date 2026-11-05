# P10 技术设计

沿用 [父设计](../10-21-ui-token-accessibility-expansion/design.md)；实际入口、消费者与保存边界见 [设置研究](../10-21-ui-token-accessibility-expansion/research/settings-provider-entry.md)。

## 所有权与兼容接口

P10 拥有 `SettingsScreen.kt` 及本批必要的 `SettingsComponents.kt` 接口。用现有 NarraUi / NarraShapes 显式配置首页组 / 行 / 标题，保留 SettingsScreenPadding=20dp 等兼容默认。SettingsGroup 两个重载都透传需要的 shape / contentPadding，SettingsListRow 需要的新参数默认等于原布局，SettingsTopBar 复用既有 horizontalPadding。长标题换行仅在已迁移调用中开启。

不引入全局布尔迁移开关、自动生效的 CompositionLocal 或另一套 Palette。颜色继续来自实际 Material ColorScheme；选中面上的内容色按真实合成面取配对，不能把 primary 无条件当作 primaryContainer 的内容色。shared API 在本批结束前按 P11 的 Config / Models / Dialog 需要审查，避免下一批随意扩大公共面。

旧调用默认的兼容证据来自 P02 / P05 原参数渲染、定向行为和基准 verify；检查其他消费者的默认参数 / 用途，不为简单 getter 编写镜像测试。NarraButton 的 48dp 和 NarraAlertDialog 的滚动 / 操作区能力直接复用。

实现前核对发现源码守卫当前要求同名声明唯一，`SettingsGroup`的两个真实重载需同时覆盖。本批允许测试层为`Scope`增添显式声明数量映射，默认仍为1，仅本文件的`SettingsGroup`为2；数量不符或映射失效仍失败。两个重载分别注入硬编码时都必须被检测，不以整体白名单规避。该测试层扩展由同一实现代理持有。

## E10 最小截图矩阵：16 张

| 场景 | 数量 |
| --- | --- |
| 设置首页，固定长摘要，三主题×浅深，360×800 / 100% | 6 |
| 首页固定动态浅深 | 2 |
| 首页顶栏 / 服务组，320×640 / 200% 浅深 | 2 |
| 主题色调、颜色模式、显示 Sheet 各浅深，320×640 / 200%，显示滚到末项 | 6 |

六张 Sheet 图实际打开生产窗口，复用 P05 的真实 root 捕获。未修改的其他设置目的地不新增基准。保存 / 无草稿 / 保存中分支由行为驱动，不复制全状态截图组合。

## 行为与回退

真实首页主操作按草稿 / isSaving / 已保存配置分支，保存成功才走显式 onSaved；返回保持当前立即 pop，错误消息和 consumeMessage 不重写。测试主壳“设置”、提供商与默认模型等回调、三 Sheet 选中 / dismiss / 更新，以及末项滚动、标签 / Switch、邻接触控。

先提交可验证的 opt-in 兼容与 P10 调用，再检查 / 人工接纳本批视觉；任何 P02/P05 意外变化先缩小接口，不全局换旧默认或 record 消除失败。本批与 P11 分开验收 / 回退。
