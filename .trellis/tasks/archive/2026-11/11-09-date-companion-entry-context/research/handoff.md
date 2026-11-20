# D2 验收与 D3 交接

2026-11-11：D2工程验收通过，允许D3接入。原会话 `01a1c896-e455-7b41-ac2f-61360ad0ba79` 在启动设备后因服务额度不足中断；本次核对冻结源码和APK后完成剩余验收。父任务继续D3–D6，提交与发布仍按整轮收尾约定处理。

## 已验证范围

- 现有线下入口的三开场、预览接受、DIRECT零请求、持久选择、旧路由恢复及准确返回原线上会话。
- INPUT/CONTINUE/OPENING、最新答重生成、免费补存/接受恢复；旧A结果在原线上或新B场次的独立补保存入口。
- 同角色/persona日常原文授权、独立/群私有/断点/空集合隔离、来源修订与发送前最后守卫。
- 每请求召回、世界书role/depth顺序、内置预设局部适配、有效身份说明和时间/角色时区快照。
- 实际Screen与生产路由wrapper的Back/IME、小屏/大字体、等待与补存、图片持久化回执。

## 证据与计数

- [独立代码检查](check-final.md)、[检查汇总](check-final-validation.json)：作者与检查者XML按class+method合并，**397个唯一JVM用例、28类**通过；最后检查者运行109项。**35项旧截图**verify通过，未record。Debug、JVM、AndroidTest及baseline编译、Lint、双APK构建通过。
- [原生设备结果](device-final-validation.json)：Android 36 `Narra_Memory_V24`，**21个唯一用例、4类**通过。首轮19过/2失败，只修两测试文件的完整文本断言，随后2项定向复验通过；主APK未变。原始失败、修复差异和通过日志全部保留，不宣称单次21项全绿。
- [原生断言修复](native-check-handoff.md)：2文件、3处断言，AndroidTest重新编译、Lint与测试APK构建通过；Lint为0 errors、56 warnings。
- [视觉检查](device-visual-review.md)：三张实际窗口候选已查看，草稿/按钮可达；没有接纳或替换旧截图基准。
- 最终1396项源码与 `native-check-source-hashes.json` 相同；SHA-256为 `c329fefa79673fbd3b04cb270acaf981d9650bee185a1abf3a7be4d4bed905e3`。273项旧schema/截图/参考保护资料逐字节未变，schema仍为55。模拟器及本轮构建进程已正常退出。

| 本地验证产物 | SHA-256 |
| --- | --- |
| `app/build/outputs/apk/debug/Narra-v2.3.0-dev-20300-dev.apk` | `d74b4822fd51de451667b8a85f180f29bef454597331eae6ec435c254b1a7204` |
| `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | `653ea58d00dde421cb6a60a22e9e7deec1a478bd919d82efee0487ac2d22c573` |

## D3接线合同

完整签名、错误与测试矩阵见 [陪伴入口与上下文规范](../../../../../spec/backend/companion-entry-context-guidelines.md)，D1来源与提交合同见 [可靠回合规范](../../../../../spec/backend/companion-foundation-guidelines.md)。

1. `CompanionCharacterSettings` / `CompanionSettingsPatch` 与 `updateCompanionSettings` 已建立按assistantId的独立映射、DataStore CAS、角色删除及未知高版本保护。D3在同一映射扩展文风/观测/模板，保留D2时区和旧角色整列表保存语义。
2. `CompanionRequestDescriptor` 在既有 `requestDescriptorJson` 保存settingsRevision/protocolVersion/promptVersion、授权scope、返回origin、选中entry、replyMessageId和timePolicy。D3应增加实际生成配置及稳定观测字段定义的兼容快照，只有revision不能解释旧标签。
3. `CompanionRequestCoordinator` / `CompanionPromptSupport` 承载逐请求准备和表现装饰。检索与世界书仍基于真实语义输入；表现规则不能进入原文授权、人物事实或覆盖内置预设局部适配。发送前来源/模型/设置守卫须保留。
4. D2的显式 `newAttempt` 会重冻合法来源和请求描述；D3新增表现快照时明确区分失败重试与用户重生成：重试保留原表现选择及细节hint，重生成才可采用新写法；不得通过放松来源/发送守卫来复用失效材料。
5. 原文来自 `CompanionGeneratedArtifact.rawReply`，提交时原样写content。D3按实际turn/selected attempt的版本解释投影，补齐统一raw accessor与各消费方，不把展示parts或去标签正文写回canonical，不改变memoryRevision。
6. `CompanionRouteContent` 是实际准备/活动/待补存装配点；D3设置入口复用现有角色与返回关系。`onSendWithInputSaved` 在输入持久化后才确认，迟到确认仅清匹配草稿。FREE SAVE、ACCEPT和明确GENERATE继续分开。

## 验收限制

原生导航验证的是实际Screen和生产wrapper装入合成NavHost；完整AppGraph入口故事归D6。没有调用真实付费模型/TTS，也没有验证物理设备体验。HTTP与配置测试证明请求边界，不证明模型文风或角色行为效果。D3的正文/观测、D4–D6的表现/声音/完整历史尚待各块实现。
