# Research: 构建、Baseline Profile、启动与可观测性扫描

- Query: 扫描 baselineprofile/宏基准、启动初始化、DI/AppGraph、Room/后台任务、R8/资源压缩、Compose compiler reports、Gradle/依赖/CI、StrictMode/ANR/崩溃采集，并为 2.2.6-dev 规划可重复的性能验证矩阵。
- Scope: mixed（内部源码/配置/已有构建产物 + Android/Gradle 官方文档与 Google Maven 版本元数据）
- Date: 2026-08-11

## Findings

### 结论摘要（按优先级）

1. **P0：现有冷启动基准不能证明测到了目标页面。**
   `StartupBenchmarks` 只有 `StartupTimingMetric`、冷启动和 10 次迭代
   (`baselineprofile/src/main/java/com/example/myapplication/baselineprofile/StartupBenchmarks.kt:24-41`)；
   “欢迎/历史”两个等待的返回值被直接丢弃 (`StartupBenchmarks.kt:45-54`)。
   当前 `AppRoot` 在合规未确认时只渲染门禁，确认后才构建导航
   (`app/src/main/java/com/example/myapplication/ui/AppRoot.kt:84-118`)，而导航起点是
   `ROLEPLAY_GRAPH` (`AppRoot.kt:129-138`、`ui/navigation/RoleplayNavGraph.kt:62-66`)。
   “历史”语义实际来自普通聊天 `ChatScreenSupport.kt:48`，不是 RP 起点；源码/资源检索也只找到
   `strings.xml:164` 的设置标签“欢迎页”，没有一个明确的启动 ready 合约。等待失败不抛错，
   因而设备残留的合规/数据库状态会改变被测画面。

2. **P0：应用自己的 Baseline Profile 当前没有可复核产物。**
   `baselineProfile` 配置为保存到源码、合并到 main、且关闭构建时自动生成
   (`app/build.gradle:175-179`)；`app/src/main/generated/baselineProfiles` 在本次扫描为空。
   旧的 `app/build/intermediates/merged_art_profile/baseline/mergeBaselineArtProfile/baseline-prof.txt:1-20`
   以 Coil 等依赖类开头；按 `Lcom/example/myapplication/` 全文检索为 0 个匹配，不能把旧 `.dm`
   文件或依赖库 profile 当作 Narra 路径覆盖证据。`CompilationMode.Partial(BaselineProfileMode.Require)`
   仍然可能被依赖 profile 满足，但无法证明应用页面已被收集。

3. **P0：启动区间存在多个未隔离的后台/磁盘/网络活动。**
   `ChatApplication.onCreate()` 立即调用 `AppGraph.launchStartupTasks()`
   (`ChatApplication.kt:45-48`)；其中一条 IO 协程执行敏感数据迁移，另一条确保内置预设，第三条
   长期收集设置 (`di/AppGraph.kt:422-438`)。预设确保逻辑会逐个读取/Upsert DAO
   (`data/repository/context/PresetRepository.kt:88-107`)，首次 DAO 访问会触发
   `Room.databaseBuilder(...).addMigrations(...).build()` 对应数据库
   (`di/AppGraph.kt:103-109`)。
   合规通过后的 `LaunchedEffect` 同时启动自动更新检查和周期 Worker
   (`AppRoot.kt:84-88`)；更新仓储在冷状态下会读取缓存并可能联网抓取元数据
   (`data/repository/AppUpdateRepository.kt:48-65`、`:168-185`)，Worker 会用网络约束注册每 3 小时任务
   (`system/moments/MomentsAutoGenerationWorker.kt:36-50`)。这些活动不应进入“无网络、固定 fixture”的滚动/启动基准，
   但现有 `setupBlock` 只有 `pressHome()` (`StartupBenchmarks.kt:42-44`)。

