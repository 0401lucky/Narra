# 页面与截图覆盖矩阵

首批 76 张基准已于 2026-10-14 在 Windows 本机生成（候选，状态见下方「首批生成状态」）；未列出的条目保持待生成或转设备。源码路径以下列根目录为前缀：
app/src/main/java/com/example/myapplication/ui/。

## 15 个候选页面 / 容器

| ID | 页面 / 容器 | 源码入口 | 批次 |
| --- | --- | --- | --- |
| P01 | 消息首页 / 手机主壳 | screen/immersive/ImmersivePhoneShell.kt | 首批 |
| P02 | 角色管理列表 | screen/settings/AssistantListScreen.kt | 首批 |
| P03 | 线上扮演聊天 | screen/roleplay/RoleplayOnlinePhoneContent.kt | 首批 |
| P04 | 线下沉浸扮演 | screen/roleplay/RoleplayScreen.kt | 后续页面扩面；首批检查气泡组件兼容 |
| P05 | 会话设置侧栏 | screen/roleplay/RoleplaySettingsSidebarPanel.kt | 后续 |
| P06 | 世界书列表 | screen/settings/worldbook/WorldBookListScreen.kt | 后续 |
| P07 | 记忆管理 | screen/settings/memory/MemoryManagementScreen.kt | 后续 |
| P08 | 朋友圈时间线 | screen/moments/MomentsScreen.kt | 后续 |
| P09 | 信箱 | screen/roleplay/mailbox/MailboxScreen.kt | 后续 |
| P10 | 设置首页 | screen/settings/SettingsScreen.kt | 后续 |
| P11 | 提供商配置 | screen/settings/ProviderDetailScreen.kt | 后续 |
| P12 | 角色创建 / 基础设定 | screen/settings/AssistantBasicScreen.kt | 后续 |
| P13 | 语音合成设置 | screen/settings/VoiceSynthesisSettingsScreen.kt | 后续 |
| P14 | 扮演日记阅读 | screen/roleplay/RoleplayDiaryDetailScreen.kt | 后续 |
| P15 | 查看手机 | screen/phone/PhoneCheckScreen.kt | 后续 |

普通 Chat 不计作新增可见入口；保留 F1 基准和共享消息组件回归。系统悬浮窗、输入法、状态栏与导航栏另列设备矩阵，宿主图不能替代。

## 首批必需用例族

| ID | 输入 / 组合 | 验证对象 |
| --- | --- | --- |
| H01 | P01/P02/P03，抹茶/香草/海洋 × 浅/深，常规内容 | 真实页面根、标题、关键操作、消息与角色数据存在 |
| H02 | P01/P02 现有空态或无搜索结果；P03 空会话 | 记录现有状态，不在安全网内补业务空态 |
| H03 | P03 一条跨多屏长回复，固定一段流式中间帧及错误消息 | 长文、停止/错误表示和布局的现状 |
| H04 | P01/P02/P03，320×640dp、字体 200%，浅/深 | 窄屏大字体基准；既有缺陷单独登记，不能默认为已修复 |
| B01 | 13 套皮肤 × 浅/深，固定用户/角色/旁白消息 | 调色板、尾角、正文和强调文字 |
| B02 | 无背景、成功加载背景/玻璃开、成功加载背景/玻璃关、背景加载失败 | 气泡颜色/形状/宽度和背景真实状态分离 |
| B03 | 圆角 4/28、宽度 64/92%、横向内边距 8/20、纵向 6/18、尾角开/关 | 边界分别覆盖，避免所有维度笛卡尔积 |
| B04 | 线上/线下模式与玻璃开关组合 | 线上全局开关生效，线下继续原逻辑 |
| C01 | 固定动态 ColorScheme 的浅/深输入 | 复用生产颜色解析；不是读取 CI 机器壁纸 |
| D01 | 代表性玻璃、有背景纯主题气泡、窄屏字体 200% | 实际设备图与交互；记录设备环境和未覆盖项 |

H01 常规参数为 360×800dp、density 2、fontScale 1；H04 为 320×640dp、fontScale 2。SDK、物理像素、字体来源、locale、timezone、固定时刻、工具版本与用例映射已写入运行清单 app/src/test/snapshots/manifest.md；参数变化必须审阅并重建对应基准。

## 首批生成状态（2026-10-14，Windows 本机候选）

所有基准仅为本机候选，**未经 CI 宿主或真机确认**，状态不得升级为「已确认」。登记表：app/src/test/java/com/example/myapplication/ui/screenshot/ScreenshotCases.kt；运行清单与完整映射：app/src/test/snapshots/manifest.md。

