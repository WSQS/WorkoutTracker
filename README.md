# 每日运动 · WorkoutTracker

一个离线安卓小应用，用来每天打卡追踪运动量，并且记录**每一组是什么时候做的**。

默认计划（每天合计 **300 个**）：

| 项目 | 每组 | 组数 | 每天合计 |
| --- | --- | --- | --- |
| 俯卧撑 | 10 个 | 8 组 | 80 个 |
| 深蹲 | 10 个 | 12 组 | 120 个 |
| 仰卧起坐 | 20 个 | 5 组 | 100 个 |

- 包名：`dev.sopho.workout`
- 版本：1.2.0（versionCode 10200）
- minSdk 26（Android 8.0+），targetSdk / compileSdk 36
- 不联网、无第三方服务。两条权限都与提醒有关：`POST_NOTIFICATIONS`（通知，运行时权限）与
  `SCHEDULE_EXACT_ALARM`（可选的“精确闹钟”特殊权限，授权后提醒才准点）；除此之外一概不申请

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

## 提醒

勾完任意一组，过**间隔**（默认 30 分钟）提醒你做下一组；**活动时间开始**（默认 9:00）时如果今天还没勾过任何一组，提醒一次。
提醒只在**活动时间**（默认 9:00–18:00）内工作：窗口外勾选不排提醒，到期时间落在窗口外也不排。

- 到点响一次（提示音+震动），通知留在通知栏直到你勾选下一组；同一时刻只保留一条
- 迟到也照发，并在通知里写明“预定 HH:mm，晚了 N 分钟”；打开应用时若发现提醒已过期，今天页会说明
- 设置页：提醒总开关、间隔、活动时间、当前模式（精确/近似）、权限状态与快捷入口
- 用的是**一次性系统闹钟**：不做前台服务、不做常驻通知、不申请开机自启（理由见 `docs/adr/0001`）

**已知的失败模式（都不藏）**

| 情况 | 结果 |
| --- | --- |
| 没授权精确闹钟权限 | 退回近似模式：**实测系统给的投递窗口约 22 分钟（30 分钟间隔）到 1 小时（远期闹钟）**，即提醒可能晚这么久 |
| 用户“强制停止”应用（force-stop／某些 ROM 的“清理”） | **已排的闹钟会被一起清掉**，那次提醒不会来；下次打开应用会在今天页显示“已超时” |
| 划掉最近任务／应用在后台被冻结 | 闹钟照常送达（这正是本设计的核心：应用可以随时死） |
| 手机在提醒前重启 | 闹钟丢失，同样靠“已超时”兜底 |
| 拒绍通知权限 | 一条通知都发不出（Android 13+ 的硬规则）；设置页与今天页会显示“提醒未启用”，不会假装提醒过 |
| Android Automotive | 通知语义不同、`chronometer` 倒计时不被支持，所以本功能只在手机形态上做验证 |

## 数据

存在应用私有目录的 SharedPreferences（`workout_store`）里，共 5 个键：

```json
plan_json:    [{"id":"pushup","name":"俯卧撑","emoji":"💪","reps":10,"sets":8}, ...]
records_json: {"v":2,"days":{"2026-08-30":{"pushup":[{"at":1788117964287},{"at":null}]}}}
reminder_settings_json: {"enabled":true,"intervalMinutes":30,"windowStart":540,"windowEnd":1080}
reminder_last_tick:     上一次“刚做完一组”的时刻（组间提醒的基准）
reminder_notified_due:  最后一次确实发出通知的那条提醒的到期时间
```

`records_json` 里每个项目是一个数组，第 i 项就是第 i+1 组；`at` 是勾选时刻（epoch 毫秒），
`null` 表示补记。

