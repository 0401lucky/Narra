# D1 迁移测试与 S3 fixture 实施交接

日期：2026-11-09。归属 v2.4.1 / D1。生产 Entity、DDL、主库版本、新 schema55 生成与全部 Gradle/formatter 执行权归 foundation 代理；本代理只完成已分配的迁移验证与 S3 配套。

当前状态：代码与静态检查完成，等待统一 Kotlin 编译、JVM 与 Android Room 设备验证。没有把静态 SQLite 检查写成真实 Room 迁移测试通过。

## 改动文件

| 文件 | 改动 |
| --- | --- |
| app/src/androidTest/java/com/example/myapplication/data/local/chat/CompanionFoundationMigrationTest.kt | 新增4项固定54→latest真实Room设备测试，来源54有固定SHA和identity。 |
| app/src/androidTest/java/com/example/myapplication/data/local/chat/MemoryManagementMigrationTest.kt | 原固定53来源及SHA保持，仅目标改为CURRENT_VERSION并要求至少54，保证旧测试仍经过最新迁移链。 |
| app/src/test/java/com/example/myapplication/data/local/chat/ChatDatabaseMigrationRegistryTest.kt | 最新注册迁移断言指向MIGRATION_54_55，保留完整连续链守卫。 |
| app/src/main/java/com/example/myapplication/system/performance/BenchmarkContract.kt | S3来源版本更新为54，未改变其它fixture数量/策略。 |
| app/src/test/java/com/example/myapplication/system/performance/FixtureSpecTest.kt | S3来源断言对应54。 |
| app/src/baseline/java/com/example/myapplication/system/performance/LegacyUpgradeFixtureBuilder.kt | 明确测量54→55，使用固定54 SQL、identity和原始字节SHA，不预执行迁移。 |
| app/src/baseline/assets/benchmark/s3-schema-v54.sql | 从固定54导出的156条建表/索引/view/setup语句；新文件，原47–53 SQL不改。 |
| app/src/test/java/com/example/myapplication/system/performance/LegacyUpgradeFixtureContractTest.kt | 逐条核对固定54 schema与SQL、identity/SHA及源54→目标55，不移除版本守卫。 |
| .gitattributes | 经主会话授权，仅为固定54.json和新s3-schema-v54.sql增加精确路径text/eol=lf，保留原journal规则；不改全仓或全局Git配置。 |

旧48、49、50、51、52迁移测试本来就以CURRENT_VERSION为目标，本次未改其源或语义。生产Migration/Entity/ChatDatabase不由本代理修改。

## 新增设备用例

1. fixed54ToLatest_preservesMessagesAndMemoryGraphWithoutInventingEncounters
   - 从固定54创建真实旧库，在所有旧表的全部原列上做前后快照比较，并再次关闭/重开。
   - fixture包含完整长原文/尾部、parts/引用/推理/媒介/时间/修订、两个日常运行绑定、记忆多版本/来源、事件成员、人工认知/来源、已付费任务/请求状态、持久暂停、导入出处及回执。
   - 旧消息新增link为空、资格为legacy；三新表初始为空；迁移不制造新场次/来源/任务。
2. fixed54ToLatest_enforcesUniqueSlotsAndForeignKeysWhileAllowingNullReservations
   - 活动键唯一、多个NULL预留合法；同场ordinal、input/reply槽唯一。
   - 缺conversation/encounter/turn的写入必须被FK拒绝；正常身份/run/generation可持久读取。
3. fixed54ToLatest_cascadesOnlyOwnedAttemptTreeAndKeepsUnrelatedHistory
   - 分别检查删turn、删encounter、删conversation的三级级联。
   - 删除场次本身不通过反向FK删除原消息；其它会话与记忆引用图保持。
4. fixed54ToLatest_rollsBackWholeAttemptTreeWhenAConstraintFails
   - 在Room事务中先写encounter/turn/attempt，再制造唯一约束失败。
   - 三层新行整体回滚，旧表全量快照保持。

这些是数据库结构/迁移与事务测试。角色/运行的逻辑授权、expectedRevision、来源资格全路径及付费产物恢复仍由 foundation 的仓储/协调器测试验证；本测试不把数据库未声明的逻辑引用误称为FK。

## 已完成检查

- 固定54.json原始SHA：c6be6663d9fbaba9cb1403bdf2551f01be24352f0230449bc7e563c3cf069ad1。
- 固定54 Room identity：e9527aaf628fa664ca1c1f4a92af1b52。
- 新S3 SQL原始SHA：1e5d5b71c6134535f93d44d58317fb61208ad7483bbbe1962cb148e17bfbd102。
- 156条S3语句与固定schema逐项一致；builder的路径/源/目标/identity/SHA匹配。
- 从当前生产MIGRATION_54_55抽取13条静态SQL，在合成内存SQLite中执行默认值、8次预期约束拒绝、级联与事务回滚检查，通过。
- 按父execution-baseline比较51份原schema/旧S3 SQL，原始字节全部未改。
- 新测试21组旧表seed map字段都在固定54 schema中；4个测试名唯一。
- 源码/fixture无编码替换字符/行尾空格；定向git diff --check exit0。Git仅提示现有core.autocrlf对Kotlin/属性文件行尾的常规转换。
- git check-attr确认两个固定字节路径均为text=set、eol=lf；规则添加后再核对原始SHA，与上述值相同，没有改写54.json或SQL字节。
- 结构化证据：[migration-static-check.json](migration-static-check.json)。

没有运行Gradle、Kotlin编译、Android Lint、IDE formatter、模拟器或真实模型；没有创建/修改用户实际库，没有安装依赖或修改全局配置。

## 统一验证命令与交接

foundation在所有写入者释放后，至少运行这些真实选择器：

    ./gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.data.local.chat.ChatDatabaseMigrationRegistryTest" --tests "com.example.myapplication.system.performance.LegacyUpgradeFixtureContractTest" --tests "com.example.myapplication.system.performance.FixtureSpecTest" --console=plain

baseline-only builder不受普通debug编译覆盖，必须包含：

    ./gradlew.bat :app:compileDebugAndroidTestKotlin :app:compileBaselineKotlin --console=plain

设备端应包含：
- com.example.myapplication.data.local.chat.CompanionFoundationMigrationTest
- com.example.myapplication.data.local.chat.MemoryFoundationMigrationTest
- com.example.myapplication.data.local.chat.MemoryExtractionMigrationTest
- com.example.myapplication.data.local.chat.MemoryRecallMigrationTest
- com.example.myapplication.data.local.chat.MemoryEventMigrationTest
- com.example.myapplication.data.local.chat.MemoryKnowledgeMigrationTest
- com.example.myapplication.data.local.chat.MemoryManagementMigrationTest

主会话依据已构建APK的实际包名与已核验的隔离AVD定向运行，不猜测试包名、不触碰用户连接设备。新55 schema必须由foundation的KSP生成及Room校验，不能由本静态SQL检查代替。

## 限制与待收口

- 新设备测试尚未编译和实际运行；需要统一验证反馈，任何失败继续在对应所有权内修复。
- 旧固定53测试保留源哈希，但未来版本继续变动时仍升到latest；S3守卫则刻意只允许当前相邻54→55，未来D5再升库时必须再次维护。
- 固定54与新SQL当前均为LF；主会话已授权并完成精确路径eol=lf，避免后续Windows checkout转换破坏字节SHA。未对其他文件做换行归一化。
- 本代理现在释放上述源码/fixture写入权，可由foundation统一格式化、编译和验证；未提交、推送或发布。
