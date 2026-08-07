# 角色音色配置滚动修复：技术设计

## 1. 现状与边界

全局 `VoiceSynthesisSettingsScreen` 已使用 `LazyColumn`；缺陷位于
`AssistantVoiceProfileSheet` 的 `ModalBottomSheet` 内容。动态模式、克隆错误提示和
长文本会使普通 `Column` 超出可视高度，底部保存行不可达。

## 2. 布局方案

- 保留现有 `ModalBottomSheet`、草稿状态和回调契约。
- 将弹窗内容放入单一可滚动容器（优先 `Column.verticalScroll` 的最小改动；若测量
  证明内容项过多，再改为 `LazyColumn`）。
- 使用 `rememberScrollState()` 保存弹窗生命周期内的滚动位置；模式切换不重建
  滚动状态，关闭弹窗后沿用现有重新创建行为。
- 内容容器补 `imePadding()` 与 `navigationBarsPadding()`，底部增加稳定 padding，
  确保输入法和系统导航栏出现时仍可滚动到操作行。
- 不嵌套第二个滚动容器；预置音色选择仍由独立的子 Sheet 承载。

## 3. 状态与兼容性

滚动状态是局部 UI 状态，不写入设置。模式切换只改变内容，不清空现有草稿；保存、
试听、克隆样本复制/删除语义保持不变。

## 4. 测试

使用 Compose 设备测试从现有设置页打开目标 Sheet；若私有 Composable 阻碍窄测，
仅提取同包可见的内容 Composable，不扩大业务 API。验证长文本/错误提示下保存节点
可滚动到、点击后回调拿到最新草稿；同时验证模式切换和重新打开不回归。

## 5. 回滚

布局修改独立于 ViewModel 和持久化；若特定 Material 版本出现 BottomSheet 测量问题，
可回退到原容器并保留测试，再改用 `LazyColumn` 方案。
