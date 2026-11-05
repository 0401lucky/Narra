# P11 独立检查结论（2026-11-05）

**独立检查及接纳后门禁全部完成，可进入具体提交清单审阅。** 用户先接纳32组效果，再接纳5张稳定捕获修正；最终真实截图159/159、完整JVM1826/1826通过，均无失败/错误/跳过。158张PNG及产品源码哈希一致，最后Lint无新增警告。尚未由本检查提交或归档。

## Findings (fixed)

接纳前代码审查未发现产品回归；接纳后首次全截图复验发现4个模型场景的原生Tab按压水波未稳定。推进Compose与主Looper各1000ms仍产生不同纹理，现仅在`ProviderSettingsScreenshotE11Test`私有捕获路径使用真实Tab的语义OnClick；产品动画、回调和行为测试的真实触控均未改变。

在`ac22bb27`的隔离worktree只叠捕获三文件补取before，再叠当前8个产品修改文件与用于原字节对齐的未改SettingsComponents取after。两轮after的32张PNG逐字节一致；其中27张与此前接纳图相同，5张只去除Tab瞬态反馈并获用户再次接纳。原始before/after及初次接纳记录均保留；未record、降低阈值或篡改原图。

## Findings (not fixed)

没有未解决的代码或本机验证阻断。具体提交清单、提交/归档和父任务四批收口由主会话继续。真机读屏、系统返回/窗外触控、IME/系统栏、实际材质、物理性能与新视觉远端CI仍待补；宿主/编译不替代设备证据。

## Verification

- 最终真实截图：[159/159、15 suites](accepted-screenshots-20261105/summary.json)，0失败/错误/跳过，退出0，63秒，2 tasks实际执行/33 UP-TO-DATE。有效日志为`run-final.log`；首轮155/159及4个瞬态差异另存`attempt-1`。
- 完整JVM：[1826/1826、245 suites](accepted-full-jvm-20261105/summary.json)，无selector，0失败/错误/跳过，退出0，129秒，testDebugUnitTest实际执行。普通JVM不比较像素；在稳定捕获代码下运行，后续仅5张PNG/文档变更，无需重复。
- 最后Lint：[capture-stability-20261105/lint-final](capture-stability-20261105/lint-final/summary.json)，退出0，112秒，3 tasks执行/30 UP-TO-DATE；实际重新分析单测，主分析和报告复用未变结果。仍为0 errors、55旧warnings、3 hints，与此前有效报告逐项一致，无新增/删除。
- TypeCheck/dev/AndroidTest：复核[quality-verified](quality-verified/summary.json)，退出0、325秒，实际dev打包与AndroidTest Kotlin编译。捕获修正未改产品/设备测试，因此未重复构建；新截图测试代码已编译并由全JVM/最终截图实际执行。
- 接纳前定向结果保留：52/52 P11/守卫及144个不同受影响用例的分轮通过证据，未把分轮结果伪称为单次全绿。
- 最终158张PNG清单SHA：`b31439fd162a2323a62edc0fea0bc289b827635a15dbc45d3cd6899f2c7e1ec7`。5张修正外的153张保持原字节；9个产品/共享源码、APK、版本/更新配置和manifest原P10及历史正文均未变。
- [最终审计](check-final-accepted-audit-20261105.json)记录全部当前哈希和门禁。[稳定捕获来源](capture-stability-20261105/isolation-provenance.json)记录原Git对象、覆盖SHA、同策略before/after及32张重复一致；96张捕获PNG复核后，隔离目录与Git worktree登记已安全清理。

本检查接纳前独立核验实现代理报告；接纳后实际执行截图、稳定性诊断/隔离重捕、无selector完整JVM和必要Lint，仅修正一个E11截图测试文件。没有修改产品、接纳基准、提交或推送；5张基准由主会话依用户第二次明确接纳写入。全部Gradle已结束并交还执行权。