提醒的**待办本身不落盘**，而是由上面这些状态算出来的（见 `data/Reminder.kt` 的 `planReminders`）：
少一处存储状态，就少一种“存的和实际不一致”的可能。

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
app/src/main/java/dev/sopho/workout/data/Reminder.kt   提醒的领域规则（纯函数：窗口、组间、开始、超时）
app/src/main/java/dev/sopho/workout/reminder/          平台胶水：排闹钟、发通知、接收器、协调器
app/src/test/java/dev/sopho/workout/data/{StatsTest,MarksTest,ReminderTest}.kt
```

## 验证记录

### 1.1.0（每组时间）

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

### 1.2.0 提醒（2026-10-13，Android 15 / SDK 35 AVD，手机形态 userdebug）

单测：**44/44 通过**（Marks 12 + Stats 8 + Reminder 24）。

在 AVD（`UE_pixel_6_API_35`，WHPX 加速）上用 `uiautomator` 定位并断言界面、用 `dumpsys` 取证：

- 打开提醒开关后，系统里立刻排上 `tag=*walarm*:dev.sopho.workout.REMINDER_StartOfDay`，`origWhen=2026-10-14 09:00:00.000` ✓
- 勾一组后，排上 `REMINDER_NextSet`，`origWhen = 勾选时刻 + 间隔`（1 分钟间隔下精确对上）✓，两种用途并存 ✓
- 今天页显示 `距下一组 0:55（09:00–18:00 内提醒） · 近似模式，可能晚十几分钟到一小时` ✓
- **应用退到后台**后到点，通知确实被系统投递：`channel=reminder_nextset`、`importance=3`、`category=reminder` ✓
- 窗口边界：17:59 勾选（到期 18:00 恰在窗口外）→ 提醒行变成 `当前不在活动时间`、不再排组间提醒 ✓
- `force-stop` 之后 100 秒无通知（闹钟被系统清掉）→ 重开应用显示 `上次排的下一组已超时 18 分钟`（兜底生效）✓
- 实测近似闹钟的投递窗口：30 分钟间隔 → `window=+22m29s`；远期闹钟 → `window=+1h0m0s`
  ⇒ **未授权精确闹钟时“晚几分钟”是不可能的，“晚十几分钟到一小时”才是真实范围**（界面文案照此写）

精确闹钟那条路（同样在 AVD 上实测）：

- 设置页「去授权」正确跳到系统的“闹钟和提醒”页（`com.android.settings/.spa.SpaActivity`）
- 授权后：**应用没被重启**，系统里那两条已排闹钟就从 `window=+1h0m0s0ms` 变成 `window=0`、
  `exactAllowReason=permission` —— 验证了“精确闹钟权限变化”广播接收器确实被唤起并完成了重排
- 今天页的模式文案随之从“· 近似模式，可能晚十几分钟到一小时”变为不带该后缀（=精确）
- **准点性**：精确模式下到点后 **2 秒**投递；对照近似模式（到点 1 分钟时还没动静）

拒绍通知权限时的行为：

- 今天页显示 `提醒未启用：缺少通知权限（到「设置」里打开）`，设置页的“通知权限”行也显示未开启
- 到点时（闹钟照常触发）：**不发通知，也不记“已提醒”**（落盘的 `reminder_notified_due` 没变）—— 不假装提醒过 ✓
- 重新授予权限后回归正常：勾选 → 到点后 2 秒投递 ✓

**AVD 验证过程中发现并修掉的三个真问题**（都有回归测试）：

1. 在窗口内的时间开启提醒、之后不再打开应用 → **一条闹钟都不排**，提醒链断掉（`nextStartReminder` 修复）
2. 协调器把“已到期、系统可能正在迟缓投递”的闹钟**当成不需要而取消** → 你在这段时间打开一次应用，提醒就静静消失（改为 `ReminderAction.LeaveAlone`）
3. 界面文案与实测不符（原写“可能晚几分钟”）

**未验证 / 验证不了**：系统“允许设置闹钟和提醒”对话框里最后那一下 `Allow` 是用户点的 —— 我验到了弹框出现，
实际授权走的是等价的 `cmd appops set … SCHEDULE_EXACT_ALARM allow`（同一个底层 app-op）；
划掉最近任务（与 `force-stop` 不同）；真实厂商 ROM 的额外杀后台行为；真机 arm64。

### Release 流水线（2026-10-13 首次真跑）

- push tag `v1.1.0` → Actions 构建并自动发布：https://github.com/WSQS/WorkoutTracker/releases/tag/v1.1.0
- 从**匿名视角**把发布出来的两个包下下来核对：
  `sha256sum -c SHA256SUMS.txt` 两个文件均 OK；两个包的证书指纹都是 `8f549fbe…`
  （与本地 `keystore/` 那把一致 ⇒ 能直接覆盖升级已安装的版本、记录不丢）；
  两个包的 versionCode/versionName 都是 `10100` / `1.1.0`（由 tag 推导）
- 踩过并修掉的坑：release job 里没有 checkout、工作目录没有 `.git`，`gh release create` 必须带 `--repo`，
  否则它去读本地 git 会报 `failed to run git: not a git repository`

## 许可证

MIT License —— 见 [LICENSE](LICENSE)。可以自由使用、修改、再分发，保留版权声明即可。

## 还没做的（可选下一步）

- 桌面小组件显示今日进度
- 记录导出/导入
- 每组时间的可视化（把一天的时间点画成时间轴/间隔柱状图）
