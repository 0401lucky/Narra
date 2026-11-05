# P05 独立检查结果（2026-10-23）

独立 `trellis-check` 已审查本批五个生产文件、截图宿主/登记、源码清单及全部P05行为测试，并回到实际NavGraph和SettingsViewModel核对数据流。范围内无遗留代码问题。

- 修正活动页标题单行省略，允许窄屏200%字体自然换行；场景副标题行为保持。
- 补充八页标题逐行完整性与已有立绘选择/清除同时出现时的名称、48dp命中回归。标题测量使用实际行边界和完整末尾，1px仅为浮点取整容差；截图比较仍为零像素阈值。
- 94% / 66%、最大480dp及Insets保持，13皮肤与用户圆角/宽度/padding/尾角/玻璃原值不取整；成功保存才返回/进入阅读，场景即时回调和模型选择时机保持。未改共享设置组件或持久化接口。

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| 最终相关JVM | 119/119，18 suites，0失败/错误/跳过，46秒 | check-behavior-final/、check-20261023-final-behavior-082614.log |
| 其中P05 | 无障碍8、皮肤/保存5、窗口9、配色1、窗口缺失负例1，共24项通过 | 同上JUnit XML |
| 源码守卫 / 共享回归 | 12 / 83项通过 | 同上JUnit XML |
| 最终lintDebug | 成功，0 errors / 55 warnings / 3 hints，3分42秒；数量与本轮首次Lint一致 | check-20261023-final-lint-082715.log |
| assembleDebug | 成功，30秒；dexBuilder/mergeProjectDex/package实际执行 | check-20261023-assemble-083115.log |
| compileDebugAndroidTestKotlin | 成功，7秒；KSP和Kotlin编译实际执行 | check-20261023-androidtest-compile-083159.log |

主会话另执行截图verify：111项中92旧图通过，18新候选仅缺原图失败，1计数守卫缺图失败；0错误/跳过。18对候选已冻结，92张已接纳PNG哈希不变。独立检查不接纳图片、不提交、不归档。

待人工视觉接纳后继续完整JVM及最终截图verify，再按具体提交审阅收尾。设备TalkBack、系统返回/窗外事件、IME/系统栏/实际玻璃、物理性能和新视觉远端CI未取得结果，不能以本轮编译或宿主测试替代。