4. **P0：settingsFlow 在启动期被重复冷收集，存在需 Trace 证实的主线程成本。**
   `DefaultAiSettingsRepository` 直接暴露底层 cold `settingsFlow`，没有共享层
   (`data/repository/ai/AiSettingsRepository.kt:11-15`)。首屏至少有四个收集者：
   `AppRoot.produceState` (`ui/AppRoot.kt:47-52`)、`SettingsViewModel` 的 `SharingStarted.Eagerly`
   与额外 `collect` (`viewmodel/SettingsViewModel.kt:141-169`)，以及 AppGraph 的 context-log 收集
   (`di/AppGraph.kt:430-436`)。每次映射会同步读取多组 SecureValueStore 值并解析 JSON
   (`data/local/AppSettingsStore.kt:156-175`)；Keystore 每次解密都会加载 KeyStore、创建 Cipher
   (`data/local/SecureValueStore.kt:27-33`、`:78-116`)。其中前三个收集路径运行在 UI/ViewModel 生命周期上下文，
   需用 Perfetto/主线程 trace 定量确认，不能在无数据时直接认定为根因。

5. **P0：进入 RP 起点会同步装配很宽的 AppGraph，并立即启动多条 Room Flow。**
   `rememberRoleplayViewModel` 的工厂参数访问 AI 网关、提示词服务、会话/RP/脚本/记忆/电话/经济/语音等
   大量 lazy 依赖 (`ui/navigation/NavigationViewModelOwners.kt:34-67`)；这只是延迟到导航首帧，
   并没有缩小启动工作。`RoleplayViewModel.init` 随后并发启动场景、摘要、会话、当前场景/会话/参与者、
   消息、日记和映射等观察 (`viewmodel/RoleplayViewModel.kt:342-415`)，底层对应多条
   `flatMapLatest`/DAO Flow (`viewmodel/RoleplayObservationSupport.kt:61-214`)。核心 ready 标记必须等到
   目标数据加载完成，而不是首个 Compose 帧。

6. **P1：Baseline 构建不代表 R8/正式包运行形态。**
   `release` 开启 `minifyEnabled true` (`app/build.gradle:95-103`)，但没有开启资源压缩；`baseline` 明确
   `minifyEnabled false`、`shrinkResources false` (`app/build.gradle:110-121`)。R8 规则还保留整个
   `com.example.myapplication.model` 包及全部成员 (`app/proguard-rules.pro:23-39`)。因此 Baseline Profile
   宏基准测的是未混淆、未压缩的 profileable 包，而不是发布包的类/资源布局。未来应至少有一个
   “release-like size/R8”验证（是否作为宏基准 build type 需先定案），不能用当前 `assembleBaseline` 代替。

7. **P1：APK 体积的主要项不是 R8 可消除的普通代码。**
   `app/build.gradle` 无 `splits`/`abiFilters`，且无资源压缩配置（`app/build.gradle:59-135`）；同时无条件引入
   ML Kit 中英日韩四套识别包 (`app/build.gradle:234-237`)、扩展 Material/Lucide 图标和 Markdown/Haze
   (`app/build.gradle:216-243`)。已有 2.2.5-dev debug APK
   `app/build/outputs/apk/debug/Narra-v2.2.5-dev-20205-dev.apk`（2026-08-07）约 79,963,941 bytes：
   4 个 ABI 的 `libmlkit_google_ocr_pipeline.so` 合计约 41,093,952 compressed bytes，19 个 DEX 合计约
   32,803,701 compressed bytes。该快照不是 2.2.6 结果，但说明“通用 APK/四 ABI”是体积与安装 IO 的首要
   测量项；若要拆 ABI，必须同时设计 dev 更新源的架构选择，不能只开一个 Gradle split。

