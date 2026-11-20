# D6 资料与装扮独立检查

2026-11-20，原生 `trellis-check /root/d6_transfer_check`。恢复现有已批准 D6，读取完整 hook、任务 JSONL/PRD/design/implement、父 §9–10、交接及持久化/资料/错误规范。本记录是源码检查及修复交接，**不是编译、JVM、Lint 或设备通过回执**。

## 已修复发现

1. `CompanionAppearanceTransferCodec.kt` 的 PNG 验证原先只核对块名、CRC 与尺寸。合法 CRC 下的错误 `sRGB`/`pHYs` 等长度、损坏压缩流、错误行数和过滤字节可进入预览。现在校验白名单块的格式、重复/顺序、位深/色彩/压缩/隔行声明，并以有界行缓冲验证 deflate 像素流与完整结束；不分配整张 Bitmap，兼容有效调色板及 Adam7 数据。实际 Android 解码探针仍保留在文件发布阶段。
2. `ContextTransferRecoveryRepository.kt` 的冻结计划同时在 `bundle` 与 `targets.companion` 嵌入相同 Base64 图片，使仍在包/图片上限内的导入过早超过计划上限。`targets` 现在仅保存本机 CAS 快照，源偏好统一从冻结 `bundle` 按角色 ID 读取。公开 v1、私人 v3 和本机计划 v1 保持；旧本机 v1 计划的冗余字段可被 Gson 忽略，源及实际设置动作未改变。
3. `CompanionAppearanceTransferViewModel.kt` 更换预览失败后仍保留已取消计划的旧预览凭证；另有部分完成计划被无条件取消，以及恢复旧计划时清掉另一份新预览的问题。现在仅取消 PLANNED 的旧预览，清除对应已失效凭证，保留已完成阶段的恢复列表；成功恢复只清同 operationId 的预览。

## 实际修改路径

源码根分别为 `app/src/main/java/com/example/myapplication/` 与 `app/src/test/java/com/example/myapplication/`。

- main：`data/repository/context/CompanionAppearanceTransferCodec.kt`、`ContextTransferRecoveryRepository.kt`；`viewmodel/CompanionAppearanceTransferViewModel.kt`。
- test：`data/repository/context/CompanionAppearanceTransferCodecTest.kt`、`ContextTransferRecoveryRepositoryTest.kt`；`testutil/CompanionTransferTestSupport.kt`；新增 `viewmodel/CompanionAppearanceTransferViewModelTest.kt`。

没有修改 AppGraph/UI、DataStore 写合同、迁移/schema、S3 资产、旧 PNG、版本/更新参数或其它执行者的源码。全部源码所有权已释放给主会话。

## 新验证输入与尚未执行项

新增 **6 个** JVM 方法，数量来自源码声明，尚未运行：

- `CompanionAppearanceTransferCodecTest` 增加 3：畸形白名单块/夹带文本、坏压缩/过滤/像素长度、有效调色板/隔行兼容。
- `ContextTransferRecoveryRepositoryTest` 增加 1：约 10 MB 的合成合法包，冻结文件只保存一份图片内容，真实文件型 Room 重开后完成发布/挂接。
- `CompanionAppearanceTransferViewModelTest` 新增 2：替换预览失败不保留已取消凭证、部分完成计划仍可恢复且不清其它预览。

首轮相关 selector：上述三个测试类；恢复仓储原 12 方法、codec 原 7 方法与新方法一并执行。既有 `AppSettingsStoreContextTransferTest`、`ContextTransferRecoveryViewModelTest`、迁移/S3 守卫及两个原生方法仍是交接要求。本代理未运行 Gradle、formatter、截图或设备，全部由主会话统一执行并绑定最终源码。

原 `settingReceiptBeforeRoomAck_survivesBothReopensWithoutOverwritingUser` 还补入真实旧 v1 冻结计划夹具（targets 内保留冗余 companion），继续验证旧格式关闭重开、设置已成功但 Room 未确认时的免费补回执与后改保值；未删减原断言。

静态核对：本代理所改 7 条源码/测试路径的 `git diff --check` exit 0；核对全部 `settingsTarget` 调用及角色来源读取，无遗留 `target.companion` 调用。旧 schema 55/56 与 S3 v55、v56 的 SHA-256 重新计算，分别匹配既有交接：

- 55：`5b33ea781a198962487b9324f054d2a243e0903f49c65ce00bf93b853b696033`。
- 56：`d5e2dff049818be64c7ab4b3bf2b767b99f1bc0546756f10a333ca05e903eab7`。
- S3 v55：`b98c6e1f893c19c5095e2e6f18b45508d600ef593ce3679a67d9d462e01721ad`。
- S3 v56：`57da481f5082135f40b13bdbc4ac722c5b217e02030f3f1e485d84b92e59af51`。

