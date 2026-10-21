# v2.3 设计令牌与无障碍试点验证记录

## 当前状态

2026-10-21 当前试点实现、人工视觉接纳与本机验收完成，已按用户批准的两批清单提交并归档为 `completed`。最终视觉基准接纳、真机效果和性能结论分别登记。

## 提交与归档

- `67ed7dfb`：`feat(ui): 统一v2.3试点令牌与无障碍行为`，320 个清单文件。
- `ce710003`：`test(screenshot): 接纳v2.3统一试点视觉基准`，75 张 PNG 与 manifest 新增的 12 行接纳记录；原有并发历史段落修改仍未暂存。
- 已核验 78 个接纳后哈希及暂存范围；本次仅做提交与文档收尾，没有重复运行已通过的完整测试。归档目录为 `.trellis/tasks/archive/2026-10/10-21-ui-token-accessibility-pilot/`，归档引用同步修正。
- 归档脚本默认会暂存整个 archive，因此使用 `--no-commit`，再只提交当前试点搬移及引用修正；其他旧任务搬移、用户文件保持原状。
- 未推送、未发包、未修改发布版本或 `dev.json`。当前构建配置仍为 2.2.12-dev；新视觉 CI、D01 与物理性能保持待补。

## 实施前冻结

| 项目 | 证据 / 当前结果 |
| --- | --- |
| 原有工作区 | `research/worktree-before.json` 保存起始 HEAD 与原有脏路径；旧任务归档搬移、web、日志和用户素材不属于本轮改动 |
| 原始截图基准 | `research/baseline-sha256-before.json` 保存 78 张 PNG 的 SHA-256（76 个矩阵用例 + 2 张冒烟图） |
| 初次 verify | 退出 0，但 `testDebugUnitTest` / `verifyRoborazziDebug` 为 UP-TO-DATE，不计为本轮真实比较；见 `research/baseline-verify-before.log` |
| 强制真实 verify | 退出 0；79 项测试，0 失败 / 错误 / 跳过；35 个任务全部执行，5m2s；78 张 PNG 哈希全部不变。见 `research/baseline-result.json` 和 `research/baseline-verify-before-rerun.log` |
| 设备 | 2026-10-21 使用本机 SDK 的 adb 重新执行 `devices -l`，无设备；没有启动模拟器或运行物理性能测量 |
| 迁移前大字体 | `research/before-large-font.png` 为现有 H04 浅色图的缩略并排，保持原始基准文件不变 |

## 验收表

| 验收 | 状态 | 证据 |
| --- | --- | --- |
| A1 默认形状 / 间距生产迁移 | 通过（试点范围） | 三处页面实际消费共享令牌；精确扫描 / 例外清单通过，未宣称全 App 完成 |
| A2 主题 / 颜色对比与缓存 | 通过（受测范围） | 主题 / 动态色、玻璃与无玻璃实际渲染、透明反例先红后绿；异步 URI 加载生命周期仍未覆盖 |
| A3 个性化与材质兼容 | 宿主 / JVM 通过；设备待补 | 13 皮肤与保存几何、现有存储 / 玻璃谓词随整套 JVM 测试通过；5 项对应设备用例仅编译验证 |
| A4 语义 / 触控 / 200% 字体 | 宿主通过 | 实际 P01/P02/P03 的语义 / 回调 / 48dp 与 root 坐标断言、大字体文字与展开操作测试通过 |
| A5 新违规门禁 | 通过 | 12 项规则与生产绑定测试通过；正反例和未消费 / 移位例外真实失败 |
| A6 视觉人工确认与最终质量检查 | 本机已接纳并通过 | 用户确认后接纳 75 张变化，另 3 张不变；79 项截图真实复验全部通过，单测 / 编译 / Lint / dev 构建全通过 |
| A7 版本记录与扩面清单 | 已更新 | v2.3 实施状态及 `research/expansion-list.md`，设备 / 性能 / 新视觉 CI 缺口保留 |

## 代码检查过程