8. **P1：Compose compiler reports 已开启，但当前输出不可作为全量稳定性证据。**
   配置把所有变体写入同一个 `app/build/compose_reports` (`app/build.gradle:137-144`)。本次现有输出的
   `app-classes.txt` 只有 `RoleplayViewModelTest` (`app/build/compose_reports/app-classes.txt:1-5`)，
   `app-composables.csv` 只有表头 (`app/build/compose_reports/app-composables.csv:1`)，最近的
   `debug/app-module.json` 仅统计 13 个 Composable (`app/build/compose_reports/debug/app-module.json:2-19`)，
   而旧的 `baseline/app-module.json`（2026-06-14）统计 3,274 个，说明增量任务/变体共用目录会覆盖或产生
   部分报告。CI 没有上传这些文件 (`.github/workflows/android-quality.yml:31-38`)。应在固定变体、干净/可重复编译
   后归档原始 reports，并检查报告是否覆盖全模块。

9. **P1：AndroidX Startup/WorkManager/ProfileInstaller 的前置初始化未被单独量化。**
   当前 debug 合并 Manifest 在 `InitializationProvider` 下注册 WorkManager、EmojiCompat、Lifecycle、
   OkHttp Platform 和 ProfileInstaller (`app/build/intermediates/merged_manifest/debug/processDebugMainManifest/AndroidManifest.xml:109-127`)；
   应用源 Manifest 没有自定义初始化器 (`app/src/main/AndroidManifest.xml:16-41`)。这些 provider 在
   `Application.onCreate()` 前运行，不能靠 AppLogger/StrictMode 的 `onCreate` 之后日志完整解释。ProfileInstaller
   依赖本身已固定为 1.4.1 (`gradle/libs.versions.toml:25-27`、`app/build.gradle:225`)；需要在 trace 中确认它与
   WorkManager/OkHttp 初始化是否占用首帧预算。

10. **P1：缺少应用运行时崩溃、ANR 和帧级可观测性。**
    `AppLogger` 默认只写 Logcat，虽保留可替换 `sink`，但注释明确 Crashlytics/自建上报仍是未来能力
    (`app/src/main/java/com/example/myapplication/system/logging/AppLogger.kt:7-23`)，默认 sink 仍是 Logcat
    (`AppLogger.kt:80-93`)。对 `StrictMode`、`ApplicationExitInfo`、`getHistoricalProcessExitReasons`、
    Crashlytics、Sentry、`setDefaultUncaughtExceptionHandler`、`android.os.Trace`/`androidx.tracing` 的源码检索均无
    命中。当前没有应用级崩溃持久化、ANR 归因、trace section 或字段级 jank 采集。

11. **P1：构建工具链有可重复性和内存风险，且必须与应用崩溃分开统计。**
    Gradle 配置开启 configuration cache 与 parallel，但只给 daemon 2 GiB heap，未发现
    `org.gradle.caching=true` (`gradle.properties:7-16`)；wrapper 为 Gradle 9.3.1
    (`gradle/wrapper/gradle-wrapper.properties:4-7`)。工作区在 2026-08-07 同时留下三份 Gradle daemon
    `hs_err`：均为 native `Out of Memory Error` (`hs_err_pid36896.log:21`、`hs_err_pid40276.log:21`、
    `hs_err_pid40520.log:21`)，命令行均显示 `GradleDaemon 9.3.1 -Xmx2048m` (`hs_err_pid36896.log:30`)，
    且本地 JRE 是 Temurin 21 (`hs_err_pid36896.log:23`)。CI workflow 只声明 setup JDK 17
    (`.github/workflows/android-quality.yml:18-23`)，但仓库的 `gradle/gradle-daemon-jvm.properties:1-12`
    又要求 daemon toolchain 21；CI 没有 `gradlew --version` 输出，实际 daemon JDK 未被证明一致。先做内存/并发
    A/B 和工具链锁定，再讨论调高 heap；不能把这三份 JVM 崩溃误报为应用 Crash。

