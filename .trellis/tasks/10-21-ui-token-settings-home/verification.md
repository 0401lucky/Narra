# P10 设置首页验证记录

状态：首页与三个外观Sheet已完成实现、用户视觉接纳及独立检查；接纳后127/127真实截图、1755/1755完整JVM全部通过，共126张PNG且哈希未变。Lint/dev构建/AndroidTest实际编译通过，无新增警告。等待具体提交清单确认与归档；功能归属v2.3，构建配置保持2.2.12-dev，未发布。

## 2026-11-05 人工接纳

- 用户明确回复“接纳这16组效果（推荐）”。仅从已审阅after复制16张E10，全部与review-data及[接纳记录](research/visual-acceptance.json)的SHA-256一致；旧110张未变，共126张。
- [接纳后完整哈希](research/accepted-png-hashes-20261105.txt)已冻结，审阅页已同步accepted=true。最终127项截图和完整JVM由独立检查代理串行真实执行，未重复最新已通过的Lint/构建/AndroidTest编译。
- 最新质量命令[退出0、5分19秒](research/quality-final/summary.json)：Lint 0 errors /55原有warnings/3 hints，无新增警告；dev实际打包、AndroidTest Kotlin实际编译。此前3条ModifierParameter警告通过缩小TopBar为titleIsHeading局部语义参数及整理私有row参数顺序修复，16张候选像素完全未变。
- 接纳后最终[127项截图](research/accepted-screenshots-20261105/summary.json)全部通过，14 suites，真实比较48秒；[完整JVM1755项](research/accepted-full-jvm-20261105/summary.json)全部通过，236 suites，真实执行91秒，两轮均退出0、无失败/错误/跳过。普通JVM不执行像素比较，因此两轮XML/日志分别归档。
- 两轮后126张PNG及两生产源码SHA均未变，[最终独立结论](research/check-summary-20261105.md)无未修复问题，可进入提交审阅。

## 范围与前置

- P05已独立提交归档，P10基点为`main / 18c1613f`。用户明确要求侧栏收尾后做设置首页，现有P10已启动，未创建重复任务。
- 本批产品文件为`SettingsScreen.kt`、`SettingsComponents.kt`；共享接口显式启用新布局，其他调用方旧默认保留。未修改NavGraph、ViewModel保存策略或P05生产代码。
- 源码守卫额外支持精确声明数量，默认仍为1，仅SettingsGroup的两个重载登记为2并全部扫描。
- 110张旧PNG已冻结于[清单](research/accepted-png-hashes-before-p10.txt)，首轮前后与截图比较后均未改变。

## 已取得结果

| 检查 | 当前结果 |
| --- | --- |
| 首版迁移前捕获 | 16张actual/语义、6个真实窗口；17项仅缺16个基准与计数守卫失败，产品修改前取得 |
| 首轮相关行为/守卫 | 34项中31通过、3失败；详见下述修正 |
| 失败项定向复验 | Slider实际边缘点击通过，Profile入口与保存成功/失败3项通过 |
| 接纳前完整相关JVM | [113/113、20 suites、72秒](research/targeted-final/summary.json)，最后参数修正后真实执行，0失败/错误/跳过 |
| 首版整包截图 | [127项、17预期缺图失败](research/screenshot-verify/summary.json)，55秒，0错误/跳过；110个旧截图全部比较通过 |
| 最终整包截图 | [127项、17预期缺图失败](research/screenshot-final/summary.json)，50秒；旧110个截图通过，0错误/跳过 |
| 最终显示Sheet候选 | 已从原始提交重取before并重取after，完整末项Switch可见；主会话复核浅深原图及全部16组总览 |
| 独立代码/捕获检查 | [未发现额外代码问题](research/check-review-20261105.md)；最终捕获/原始提交/哈希与最后参数修正均复核通过 |
| Lint/dev/AndroidTest编译 | [退出0、无新增警告](research/quality-final/summary.json)，13个task实际执行、47个UP-TO-DATE；主会话未并发运行Gradle |

## 本轮已修正问题

- 显示页Slider内部触控高度实测44dp；仅设外层heightIn未改变内部语义区。现局部设置SliderDefaults.Thumb最小48dp，真实touch bounds及顶部边缘单次点击回调已通过。
- Profile原有设置入口没有Role.Button；测试按真实命名/点击契约定位，并滚至固定底栏上方，未扩改P01产品。
- 保存成功测试的合成已存URL缺少规范化尾斜杠，真实设置编辑器规范化后被误判为新草稿；只修该VM测试的已存fixture，未修改截图fixture或VM/NavGraph。
- 首版显示Sheet截图只将末项Text滚入视口：before滚动位置183/264，after为518/670，不能证明最后开关完整可见。现已改为滚至真实max并断言整个末项Switch命中区域处于窗口内。

## 捕获修正与剩余边界

为了保持前后捕获策略一致，已使用`18c1613f`的隔离detached worktree，仅叠入修正的3个E10测试文件及捕获工具，重取16张before，再从当前实现重取after。before显示浅深均为264/264，after均为670/670，截图前断言整个末项Switch真实命中区位于窗口内。两阶段各14张非显示图与initial字节相同，只有两张显示图变化；主工作区产品实现及110张基准未变。

首版证据保留于`research/before-initial/`、`research/after-initial/`，完整原始提交、覆盖/复制哈希和参数见[捕获来源记录](research/capture-recovery-provenance.json)。最终[16组审阅页](review/index.html)已重建，16组均有变化；未record或接纳未确认图片。源码守卫实际为13项测试（当前上下文JSONL为14条，二者不是同一口径）。

新视觉已接纳，接纳后的最终完整JVM与截图门禁均通过，具体提交与归档仍待完成。真机读屏、IME/系统栏、实际材质、物理性能和新视觉远端CI未取得结果，不能用宿主或编译代替。