| 用例族 | 计划覆盖 | 当前状态 |
| --- | --- | --- |
| H01 | P01/P02/P03 × 抹茶/香草/海洋 × 浅/深（18 张） | 已人工确认（2026-10-14 用户确认；Windows 候选，CI 宿主复跑待做）；P03 六张已随调色板来源修复定向重录（浅/深两组），见下方观测 |
| H02 | P01/P02 现有空态 + P03 空会话（3 张） | 已人工确认（2026-10-14 用户确认；Windows 候选，CI 宿主复跑待做） |
| H03 | 跨多屏长回复 / 固定流式中间帧 / 错误消息（3 张） | 已人工确认（2026-10-14 用户确认；Windows 候选，CI 宿主复跑待做） |
| H04 | P01/P02/P03 窄屏 320×640dp + 字体 200% × 浅/深（6 张） | 已人工确认（2026-10-14 用户确认；Windows 候选，CI 宿主复跑待做）；既有截断缺陷已登记 |
| B01 | 13 套皮肤 × 浅/深（26 张） | 已人工确认（2026-10-14 用户确认；Windows 候选，CI 宿主复跑待做） |
| B02 | 无背景 / 玻璃开 / 玻璃关 / 加载失败（4 张） | 已人工确认（2026-10-14 用户确认；Windows 候选，CI 宿主复跑待做）；真实材质转 D01 |
| B03 | 圆角、宽度、内边距、尾角边界分别覆盖（10 张） | 已人工确认（2026-10-14 用户确认；Windows 候选，CI 宿主复跑待做） |
| B04 | 线上/线下 × 玻璃开关（4 张） | 已人工确认（2026-10-14 用户确认；Windows 候选，CI 宿主复跑待做）；线下两项为组件级 |
| C01 | 固定动态 ColorScheme 浅/深（2 张） | 已人工确认（2026-10-14 用户确认；Windows 候选，CI 宿主复跑待做） |
| D01 | 真机材质、系统栏/IME、真实取色等宿主不可覆盖项 | 宿主不可覆盖，转 D01；清单见 manifest.md 第 6 节，尚未执行 |

### 本机观测（只记录，不修产品）

- P03 六张原与 B02「无背景」像素一致（测试舞台注入固定合成调色板）的缺口已修复：2026-10-14 fixture 改为在组合内走生产派生 deriveImmersiveGlassPalette(imageBitmap, MaterialTheme.colorScheme, highContrast = false)，并定向重录 19 张受影响基准（H01 P03 六张、H02 P03、H03×3、H04 P03×2、B02×4、B04×3）。重录后 H01 P03 浅/深两组哈希不同；同一深浅下三种主题仍一致（生产 resolveAppColorScheme 按深浅统一 surface 系颜色，无背景外壳不绘制随主题变化的 primary/secondary 强调色），属生产行为而非夹具缺口，详见 manifest.md 第 7 节。
- B02「已配置背景但未加载成功」与「未配置背景」重录后仍像素一致：组件只在背景 bitmap 可用时绘制背景，没有占位或失败 UI。
- H04 窄屏 + 200% 字体：P03 首条消息被放大的头部遮挡、输入区提示换行溢出；P01 底栏文字下沿裁切。既有缺陷已于 2026-10-14 随基准确认登记，是否单独立项修复由后续决定。

## 数据与截图契约

- 所有角色、消息、日期、图片均使用合成 fixture；不从真实数据库、用户文件、在线头像或随机网络取得数据。
- 每个用例有稳定 ID，与测试名、基准图文件和人工确认记录一一对应。
- 截图前断言代表性节点存在、内容和尺寸正确，防止空白 Activity 或错误页面也被当作基准。
- 图片预解码并等待完成；动画在已知时刻截取。正常 JVM 测试和截图验证使用各自明确入口，不能因筛选截图而漏掉原有单测。
- 初始基准保存当前真实外观；当前缺陷写入登记，不在记录过程中顺手改变产品行为。
- 若宿主不能正确渲染某项真实材质，标明原因并进入设备矩阵；不得将其标成宿主已覆盖。
- 每张基准状态区分「待生成 / 已生成待人工确认 / 已人工确认 / 已确认」。首批 76 张当前为「已人工确认（Windows 本机候选，2026-10-14 用户确认）」；CI 宿主复跑并通过前不得升级为「已确认（CI 宿主）」。