12. **P1：CI/现有测试不能证明性能或启动稳定性。**
    `.github/workflows/android-quality.yml:31-38` 只运行 `assembleDebug`、JVM `testDebugUnitTest` 和
    `lintDebug`；没有连接设备、baseline 生成、宏基准、R8/release 或 Compose report artifact。源码测试虽有
    169 个 JVM 文件、15 个 AndroidTest 文件，但在测试源中没有 `ChatApplication`、`AppGraph`、
    `launchStartupTasks`、`MomentsAutoGenerationWorker`、`StrictMode`、`ApplicationExitInfo` 或
    `reportFullyDrawn` 命中。现有 Room 测试验证 schema/迁移正确性 (`ChatDatabaseMigrationTest.kt:26-47`、
    `:241-286`)，不测迁移耗时；当前没有可证明首帧、ANR、jank 或后台污染的自动化门禁。

13. **P1：版本记录仍有 2.2.5/2.2.6 漂移。**
    任务元数据已标 `release_version: 2.2.6-dev` (`.trellis/tasks/08-07-app-performance-optimization/task.json:26-27`)，
    但 PRD 仍写“目标版本待定/不阻塞 2.2.5-dev” (`prd.md:5-10、:80`)，仓库构建与发布文档仍是 2.2.5：
    `gradle.properties:21-23`、`CHANGELOG.md:22-45`、`版本功能添加/优化.md:3-30`、
    `docs/版本策略.md:5、:68`、`docs/发布候选记录.md:6-25`、`docs/updates/dev.json:4-7`。
    本次只记录，不改版本文件；实现前必须先以 2.2.6-dev 为唯一基线，避免把性能结果挂到错误包名/版本号。

### Files found（关键路径）

- `baselineprofile/build.gradle`：`com.android.test`、minSdk 28、targetAppId、connected-device 开关。
- `baselineprofile/src/main/.../StartupBenchmarks.kt`：两种编译模式的冷启动宏基准。
- `baselineprofile/src/main/.../BaselineProfileGenerator.kt`：当前启动/历史/设置 profile 路径。
- `app/build.gradle`：变体、Profile 合并、Compose reports、R8、依赖和 APK 命名。
- `app/proguard-rules.pro`：Gson/R8 keep 规则。
- `app/src/main/.../ChatApplication.kt`、`MainActivity.kt`、`ui/AppRoot.kt`：应用入口与门禁/首屏顺序。
- `app/src/main/.../di/AppGraph.kt`：Room、设置、仓储与启动任务装配。
- `app/src/main/.../data/local/AppSettingsStore.kt`、`SecureValueStore.kt`：重复设置映射与 Keystore IO。
- `app/src/main/.../data/repository/AppUpdateRepository.kt`、`system/moments/MomentsAutoGenerationWorker.kt`：联网更新和后台周期任务。
- `app/src/main/.../viewmodel/RoleplayViewModel.kt`、`RoleplayObservationSupport.kt`：RP 起点的观察 fan-out。
- `app/build/intermediates/merged_manifest/debug/.../AndroidManifest.xml`：实际合并的 AndroidX Startup provider。
- `app/src/main/generated/baselineProfiles`：当前为空；`app/build/intermediates/*art_profile*` 为旧构建产物。
- `app/build/compose_reports`：当前/旧变体 Compose metrics；输出未归档到 CI。
- `.github/workflows/android-quality.yml`：当前 CI 质量门禁。
- `gradle.properties`、`gradle/gradle-daemon-jvm.properties`、`gradle/wrapper/gradle-wrapper.properties`：Gradle 内存、并发、daemon JDK、wrapper。
- `hs_err_pid36896.log`、`hs_err_pid40276.log`、`hs_err_pid40520.log`：本地 Gradle daemon native OOM 证据（不是应用崩溃日志）。
- `CHANGELOG.md`、`版本功能添加/优化.md`、`docs/版本策略.md`、`docs/发布候选记录.md`、`docs/updates/dev.json`：当前仍记录 2.2.5-dev。

## Measurement gaps and decisions

### 需要 lucky 在进入实现前确认的问题

1. **测量范围**：2.2.6-dev 是否同时把 APK 体积/安装 IO/ABI 拆分纳入性能目标？当前 41 MB
   native OCR 库来自四 ABI；若纳入，更新源必须按设备 ABI 选择包。
