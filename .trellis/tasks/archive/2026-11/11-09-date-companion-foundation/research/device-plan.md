# D1 隔离设备验证准备

当前未运行D1设备用例。2026-11-09已只读确认Android SDK与先前合成测试AVD可用，物理/其它模拟器设备列表为空。

- SDK：`C:/Users/lucky0401/AppData/Local/Android/Sdk`，adb/emulator未加入PATH，使用完整可执行路径。
- 已安装Android36 Google Play x86_64镜像，无需安装新的SDK组件。
- 专用合成测试AVD：`Narra_Memory_V24`，目录`app/build/tmp/memory-v24-avd/`，端口5580；原C1–C6已用此设备验证，不能误用用户默认Pixel AVD。
- 此目录是独立AVD home；普通`emulator -list-avds`只显示默认三个设备，未显示本专用AVD不是不存在。
- 主机约16GB内存；Gradle唯一所有者完成并冻结源码/APK后再启动1536MB/2核测试AVD，设备结束关闭后再构建。
- 启动时在当前工具子进程设置ANDROID_AVD_HOME为上述目录并继承给emulator，使用隐藏窗口/no-window与独立日志；不写全局环境或修改用户默认AVD。
- 安装前核对实际APK包名/签名/哈希与checker证据，向指定`emulator-5580`安装，先核对AVD名称再操作；不得向其它设备安装或清数据。
- 用例包括新增固定54→55的CompanionFoundationMigrationTest、旧库升级、v2.4来源/运行/删除回归；确切类和数量以最终测试源码为准。所有库/数据必须由用例创建隔离合成实例。
- 保存原始instrumentation输出，统计真实唯一测试和失败，不以adb exit0替代`OK (N tests)`/测试终态。

已启动的adb本地服务仅用于发现设备，当前未安装APK、未启动AVD、未改变任何测试设备设置。依赖统一构建/检查完成后再执行。