其余 transfer 合同已沿真实路径核对：公开字段和素材映射、私人缺域保值/未来版本拒绝、Room 与阶段回执同事务、设置单目标 CAS/幂等、文件 hash 发布及后改不覆盖、头像冻结/重开、记忆外层回滚/提交后通知、56→57 新表/注册与 S3 相邻升级。未发现当前可确认但仍未修复的 transfer 代码问题；最终结论待真实检查结果。

## 主会话文档与验收输入

持久化规范的当前版本需随真实 KSP 结果同步为 57，记忆资料规范同步实际私人 v3。按最终证据记录 PNG 校验、源域单份冻结和预览/恢复操作 ID 边界；不把本记录或测试文件存在当作 AC 通过。

真实模型/物理设备声音、旧视觉接纳仍未验证；没有操作真实用户数据、凭据或付费接口，没有提交、推送、发布或归档。

## 首次编译失败的最小修复

主会话 `validation/01-main-compile/raw.log` 的实际 `:app:compileDebugKotlin` 退出 1，唯一 Kotlin 诊断为 `preferredMode` 调用将 reified `T` 推断成可空 `CompanionDisplayMode?`，不满足 `Enum<T>`。2026-11-20 重新接手仅修复该调用为 `optionalEnum<CompanionDisplayMode>("preferredMode")`；缺字段/null 仍返回 null，全部枚举和图片校验保持。没有增加测试或重跑 Gradle。修复后定向 `git diff --check` exit 0，文件已立即释放，编译结果等待主会话定向复验。

## 10-targeted-jvm 的七项实际失败修复

主会话提供的该轮原始 XML 为 146 项、137 通过、9 失败、源码稳定。本代理只接手其中 7 项 transfer 失败；两项历史失败由另一检查者负责。原失败报告保留，没有将失败修改成通过回执。

- 逐项读取原 `4bceccfb1f397a3d.xml` / `26650123da553f1e.xml` 与真实断言位置。`AppSettingsStore.settingsFlow` 是 eager、replay=1 的共享 Flow，DataStore 保存完成后首个回放仍可能是保存前值。夹具 `initialize` 现在等待 Alice/Bob 完整角色与选择状态；相关角色偏好/导入/用户后改读取按真实角色、revision、zone、写法/素材及头像字段确认。保存返回同时要求 Saved，保留原“旧包不覆盖偏好”“部分成功不重复覆盖”“取消零业务写入”“头像不重做”的全部断言；没有增加睡眠、超时或改 expected 值迎合错误。
- `publicExport` 夹具原来把 Windows 私有绝对路径放进图片 `name`，在生产名称上限处被正确拒绝。仅将名称改为合法的“合成立绘一”；实际私有路径仍在 source.path、头像和原 PNG 元数据中，原白名单/秘密哨兵/去元数据/ID 映射/共享像素断言保留，生产校验未放宽。
- `outerRoomFailure` 的 DTO `messageId` 是非空 String，原 `== null` 恒假。现在先要求 sources 非空、DTO messageId/conversationId 为空串，再逐个 source.id 查询真实 `memory_source_evidence` 并要求 messageId 为 SQLite NULL、conversationId 为空串及唯一行；原 v3 出处、外层回滚、免费同计划重试、无会话和回执计数断言保持。
- 公开 codec 的严格 JsonReader 在畸形/尾随 JSON 边界可能抛 IOException 子类；公开 decode 现在仅在解析边界将其保留为 cause 的安全 IllegalArgumentException，仍整包拒绝。负例字段通过 JSON AST 明确修改并断言结构实际改变；重复字段/尾随对象仍以原始 JSON 输入验证。原未知版本、私有路径、声音样本及旧版本拒绝需求未降低。

本轮仅改 4 个源码/测试路径：`CompanionAppearanceTransferCodec.kt`、`CompanionAppearanceTransferCodecTest.kt`、`ContextTransferRecoveryRepositoryTest.kt`、`CompanionTransferTestSupport.kt`。没有新增 @Test 方法，没有修改历史/UI/其它执行者的文件。修复后定向 `git diff --check` exit 0；尚待主会话真实复验，本代理仍未运行 Gradle/formatter/设备。

最小充分类级 selector 为 `com.example.myapplication.data.repository.context.CompanionAppearanceTransferCodecTest`（当前声明10方法）和 `com.example.myapplication.data.repository.context.ContextTransferRecoveryRepositoryTest`（当前声明13方法）。两类覆盖已修7项、同一夹具的全部读取，以及另外3个改为有效AST负例的既有方法和旧v1回执重开方法；不要累加旧146轮次。若只定位原失败，原7个method名称均保持不变，可从该轮原XML直接筛选。