2. **基准隔离授权**：是否允许仅在 `baseline` 渠道、收到 benchmark 指令时关闭自动更新检查、
   Moments 周期 Worker 和远程 telemetry？不隔离就无法满足“测量期间无网络”。
3. **工具链冻结**：当前 pinned `benchmark-macro-junit4`/Baseline Profile 为 `1.5.0-alpha03`
   (`gradle/libs.versions.toml:25-27`)，Google Maven 在 2026-08-11 已有 `1.5.0-beta01`；是冻结
   alpha03 先取基线，还是先升级到 beta 再全程固定？不得在前后对比中途换版本。
4. **启动目标**：首个可见门禁、合规后 RP 首页，还是“RP 消息/普通聊天列表已加载”作为正式
   `startupReady`？建议同时保留 first-run gate 与 returning-user core 两套，且实现 TTFD/ready 语义。
5. **设备策略**：是否有一台固定物理 Pixel/API 35/36 设备作为阈值设备？没有时只能做 emulator
   功能 smoke，不能把 emulator jank 数字当性能结论。
6. **崩溃/ANR 数据边界**：是否允许远程 Crashlytics/Sentry 类 SDK？若不允许，建议 2.2.6 只做
   Android 11+ `ApplicationExitInfo` 的脱敏本地摘要 + 可选导出；若需要跨设备聚合，再单独立项并明确
   用户同意、保留期、符号表上传和 baseline 渠道禁用规则。
7. **构建 JDK**：仓库 daemon criteria 要求 JDK 21，而 CI step 写 JDK 17；应选择一个并在 CI 输出
   `gradlew --version` 证明，不要让构建/Compose 指标跨 JDK 比较。

### 当前无法证明的指标

- 首帧以外的 TTFD、RP/聊天列表 `frameOverrunMs` P50/P90/P95。
- 应用自有 Baseline Profile 命中率、ProfileInstaller 实际安装状态、R8 后启动差异。
- 应用启动期间主线程耗时分解（settingsFlow 解密、AppGraph 装配、Room 首次打开、Compose 首次组合）。
- 自动更新/WorkManager/AndroidX Startup provider 对首帧的独立耗时。
- 应用崩溃率、ANR 率、上一进程退出原因与现场 trace 的持久化趋势。
- Gradle clean/warm build 的耗时、峰值 RSS、configuration-cache 命中率和 OOM 复现率。

## Recommended baseline matrix

所有 case 都应在同一物理设备、同一刷新率/温控状态、同一 APK/commit 下运行；fixture 写入、
合规确认、更新状态清理、滚动位置恢复必须在 `measureBlock` 外完成。

| Case | 准备状态与动作 | 指标/模式 | 次数与闸门 |
| --- | --- | --- | --- |
| S0 first-run | 清空数据、未确认条款，只到合规 ready | StartupTiming（None/Baseline；可加 Full 诊断） | 冷启动 10；等待失败即失败 |
| S1 returning core | 固定合规状态、空/小数据库，进入 RP 起点并等数据 ready | StartupTiming + TTFD | 每模式 10；Baseline 中位数不慢于 None |
| S2 populated | 80–120 条普通聊天 + 80–120 条 RP 消息，固定场景/助手 | StartupTiming + Perfetto | 每模式 10；保留原始输出 |
| S3 upgrade | 从受支持旧 schema/设置快照启动到当前版本 | StartupTiming + migration trace | 5–10；单列迁移成本，不与 S1 混淆 |
| F1 chat scroll | 普通聊天固定列表，底部→顶部→底部轨迹 | FrameTiming（主线程/overrun） | ≥5；报告 P50/P90/P95 |
| F2 RP scroll | RP 固定列表，同速上下滚动 | FrameTiming | ≥5；P95 改善目标 ≥15%（不可判定如实报告） |
| F3 RP stream replay | baseline-only 本地固定 chunk，按现有 48 ms pump 重放，无网络 | FrameTiming + 自定义 trace section | ≥5；验证 item key 不因正文变化而变 |
| P1 profile path | 启动、RP、普通聊天历史、设置、角色音色 Sheet；每一步强断言 | BaselineProfileRule | 生成后源码/合并 profile 必须含 app namespace 规则 |
| B1 build/size | clean 与 warm 各 3 次，固定 daemon/JDK；收集 debug/baseline 与 release-like（需授权） | Gradle profile、APK/DEX/native/resource 体积 | 记录 cache 命中、峰值内存、原始报告 |