- 共享令牌与配色首轮生产编译已通过：42s，9 tasks（2 executed），详见 `research/implementation-verification.md`。
- 首轮定向 JVM：14 项中 13 通过、1 失败；4 项新颜色行为通过，失败为源码守卫的旧共享声明清单不匹配。守卫代理已精确登记原样保留的线下布局和几何例外，待下一轮复验，未增加目录级豁免。
- 独立审查发现并移交实现修复：图片缓存键的强引用、Material 容器与品牌文字不配对、辅助按钮配色 / 命中不足、透明玻璃实际合成与计算输入差异；这些都需行为测试后才能验收。
- 第五轮定向结果：23 项中 22 项通过；原有具体失败已清，源码守卫 12/12、主题 / 缓存 / 实际玻璃 5/5、大字体页面 5 项均通过。剩余为新加入的跳转滚动测试未等动画完成，继续修复复验；完整结果以最终集成验证为准。
- 实现代理第八轮已清所有定向失败：31 个用例均有通过证据（详见实现记录；最后一批 14/14），产品及夹具冻结后进入整体验证。
- 第一轮整体验证 `testDebugUnitTest :app:compileDebugAndroidTestKotlin lintDebug assembleDebug`：退出 1，5m15s，1641 项单测中 2 项失败 / 0 错误 / 0 跳过；失败为 H04 P03 的旧首消息可见守卫，窄屏实际自动贴底后该首消息不可见。AndroidTest 编译与 dev 构建已执行，Lint 未取得完整通过结果；结果见 `research/quality-first-result.json` 与 `research/quality-final.log`。
- 最终检查另发现液态玻璃 + 深色 + 白背景图 + 背景玻璃关闭时的透明合成低对比反例。检查代理获授权修复实际前景选择并补渲染回归，同时将 H04 守卫改为当前可见尾部内容和头部 / 列表 / 输入区边界；用例、参数和原 PNG 保持。
- 检查代理最终定向 29/29 通过（0 失败 / 错误 / 跳过），含上述透明实际渲染反例、H04 六例、主题 / 玻璃 / 语义和全部守卫；详见 `research/check-report.md`、`research/check-targeted-after-summary.json`。

## 最终整体验证

- 完整 JVM 单测：1642 项全部通过，0 失败 / 错误 / 跳过；保存于 `research/unit-final-result.json`，与截图比较命令的结果分开登记。
- 截图 verify：79 项检查，75 项因 Roborazzi 像素差异失败，0 错误 / 0 跳过；全部失败均指向真实 compare 输出，无缺图或页面守卫错误；详见 `research/screenshot-result.json`。没有执行 record 或降低阈值。
- [完整审阅页](review/index.html) 提供 78/78 原图 / 候选 / 差异，75 张变化、3 张一致；来源与像素计数见 `review/review-data.json`。原始 PNG 的 SHA-256 全部保持不变。
- 已目视检查常规深浅色、大字体、玻璃开关及代表性皮肤组合，见审阅目录的 `normal-theme-overview.png`、`large-font-comparison.png`、`material-skin-overview.png`；最终视觉接受仍由用户确认。
- 最终命令 `testDebugUnitTest :app:compileDebugAndroidTestKotlin lintDebug assembleDebug --console=plain`：退出 0，`BUILD SUCCESSFUL in 4m51s`；69 tasks（14 executed / 55 up-to-date），三项门禁均通过。Lint 无 error，保留既有 Warning / Hint，不等同于零警告；日志 `research/quality-after-check.log`，机器可读摘要 `research/quality-final-result.json`。
- 生成 dev APK 使用现有配置版本 2.2.12-dev / 20212，仅作为本次构建验证，不代表 v2.3 已发布；没有上传、安装、提交或推送。

## 用户接纳后复验

- 用户在候选图接纳问题后回复“ok，还有任务吗？”，确认接纳本批视觉。
- 对审阅图与冻结原始基准逐项比对后，仅替换 75 张变化的 PNG，3 张一致图不动；没有重新 record，没有改变 ID、数量、参数或像素阈值。原始图仍保留在 `review/before/`，接纳结果和全部新哈希见 `research/baseline-acceptance.json`。
- 命令 `:app:verifyRoborazziDebug --tests "com.example.myapplication.ui.screenshot.*" --console=plain`：退出 0，`BUILD SUCCESSFUL in 45s`，79 项检查，0 失败 / 错误 / 跳过；测试任务真实执行，78 个新基准哈希在复验前后一致。证据 `research/verify-accepted-baselines.log`、`research/accepted-verify-result.json`。
- 更新 manifest 时只增加本轮当前接纳记录，原有并发修改的历史段落未恢复或覆盖。本轮新视觉的远端 CI 尚未运行，不能借用旧基准的 CI 结果。
- 当前试点实现 / 宿主验收完成，工作提交与归档见上节；D01 / 物理性能缺测和后续页面扩面继续单列。

## 结果边界

无连接设备时，Robolectric 的真实 Compose 布局 / 交互可以作为宿主行为证据；AndroidTest 编译不等同于设备运行。D01 系统栏、输入法、实际玻璃效果和物理性能继续待补，未输出“性能无回归”结论。

实施期间发现 `app/src/test/snapshots/manifest.md` 出现未由本轮主会话、实现代理或守卫代理写入的并发修改（移除原有 CI 确认文字）。保留该外部修改，当前任务不恢复或纳入提交；截图事实以冻结的 78 个 PNG 哈希和本轮真实 XML / 日志为准。
