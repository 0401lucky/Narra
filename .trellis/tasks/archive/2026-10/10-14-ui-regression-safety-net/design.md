# 视觉回归安全网：技术设计

## 范围

本任务建立现状基准、验证入口和证据登记。宿主截图优先采用 Roborazzi + Robolectric；设备测试继续验证材质、系统行为和交互；性能沿用 baselineprofile。工具依据见 [research/tooling.md](research/tooling.md)，用例边界见 [research/screenshot-matrix.md](research/screenshot-matrix.md)。

## 结构与文件责任

| 范围 | 拟修改 / 新增位置 | 责任 |
| --- | --- | --- |
| 依赖与测试设置 | gradle/libs.versions.toml、根 build.gradle、app/build.gradle | 固定截图依赖、应用插件、Android 资源及宿主测试配置；不升级主工具链。 |
| 截图测试 | app/src/test/java/com/example/myapplication/ui/screenshot/ | 合成数据、测试组合根、场景及确定性截图；不复制业务 UI。 |
| 批准的基准 | app/src/test/snapshots/ | PNG 与环境/用例清单，跟随代码审阅；失败图写 build 目录。 |
| CI | .github/workflows/android-quality.yml | 独立截图 job，保留原 quality job；始终保留差异报告。 |
| 设备回归 | 现有 app/src/androidTest/java/.../ui/ 下对应测试 | 复用背景气泡与交互用例，必要时补缺口。 |
| 证据 | 本任务 research 与后续 verification.md | 覆盖状态、人工确认、工具兼容性、基准来源及缺测。 |
| 生产可测性入口 | 仅实际阻塞测试的页面 | 优先现有 Fake；必要时提取保持行为的内容函数，不改变视觉或回调语义。 |

AssistantListScreen 目前直接接收 SettingsViewModel，不能按“已有无状态 Screen”假设工作量。需要提取时，原入口继续收集状态和绑定回调，原排序、搜索、删除确认不变，现有设备测试作为行为对照。禁止为测试启动真实 AppGraph、网络提供商或用户数据库。

## 固定宿主环境

- 截图 CI 使用固定 Ubuntu 镜像版本与当前 JDK 17；截图工具及 Robolectric 采用明确的发布版本。
- SDK 选择以最小接入验证为准，优先检验固定 API 35；目标 SDK 36 的系统行为仍由设备测试覆盖。
- 常规逻辑尺寸 360×800dp、density 2、fontScale 1，窄屏 320×640dp、fontScale 2；在运行清单保存最终物理像素。
- 固定中文 locale、Asia/Shanghai 时区、合成日期/消息 ID 和本地图片；字体来自固定渲染环境。
- 关闭自动推进的测试动画时钟，在明确时刻截图；等待图片解码和必要的 Compose 布局稳定，不使用任意 sleep。
- 三个应用主题显式传入深浅色；动态色采用固定 ColorScheme，并复用 resolveAppColorScheme。主题接线另有断言，不通过测试自造配色掩盖生产分支。
- Windows 本机结果可调试，但不能拿另一宿主渲染的 PNG 静默覆盖 CI 基准；初始基准也必须由同一固定环境生成。
- 对 Windows 协作者，优先检查已有 WSL/Docker 等能复现该环境的工具；若不可用，保留环境限制并准备可审阅的运行方式，不自动安装或修改全局系统配置。

## 用例与结果

真实生产组件接收纯合成状态、可控回调和本地资源。截图前断言根节点、代表性内容和有效尺寸，避免空白图通过。基准矩阵中的用例 ID 必须映射到测试名和输出文件，零测试、缺必需场景或缺基准均不能成功。

严格比较设置零变化比例、零色差容忍、零水平/垂直对齐偏移，使用选择版本的实际 API。验证同一链路中的“单个不透明像素改变”和“缺失基准”均非零退出；恢复后连续两次通过，并证明验证未改变基准哈希。

基准生成与验证分开：
- record 产出候选基准，人工查看后记录确认；新基准不能因为来自程序就自动可信。
- verify 只对已批准基准比较，失败写实际图、差异图与报告到 build 目录。
- 初始图中的既有缺陷登记为后续改进项；不在安全网任务顺手修视觉。
- 更新基准需要列明预期差异、确认人/反馈来源与对应变更，不允许 CI 运行 verifyAndRecord。

## CI 接入

新增 screenshots job，与现有 quality job 独立执行，不新增发布权限、密钥或外部截图服务。使用仓库中的批准基准，运行真实 verify 入口；失败时仍以 always() 保存截图差异、Roborazzi 报告与测试结果。仅上传合成 fixture 产物，不扫描或上传仓库已有用户截图。

最小接入阶段确认插件任务的测试筛选方式。可以使用官方 testDebugUnitTest + roborazzi.test.verify=true 入口筛选 screenshot 包，但不能全局改变原有 JVM 单测集合。仅修改 YAML 不等于远端 CI 已通过，实际 run 链接或未运行状态写入验证记录。

## 设备与性能

宿主不能完整表达 Haze/玻璃、系统栏或输入法时，保留实际实现并在设备矩阵验证，不能改普通底色换取通过。初始设备结果可复用对应实际源码版本的既有测试，版本或配置不一致则重新记录。

性能登记详见 [research/performance-baseline.md](research/performance-baseline.md)。先检查已有归档，再在固定物理设备运行现有 S0–S3 / F1–F3 / N1；保持工具、数据、Profile 和编译条件可比。缺少设备可以继续独立完成宿主门禁，但 A6 和安全网整体验收保持未完成。

## 回退

每批先记录文件差异和基准哈希。接入失败只撤回本任务新增配置或测试，不回滚用户已有变更；保留失败证据。若需主工具链升级、替换测试后端或扩展产品范围，先以证据更新设计后再审阅。