### 可观测性边界建议

- **基准专用**：增加稳定 ready 标记、`androidx.tracing` section（settings decode、DB open、AppGraph
  resolve、首个消息映射），并让宏基准保存原始 JSON/Perfetto；这些不应写入用户数据。
- **StrictMode**：只在 debug/独立诊断变体启用 `penaltyLog`，不要在正式基准变体启用会改变时间的
  `penaltyDeath`；`Application.onCreate` 之后才可观察，provider 初始化仍须依靠 Perfetto。
- **本地崩溃/ANR MVP（默认不联网）**：Android 11+ 下一次启动读取 `ApplicationExitInfo`，仅保留
  reason、时间、版本、PSS/RSS 和脱敏摘要；API 26–29 如确有需要，再加一个保留原 handler 的
  uncaught-exception 最小记录，写入 `noBackupFilesDir`、限量轮转并提供用户主动导出。不要自动上传
  聊天文本、Key、完整 DataStore/Room 行或 ANR trace。
- **远程 SDK（需单独确认）**：Crashlytics/Sentry 能提供跨设备聚合、ANR/native 崩溃和符号化，但会引入
  新依赖/初始化器、网络与隐私/保留期配置；若批准，baseline 渠道必须禁用采集或使用离线 sink，且 CI
  需上传 mapping/native symbols。它不应作为本专项默认隐含工作。
- **Gradle/JVM 稳定性**：把 OOM 作为构建门禁单独记录（JDK、daemon heap、workers、系统可用内存、任务名），
  与应用 crash/ANR dashboard 分开；先做 `parallel`/workers 与 JDK 17/21 的 A/B，再调整 2 GiB heap。

## Validation commands and device dependencies

### 只读设备前置检查（执行前记录到原始结果）

```powershell
adb devices -l
adb shell getprop ro.product.model
adb shell getprop ro.build.version.sdk
adb shell getprop ro.build.version.release
adb shell dumpsys battery
adb shell dumpsys thermalservice
adb shell settings get system peak_refresh_rate
adb shell settings get system min_refresh_rate
adb shell settings get global window_animation_scale
adb shell settings get global transition_animation_scale
adb shell settings get global animator_duration_scale
```

`baselineprofile` 的 `minSdk` 是 28 (`baselineprofile/build.gradle:17-22`)，因此需要 API 28+；阈值结果
应使用固定物理设备（建议单一 Pixel/API 35 或 36、固定 60/120 Hz、温控正常、关闭省电模式），
模拟器只做功能 smoke。当前会话没有在线设备，未取得任何真实宏基准/帧指标。

### 未来窄测与质量门禁

```powershell
.\gradlew.bat :baselineprofile:connectedBaselineAndroidTest --console=plain
.\gradlew.bat app:generateBaselineProfile --console=plain
.\gradlew.bat assembleBaseline --console=plain
.\gradlew.bat :app:testDebugUnitTest --console=plain
.\gradlew.bat :app:compileDebugAndroidTestKotlin --console=plain
.\gradlew.bat lintDebug --console=plain
.\gradlew.bat assembleDebug --console=plain
```

有独立 benchmark 类后，用 instrumentation class/method 过滤器先跑 S0/F1/F2/F3，再跑完整
`connectedBaselineAndroidTest`；每个 JVM 单测命令按项目约定最多等待 60 秒。Profile 生成后必须
同时检查 `app/src/main/generated/baselineProfiles`、合并 text profile 中的 `com/example/myapplication`
规则和安装后的 dexopt/ProfileInstaller 结果。release/R8 构建仅在 lucky 明确授权且签名/非发布验证
边界确认后执行，不能把旧 release APK 当作本版本结果。

