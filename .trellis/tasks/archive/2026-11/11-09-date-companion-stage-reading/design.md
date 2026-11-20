# D4 立绘演出与连续阅读：技术设计

以 [本块PRD](prd.md) 与 [父设计](../11-09-date-companion/design.md) 为权威；本块已完成产品实现和工程/原生验证，实际接口与证据见 [2026-11-19交接](research/handoff-2026-11-19.md)。旧视觉差异未接纳，D5/D6继续按依赖单独实施。

## 依赖输入

[D3](../11-09-date-companion-style-observation/prd.md) 已通过独立验收并提供对应接口/验证证据。接口差异先回到父设计协调，不在本块建立平行数据或备用状态单例。

## 数据流与合同

使用D3 versioned ParsedReply与BeatId，不将流式未提交片段编入正式演出。Player只接受本地advance/reveal/replay等事件，与send/retry分开；sourceRevision变更直接失效。资源先解析用户本轮明确的场景/角色外观选择；选择套装后按套装目标表情→套默认→旧场景图→头像/姓名回退，未作新选择才沿用旧单图。历史单图字段不能永久压住新套装，并区分PORTRAIT/DECOR类型。D4实现基础跨场次reader query与稳定keyset供D6扩展日期/管理，不等待D6才实现读取。声音只暴露选中SpeechUnit回调，D5后续接真正网络/播放。

## 写入与协调范围

以下源码路径相对app/src/main/java/com/example/myapplication；新类型/目录是计划位置，已有类的详细行号见父research。

- RoleplayScreen/Content/DialoguePanel、RoleplayReadingMode及新增CompanionStage/Player/Overlay。
- RoleplayPortraitLayer及陪伴资源解析/图集/布局设置，复用LocalImageStore。
- 基础跨场次分页读取接口与ViewModel滚动状态（按D1仓储合同），D6后续接管理。
- 现有ui-token/系统栏/无障碍适配与受影响截图/UI测试；不修改用户manifest原差异。

你不是唯一在代码库工作的执行者。实施时核对最新差异、保留用户与其它任务改动，所有跨块文件在明确交接后串行写入；不回滚或覆盖他人文件。

## 兼容与失败策略

原用户未设置演出时不强制切换；GROUP/剧情长文可继续原UI。不要拿RoleplayOfflineReplaySupport性能夹具当播放器，不让显示状态变成运行权威。不复制图片二进制到书签。

高频重组/旧timer、私有资源引用、滚动锚点和旧UI基准为风险。回退只停用演出视图，原文/新场仍可阅读；不删除用户素材或全局样式。

## 验证设计

新增CompanionBeatPlayer/Resources/ReadingPagination/StageUi测试；原生IME和系统返回，真实图片路径/像素核验；受影响E04/E05/手机入口截图按实际变更选择verify，绝不预先record。

每个本块AC都需绑定行为证据；请求计数、源ID/修订、仓储结果、实际UI回调和错误/取消路径优先于实现镜像断言。遵循父计划的合成数据、隔离库及真实效果单独标注规则。

## 上下游交付

输出本块实际接口/文件范围、错误/恢复约定和验证结果；更新本块research中的implementation/check证据供下一块引用。公共contract发生实质变化时同步父/相关子设计，不只通过代理消息约定。
