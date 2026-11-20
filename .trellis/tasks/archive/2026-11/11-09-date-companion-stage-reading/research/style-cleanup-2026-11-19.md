# D4 定向风格清理（2026-11-19）

`/root/d4_recovered_check`按主会话明确分配直接自修，仅改三个指定源码文件。版本仍为2.4.1。修改后已冻结，等待主会话统一formatter、构建和设备复验；本代理未运行Gradle、Lint、设备或付费服务。

## 修复与原始告警

原始告警来自 `resume-2026-11-18/d4-recovered-ui-engineering-16b/lint-results-debug.xml`：

| 文件 | 原位置/告警 | 最小修改 |
| --- | --- | --- |
| `AndroidCompanionResourcesRepository.kt` | 41、121，`UseKtx`（String.toUri） | 两处`Uri.parse(uri)`改为`uri.toUri()`，增加`androidx.core.net.toUri` import。已有core-ktx依赖无需变动；`Uri.fromFile`继续保留原行为。 |
| `CompanionReadingScreenTest.kt` | 497、511，`ComposableNaming` | 私有`reader`/`content`改为`Reader`/`Content`，同步本文件调用。 |
| `CompanionVisualSettingsScreenTest.kt` | 426，`ComposableNaming` | 私有`screen`改为`Screen`，同步本文件调用。 |

Stage helper命名和新增快捷Dialog窗口高度仍由Stage实施者处理，未交叉写入。旧`RoleplayDialoguePanel.modifier`位置提示未改；fixture、Stage、父/spec/version、旧PNG/schema及其它源码未由本次清理修改。

## 定向验证与冻结

修改前快照沿用 `recover-01a1ee8a/style-before/`，已再次核对三文件与17冻结点相同。最小差异和指纹在：

- `recover-01a1ee8a/style-cleanup-2026-11-19.diff`
- `recover-01a1ee8a/style-cleanup-2026-11-19.json`

独立逆移除上述URI/import和helper大小写修改后，三个文件都与修改前原始字节完全相同。Reader11、Visual8个`@Test`方法名称/数量不变，所有行为、原文和强断言随原始字节证明保留；定向扫描已无旧helper调用。主仓储的公共API及解析行为未变。

formatter前SHA-256：

| 文件 | 修改后SHA-256 |
| --- | --- |
| AndroidCompanionResourcesRepository.kt | `e499dab11f29a7028c40300e75727f2c205c417d0a3550ab920ce0cd02b51a14` |
| CompanionReadingScreenTest.kt | `07a46087847389b717e8c0dca73ca2e8be77d4c73ece4ec2dbe1694c157da01d` |
| CompanionVisualSettingsScreenTest.kt | `567b8af9003491d739507ad6b48de0294755c74f38181e466872d4d1076ebc7f` |

实际Lint/TypeCheck/设备结果待主会话在Stage新修复和统一formatter后执行。17的Reader/Visual通过可说明清理前行为，不能直接替代当前新SHA的编译或整个D4最终验收。
