# P06 验证记录

用户于2026-11-06接纳[63组前后对照](review-corrected/index.html)并批准提交归档，已完成`c925da5e`和`b7af5429`两笔工作提交。原158张基准保持，共221张PNG；[接纳记录](research/visual-acceptance.json)保留用户原话与审阅哈希。222/222真实截图和1973/1973完整JVM均通过，本任务已归档；后续main推送及Actions/R2发版按用户新指令处理。

## 修改前来源

原始提交为 `43ebda6dc40a5d21812cd594d10777ce3f2b166a`。最终对照使用[before-corrected](research/before-corrected/manifest.json)、[after-corrected](research/after-corrected/manifest.json)及[after-repeat-corrected](research/after-repeat-corrected/manifest.json)。三轮均有63张图、18个独立窗口（4个Popup）、14项生产/配置来源与9项相同捕获源。

目视复核发现旧after的三张资料库预览只露提示标题，随后仅修捕获定位和实际正文/计数/冲突边界断言，并在原始提交的隔离树重取before。其余60张新before和after均分别与旧记录逐张同SHA；新after、repeat及最终全量验证中的第三轮63图全部同SHA。完整来源、原始Git字节、主树未变证明和报告哈希见[来源链](research/capture-recovery.json)。旧13项来源含混合换行，仍保留原始清单；修正before的14项使用精确Git blob，不将两种字节口径混写。

历史[before-02](research/before/manifest.json)及旧after/repeat完整保留用于追溯；旧review因三张预览取景不足不用于接纳。更早before-01曾截错Popup，也已明确作废。

| 验证 | 结果 | 原始证据 |
| --- | --- | --- |
| 捕获设施探针 | 11 项通过、退出 0；原会话已执行，本次核对来源 | [检查点一交接](research/checkpoint-1-capture-summary.md)、[探针摘要](research/probes/final-summary.json) |
| 修改前完整截图 | 63 张，18 个独立窗口，其中 4 个 Popup | [原始清单](research/before/manifest.json) |
| 修改前 JUnit | 47+16 项仅缺少新基准，另 1 项计数守卫缺图；64 项预期失败、退出 1 | [原始报告摘要](research/before/summary.json) |
| 续接时逐项复核 | 63 图、原始语义/测量/JUnit，13 项产品配置与 9 项捕获源，以及 158 张旧基准均匹配 | [本轮复核](research/resume-before-validation-20261106.json) |
| 审阅工具负向检查 | 缺独立 root、混入 Stage、窗口头不一致、测量漂移均被拒绝；仅内存注入 | [续接记录](research/continuation-20261106.md) |
| 预览捕获正反向 | 2项通过，拒绝旧标题-only取景，修正后正文完整 | [原始JUnit](research/capture-runs/recovery-probe-01/test-results/TEST-com.example.myapplication.ui.screen.settings.ContextTransferPreviewCaptureTest.xml) |
| 审阅工具修正门禁 | 旧取景、声称可见但实际越界、其余60图漂移均被拒绝；原PNG不改，联络表正确合成透明圆角 | [定向验证](research/review-recovery-validation.json) |

## 产品与回归

| 已取得结果 | 状态 | 原始证据 |
| --- | --- | --- |
| UI、共享兼容、源码守卫等专项 | 130 个唯一用例均有最终通过结果；最后16项集中复验覆盖全部文字测量辅助调用 | [分批汇总](research/specialized-validation-summary.json) |
| 世界书与导入导出领域回归 | 106 项通过，0 失败/错误/跳过 | [领域日志](research/implementation-runs/contracts-01/run.log) |
| Lint、dev构建、AndroidTest Kotlin编译 | 三项均实际执行并通过、退出 0；设备测试只编译，未运行真机 | [构建日志](research/implementation-runs/build-01/run.log) |
| Lint警告对照 | 55项Warning、3项Hint与前序最终报告逐项一致，无新增；比较ID/消息/文件，不按格式化后行号误判 | [对照结果](research/lint-comparison-20261106.json) |
| 接纳前完整截图门禁 | 实际222项：158旧图通过，64项仅为63新图与原计数守卫缺基准；0其他失败/错误/跳过，退出1如实保留为接纳前历史 | [接纳前截图报告](research/capture-runs/all-screenshots-corrected-01/summary.json) |
| 独立全范围检查 | 代码、测试、spec、窗口/边界、Git来源与全部报告通过复核，无代码阻断；可交人工视觉接纳 | [检查结论](research/final-check.md) |
| 接纳后完整截图 | 222/222，17 suites，104.4秒；实际执行，0失败/错误/跳过，221张均unchanged，recorded/added/changed为0 | [完整截图结果](research/post-acceptance/screenshots-01/summary.json) |
| 接纳后完整JVM | 无selector，1973/1973，261 suites，216.2秒；实际执行且退出0，0失败/错误/跳过 | [完整JVM结果](research/post-acceptance/jvm-01/summary.json) |
| 接纳后独立质量门禁 | 原始报告、全部221 PNG和14+9来源前后哈希一致，无修复或新阻断 | [最终核对](research/post-acceptance/audit.json) |

视觉接纳、完整门禁、具体提交和归档已完成。221张基准清单见[接纳哈希](research/accepted-png-hashes-20261106.txt)；接纳前预期缺图报告仅保留历史。journal以两笔工作提交记录，不混入v2.4规划或其他工作区改动。

重新生成最终审阅页使用：

```powershell
python -X utf8 .trellis/tasks/archive/2026-11/11-05-ui-token-worldbook/research/build_review.py --before research/before-corrected --after research/after-corrected --repeat research/after-repeat-corrected --output review-corrected
```

## 验证边界

宿主截图/交互使用固定虚构数据、普通 Application 与测试 Fake/内存 ContentProvider，不接触用户数据库、真实文件、凭据或网络。设备上的系统选择器、IME/读屏、系统栏、材质与物理性能尚无本批实测证据。构建和更新版本保持 `2.2.12-dev`；本任务归属尚未发布的 v2.3。