## External references (docs / versions)

- Android Baseline Profiles overview：<https://developer.android.com/topic/performance/baselineprofiles/overview>
- Macrobenchmark overview/setup：<https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview>
- Macrobenchmark startup metrics：<https://developer.android.com/topic/performance/vitals/launch-time>
- Compose stability/compiler reports：<https://developer.android.com/develop/ui/compose/performance/stability/diagnose>
- ProfileInstaller debugging：<https://developer.android.com/topic/performance/baselineprofiles/debug-baseline-profiles>
- StrictMode：<https://developer.android.com/reference/android/os/StrictMode>
- ANR/Android vitals：<https://developer.android.com/topic/performance/vitals/anr>
- `ApplicationExitInfo`：<https://developer.android.com/reference/android/app/ApplicationExitInfo>
- R8/resource shrinking：<https://developer.android.com/topic/performance/app-optimization/enable-app-optimization>
- Gradle configuration/build cache：<https://docs.gradle.org/current/userguide/configuration_cache.html>、<https://docs.gradle.org/current/userguide/build_cache.html>
- Google Maven 元数据（2026-08-11 读取）：`benchmark-macro-junit4`/Baseline Profile 最新为 `1.5.0-beta01`，仓库固定 `1.5.0-alpha03` (`gradle/libs.versions.toml:25-27`)；`profileinstaller` 最新为 `1.4.1`，与仓库一致。

## Related specs

- `.trellis/spec/backend/directory-structure.md:11-16、:40-44`：入口必须轻量，AppGraph 负责启动任务，baselineprofile 只放宏基准/Profile。
- `.trellis/spec/backend/quality-guidelines.md:67-72、:73-102`：启动/关键 Compose 页面需更新 profile；baseline、设备测试、Lint/构建命令和 60 秒超时规则。
- `.trellis/spec/backend/logging-guidelines.md:5-15、:40-65`：统一使用 AppLogger，项目没有 structured logging/Crashlytics 依赖，日志不得替代业务错误。
- `.trellis/spec/backend/compliance-guidelines.md:44-51、:62-67`：AppRoot 合规前不建导航/更新检查，Worker 每次执行重新守卫。
- `.trellis/spec/backend/database-guidelines.md:17-22、:79-110`：唯一 ChatDatabase、迁移注册/schema/测试契约；当前测试验证正确性，不是耗时。
- `.trellis/spec/guides/cross-layer-thinking-guide.md:3-20、:54-60、:113-120`：跨层基准需记录线程、失败、取消、资源与验证边界。

## Caveats / Not Found

- 本次没有执行 Gradle、AndroidTest、宏基准、Profile 生成或网络发布；研究角色只读，未修改产品代码。
- `app/build` 下的 release/baseline/intermediates 与 Compose report 有 2026-06 至 2026-08-07 的旧时间戳，
  只能作为“已有产物形态”观察，不能当作 2.2.6-dev 优化前数值。
- `app/src/main/generated/baselineProfiles` 为空、`FrameTimingMetric`/`MemoryUsageMetric`/
  `TraceSectionMetric`/`android.os.Trace`/`androidx.tracing`/`reportFullyDrawn`/StrictMode/
  ApplicationExitInfo/Crashlytics/Sentry 等检索均无源码命中；负向结论以本次检索范围为准。
- `hs_err_pid*.log` 记录的是 Gradle/JVM native OOM，不是 Narra 进程的 Java crash、ANR 或用户现场；
  应独立归档并与应用运行时稳定性报表分开。
- 当前会话无在线设备，无法读取 logcat/DropBox/ANR buffer，也无法验证 ProfileInstaller 在设备上的实际安装状态。
- 任务元数据已指定 `2.2.6-dev`，但 PRD/发布文档仍显示 2.2.5；本报告不替代版本文档同步，也不主动改动它们。
