# 每日运动 · WorkoutTracker

一个离线安卓小应用，用来每天打卡追踪运动量，并且记录**每一组是什么时候做的**。

默认计划（每天合计 **300 个**）：

| 项目 | 每组 | 组数 | 每天合计 |
| --- | --- | --- | --- |
| 俯卧撑 | 10 个 | 8 组 | 80 个 |
| 深蹲 | 10 个 | 12 组 | 120 个 |
| 仰卧起坐 | 20 个 | 5 组 | 100 个 |

- 包名：`dev.sopho.workout`
- 版本：1.1.0（versionCode 2）
- minSdk 26（Android 8.0+），targetSdk / compileSdk 36
- 不联网、不申请任何权限、无第三方服务

## 安装

先本地构建（`dist/` 不进仓库）：

```bash
./gradlew assembleRelease          # 产物：app/build/outputs/apk/release/
adb install -r app/build/outputs/apk/release/app-release.apk
```

也可以把 release APK 传到手机点击安装。想直接用打包好的现成包，看仓库的 Releases
（那里附了 APK 和 `SHA256SUMS.txt`）。

## 关于「时间」：做了什么，以及为什么不给补记编时间

一组记录不是简单的计数，而是一条带时间戳的记录（`SetMark(at = 勾选时刻的毫秒数)`）。

- **真点出来的组**有真实时间：点圆圈、`+1 组` 都算
- **批量勾上的组**（`全部完成`，或者跳过中间的圆圈直接点后面的）真的做了，但当时没有记录到时间，
  所以时间是 `null`，界面上叫 **「补记（无时间）」**，圆圈显示成橙色而不是蓝色。
  给它们盖上当前时间戳就是编数据，所以不编。
- 由此得到的「用时」只在**至少两组有真实时间**时才算，否则不显示（一组谈不上用时）；
  如果系统时钟被往前调过导致跨度为负，也不显示用时，而不是显示负数。

界面上能看到的：

- **今天页**：顶部与每张卡片各有一行 `训练时段 08:10 – 08:34 · 用时 24 分钟 · 含 2 组补记`；
  点卡片里的「每组时间」，逐组列出时间或「补记（无时间）」
- **记录页**：每天明细里有 `🕘 08:10 – 08:34 · 用时 24 分钟`，累计数据里有
  `累计训练时长 X · 平均每次 Y`
- 时间按设备当前时区显示（存储用 epoch 毫秒，不存字符串时区）

## 用法

**今天** — 三个项目各一张卡片：

- 点圆圈 = 完成该组（从中间点也行：中间的算补记，点的那组记真实时间）
- 点已经完成的圆圈 = 退回到该组之前（等于撤销）
- `+1 组` / `撤销一组` 逐组增减，`全部完成` 一次勾满（都是补记）
- 顶部是今日完成百分比、`已完成 X / 300 个`、连续达标天数、今日训练时段与用时
- 日期变了自动进入新的一天，昨天的记录留在「记录」页

**记录** — 累计数据（打卡天数、达标天数、当前连续、最长连续、各项目累计个数、累计训练时长、
平均每次用时）、最近 14 天完成度柱状图、最近 30 天逐日明细（含当天训练时段；达标日整卡高亮）。

**设置** — 调整每个项目的「每组个数」和「组数」，改完立刻生效；以及
`重置今天` / `清空记录` / `恢复默认计划`（都会先弹确认框）。

「达标」= 当天三个项目全部勾满；「打卡」= 当天至少勾了一组。
连续达标从今天往前数，今天还没做完不会把昨天之前的连续记录打断。

## 数据

存在应用私有目录的 SharedPreferences（`workout_store`）里，两个 JSON 键：

```json
plan_json:    [{"id":"pushup","name":"俯卧撑","emoji":"💪","reps":10,"sets":8}, ...]
records_json: {"v":2,"days":{"2026-08-30":{"pushup":[{"at":1788117964287},{"at":null}]}}}
```

`records_json` 里每个项目是一个数组，第 i 项就是第 i+1 组；`at` 是勾选时刻（epoch 毫秒），
`null` 表示补记。

**不向后兼容**：v2 只读 `records_json` 里的 `"days"` 结构，1.0.0 写下的旧格式
（`{"2026-08-30":{"pushup":3}}`）会被当成「还没有记录」而忽略 —— 这是明确取舍，不是 bug。
另外卸载应用会连数据一起删除（还没有导出/导入功能）。

改「每组个数」会让历史累计个数按新数值一起变；改「组数」会让历史那天的达标判定按新组数算。

## 构建

需要 JDK 17 和 Android SDK（platform 36、build-tools 36.1.0）。

克隆下来后还要告诉 Gradle SDK 在哪，二选一：

- 建一个 `local.properties`（不提交），写 `sdk.dir=/path/to/Android/Sdk`
- 或者设环境变量 `ANDROID_HOME=/path/to/Android/Sdk`

```bash
export JAVA_HOME=/path/to/jdk-17
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease        # app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest      # 20 个纯逻辑单元测试
```

单元测试：`StatsTest`（完成度、达标、连续达标、累计）+ `MarksTest`（点击/撤销/跳点、
补记、时间窗口与用时、时钟倒退、跨项目时段）。

### 签名

签名口令**不在仓库里**：把 `keystore.properties.example` 复制成 `keystore.properties`（已被 git 忽略），
填上自己的密钥即可。没有这个文件时 `assembleRelease` 照样能跑，只是产出的是未签名的
`app-release-unsigned.apk`。

