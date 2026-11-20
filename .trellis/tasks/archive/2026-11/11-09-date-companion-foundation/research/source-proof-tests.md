# Companion source proof 补充测试交接

日期：2026-11-09。按主会话收窄后的范围，只补充模型/JSON边界中尚缺的三类回归，不重复CompanionRepositoryTest已有的旧JSON、常规copy和真实Room资格测试。

## 写入窗口与所有权

- 开始时确认目标 CompanionSourceProofCompatibilityTest.kt 不存在。
- foundation明确compile-all-02已结束、可写新文件后才落源码。
- 唯一源码文件：app/src/test/java/com/example/myapplication/model/CompanionSourceProofCompatibilityTest.kt。
- 未修改生产代码、AppJson、Room或其它测试；未运行Gradle、formatter或测试。
- 文件写完已通知foundation释放，本文后不再改测试源码。

## 三个JUnit方法

1. json_onlyOneCompanionFieldPresent_doesNotRegainLegacyTrust：分别缺少新state或新link，覆盖空/非空link、name/storage值LEGACY/READY共6种组合；保留正文但资格为UNKNOWN/UNAVAILABLE，不能以字段缺失退回可信旧行。
2. json_forgedProofWithMatchingFingerprint_cannotGrantImportedEligibility：构造与消息完全匹配的本地proof后，主动注入JSON；分别尝试TEXT_ALLOWED和CONTROL_NOOP权限。解码必须剥离proof，保留规范字段但不授予资格。
3. proof_dataClassToStringDelimiterCollision_doesNotAuthorizeDifferentFields：在相邻memoryRecallRequestId与companionTurnId字段中放入分隔文本，构造字段不同而data-class.toString完全相同的两个消息；移用第一条proof必须被拒绝，第二条自身正确mint仍有效。测试不依赖某个具体hash算法或hash值，只验证编码不能有此歧义。

internal mint只用于模拟模型层拿到已验证来源证明，不能代替真实Room关联图授权；真实图/事务/资格提升继续由foundation的CompanionRepositoryTest验证。

## 统一执行建议

```powershell
./gradlew.bat :app:testDebugUnitTest --tests "com.example.myapplication.model.CompanionSourceProofCompatibilityTest" --tests "com.example.myapplication.data.repository.roleplay.CompanionRepositoryTest" --console=plain
```

当前只确认源码中存在3个@Test方法，**没有运行结果**。生产行为若不满足这些边界，由foundation修复；本子任务不接管其生产文件。