```bash
keytool -genkeypair -v -keystore keystore/release.jks -alias mykey \
  -keyalg RSA -keysize 2048 -validity 10950
```

**升级必须用同一把钥匙**：换了签名，已装的旧版就无法覆盖升级，只能先卸载（数据会一起没）。
所以 `keystore/*.jks` 需要自己单独备份好。

## 仓库里有什么、没有什么

提交：源码、资源、Gradle 构建脚本与 wrapper、单元测试、README。

**不提交**（见 `.gitignore`，理由是这些东西要么是本机/个人的，要么泄露了会有实际风险）：

| 路径 | 为什么 |
| --- | --- |
| `keystore/`、`keystore.properties` | 签名私钥与口令。公开它等于让别人能签出「Android 认作正常升级」的 APK |
| `dist/` | 本地打包好的 APK（二进制，每次改动在 git 历史里叠一层） |
| `local.properties` | 本机 SDK 路径 |
| `build/`、`.gradle/`、`.idea/` | 构建产物与 IDE 配置 |

## 自动构建与发布（GitHub Actions）

`.github/workflows/release.yml`：

| 触发 | 结果 |
| --- | --- |
| push 到 `main`、对 `main` 的 PR | 构建 + 跑单元测试（不发 Release） |
| push tag `v1.2.3` | 构建 + 发 Release，附 **release 包**、**debug 包** 与 `SHA256SUMS.txt` |
| 手动 Run workflow（可填 tag） | 同上；tag 留空则只构建、不发 Release |

版本号由 tag 推导：`v1.2.3` → versionName `1.2.3`、versionCode `10203`（`major*10000+minor*100+patch`）。
versionCode 必须递增，否则 Android 不把新包当升级；预发布 tag（如 `v1.2.3-rc1`）会减 1，保证正式版能盖过它。

### 想让 Release 里的包能直接装，需要 3 个 secret

不配也能跑，但那样 release 包是**未签名**的（Android 直接拒装），debug 包用的是每次构建都不同的
CI 临时签名（跨版本安装必须先卸载，**运动记录会一起丢**）。配上之后两个包都用你的正式密钥签名，
**可以互相覆盖安装、记录不丢**。在你自己的终端里跑（`gh` 需已登录）：

```bash
base64 -w0 keystore/workout-release.jks > /tmp/ks.b64
gh secret set KEYSTORE_BASE64 < /tmp/ks.b64
gh secret set KEYSTORE_PASSWORD --body 'storePassword 的值'
gh secret set KEY_ALIAS         --body 'workout'
gh secret set KEY_PASSWORD      --body 'keyPassword 的值'
rm /tmp/ks.b64
```

密钥只在 `push` tag / 手动触发时注入 runner；`pull_request` 一律不注入，避免「不信任的代码 + 密钥」同处一台机器。
（公共仓库的 Actions 分钟数免费。）

## 代码结构

```
app/src/main/java/dev/sopho/workout/
  MainActivity.kt          入口、主题、跨天刷新（onResume）
  data/Model.kt            Exercise 数据类 + 默认计划 + 取值范围
  data/Marks.kt            每组记录：SetMark / 点击与撤销语义 / 时间窗口与用时（纯函数）
  data/Stats.kt            纯函数：完成度、达标、连续达标、累计
  data/WorkoutStore.kt     持久化 + StateFlow（Compose 直接订阅）
  ui/App.kt                顶栏 + 底部三页签骨架
  ui/TodayScreen.kt        今天（组圆圈、进度条、快捷按钮、每组时间弹窗）
  ui/HistoryScreen.kt      记录（累计、14 天柱状图、30 天明细）
  ui/SettingsScreen.kt     设置（计划编辑、数据操作、关于）
  ui/Format.kt             日期/时间/时长/百分比格式化
  ui/Theme.kt              Material 3 主题（Android 12+ 跟随取色）
app/src/test/java/dev/sopho/workout/data/{StatsTest,MarksTest}.kt
```

## 验证记录（1.1.0）

- `assembleDebug` + `assembleRelease` + `testDebugUnitTest`：全部成功，**20/20** 测试通过
- 在 Android 16（SDK 36）车机虚拟机上用 uiautomator 界面树定位点击并断言文本：
  - 间隔 35 秒点第 1、2、3 组，再跳过第 4、5 组直接点第 6 组
  - 界面显示 `训练时段 19:26 – 19:27 · 用时 1 分钟 · 含 2 组补记`
  - 「每组时间」弹窗逐组显示：第 1 组 19:26、第 2 组 19:26、第 3 组 19:27、**第 4 组 补记（无时间）**、
    **第 5 组 补记（无时间）**、第 6 组 19:27
  - `am force-stop` 后重新启动，以上数据与时间全部还在（真的落盘了）
  - 落盘的原始 JSON 为 `{"v":2,"days":{"2026-08-30":{"pushup":[{"at":…287},{"at":…717},{"at":…135},
    {"at":null},{"at":null},{"at":…198}]}}}`，间隔与用时一致
  - release 包同样验证：5/8 组、`用时 不到 1 分钟 · 含 2 组补记`、弹窗正确、无 `FATAL EXCEPTION`
- 验证结束后已 `adb uninstall`

## 许可证

MIT License —— 见 [LICENSE](LICENSE)。可以自由使用、修改、再分发，保留版权声明即可。

## 还没做的（可选下一步）

- 每日提醒通知（需要 `POST_NOTIFICATIONS` 权限 + AlarmManager/WorkManager）
- 桌面小组件显示今日进度
- 记录导出/导入
- 每组时间的可视化（把一天的时间点画成时间轴/间隔柱状图）
