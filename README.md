<div align="center">

<img src="docs/icon.png" width="104" alt="Habit 图标" />

# Habit · 好习惯

**A good habit builds everything**

一款用积分、成就和一点点浪漫陪你把好习惯坚持下去的 Android 应用

![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF?logo=kotlin&logoColor=white)
![Material 3](https://img.shields.io/badge/Material-3-757575?logo=materialdesign&logoColor=white)
![Version](https://img.shields.io/badge/version-beta1.0-blue)
![License](https://img.shields.io/badge/license-MIT-green)

</div>

---

## 它是什么

想早睡、想读书、想运动——道理都懂，坚持很难。

Habit 把「坚持」变成看得见的数字：

- **每完成一次，加分**；每违背一次，减分
- 攒下的积分能**兑换成自己定的小奖励**（看一集剧、一杯奶茶、睡个懒觉）
- 连续坚持会解锁**成就**，也会被记录成**连续天数**
- 数据全部存在你手机本地，**不联网、不注册、不上传**

---

## 截图

<table>
  <tr>
    <td align="center"><img src="docs/screenshots/01-plans.jpg" width="230" /><br/><sub>计划列表</sub></td>
    <td align="center"><img src="docs/screenshots/02-new-plan.jpg" width="230" /><br/><sub>新建计划</sub></td>
    <td align="center"><img src="docs/screenshots/03-store.jpg" width="230" /><br/><sub>积分商店</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/04-me.jpg" width="230" /><br/><sub>我的</sub></td>
    <td align="center"><img src="docs/screenshots/05-about.jpg" width="230" /><br/><sub>关于</sub></td>
    <td align="center"><img src="docs/screenshots/07-winter.jpg" width="230" /><br/><sub>彩蛋：冬天的雪</sub></td>
  </tr>
</table>

---

## 功能

### 计划管理

- 创建计划：名称、信念、契机、每次加分数、每次减分数、起始日期
- 卡片上直接 **加分 / 减分**，带 🎉 和 💪 鼓励浮层
- 连续加分计数（减分即清零），并记录历史最长连续
- 编辑、删除（删除会提示积分一起清空）

### 积分与商店

- 总积分 = 所有计划积分之和
- 商店里的**奖励由你自己创建**：想兑换什么、需要多少分，都由你定
- 兑换有二次确认，扣分实时同步到顶部积分

### 成就

| 成就 | 条件 |
|---|---|
| 小试牛刀 | 单个计划连续加分 10 次 |
| 自律专家 | 单个计划连续加分 100 次 |
| 自律大师 | 单个计划连续加分 1000 次 |
| 自律大神 | 所有计划累计连续加分 5000 次 |

解锁后永久保留，也可以一键清空重新挑战。

### 其他

- **5 种语言**：简体中文 / 繁體中文 / English / 日本語 / Русский
- **深色 / 浅色 / 跟随系统**
- 动态配色（Android 12+ 跟随壁纸）
- 用户协议、隐私政策、开源许可（独立阅读页，多语言）
- 开发者列表与详情页

---

## 三个彩蛋

<details>
<summary>⚠️ 这里有剧透，想自己探索就别点开</summary>

<br/>

**① 你瞅啥呢**

`我的 → 彩蛋` 进去是一片空白，只有一个 🙃 和一句话。
表情会缓慢浮动；连点 5 下，它就不装了（🙃 → 🤪），并把出口交给你。

**② 某些地方似乎发生了变化**

前置条件：**进过彩蛋①** + **解锁「小试牛刀」**。

在「计划」页连点左上角 App 图标 3 下（每下都有震动），第 3 下弹出提示。

**③ 冬天到了**

彩蛋②触发后，去「我的」点**任意一行菜单**，整个屏幕会被劫持：

> 黑场渐亮 → 雪夜、月亮、松林、亮着灯的雪中小屋 → 开始下雪
> → 四句诗竖排逐字浮现（冬天到了 / 雪花飘扬 / 我的所爱 / 你在哪里）
> → 落款印章 → 缓慢黑屏收束，共 18.5 秒

**整段动画没有使用任何图片素材**，全部是 `Canvas` 手绘：天空渐变、星星闪烁、月亮环形山、远山贝塞尔曲线、松树积雪、炊烟、飘雪粒子、暖黄窗光……全部即时绘制。

</details>

---

## 技术栈

| 项目 | 说明 |
|---|---|
| 语言 | Kotlin |
| 构建 | Gradle 9 + AGP 9（内置 Kotlin 支持） |
| UI | XML 布局 + Material 3（不使用 Compose） |
| 目标 | minSdk 24 / targetSdk 37 / compileSdk 37 |
| 存储 | SharedPreferences + org.json（无数据库、无网络） |
| 动画 | View 动画 + 自定义 View `onDraw` |
| 依赖 | 仅 AndroidX + Material（无第三方库） |

---

## 项目结构

```text
Habit/
├── app/src/main/
│   ├── java/com/example/habit/
│   │   ├── SplashActivity.kt         启动页
│   │   ├── MainActivity.kt           主界面（计划 / 商店 / 我的）
│   │   ├── Project.kt                计划数据模型
│   │   ├── ProjectStore.kt           计划本地存储
│   │   ├── Store.kt                  商店奖励与兑换记录
│   │   ├── Achievement.kt            成就定义与解锁
│   │   ├── AchievementStore.kt       成就存储
│   │   ├── EasterEggActivity.kt      彩蛋①
│   │   ├── WinterEgg.kt              彩蛋②③的状态机
│   │   ├── WinterActivity.kt         彩蛋③ 播放页
│   │   ├── WinterSceneView.kt        雪夜场景（纯 Canvas 手绘）
│   │   ├── ProfileActivity.kt        个人主页
│   │   ├── LoginActivity.kt          登录
│   │   ├── RegisterActivity.kt       注册
│   │   ├── SettingsActivity.kt       设置
│   │   ├── AppSettingsActivity.kt    语言与主题
│   │   ├── AchievementsActivity.kt   成就页
│   │   ├── AboutActivity.kt          关于页
│   │   ├── Developer.kt              开发者数据
│   │   ├── DeveloperDetailActivity.kt 开发者详情
│   │   └── LegalActivity.kt          用户协议 / 隐私政策 / 开源许可
│   └── res/
│       ├── layout/                   25 个布局
│       ├── drawable/                 43 个手写 vector 图标
│       ├── values/ + 4 套语言         217 条文案 × 5 种语言
│       └── mipmap-*/                 应用图标（由脚本生成）
├── docs/screenshots/                 README 截图
└── habit-release.jks                 ⚠️ 签名密钥（不提交，见下）
```

---

## 构建

**环境要求**：JDK 21+、Android SDK 37

```bash
# 调试版（可直接安装）
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk

# 正式版（需自备签名）
./gradlew assembleRelease
```

### 关于签名

仓库里**不包含**签名密钥。想打正式包，请先生成自己的 keystore：

```bash
keytool -genkeypair -v -keystore habit-release.jks \
  -storetype JKS -keyalg RSA -keysize 2048 -validity 10000 \
  -alias habit -storepass <你的密码> -keypass <你的密码> \
  -dname "CN=YourName, OU=Habit, O=YourName, C=CN"
```

然后在 `app/build.gradle.kts` 的 `signingConfigs` 里改成你的路径与密码。

> **keystore 一旦丢失，你就再也无法更新这个 App**——系统要求同一包名的新版本必须用同一把钥匙签名。请单独备份。

---

## 说明

- 应用**不申请任何敏感权限**，不联网，不收集任何个人信息
- 卸载应用或清除数据会永久删除所有记录，请自行留意
- 本项目在真机（荣耀 Magic8 Pro / Android 17）上完成全部功能验证

---

## 开发者

<table>
  <tr>
    <td align="center" width="140"><b>Wan</b><br/><sub>创始人 · 开发者</sub></td>
    <td>独立开发者，喜欢折腾各种小工具。Habit 是从零开始学 Android 的第一个完整项目——从搭环境、写第一行 Kotlin，到把它装进手机里跑起来。</td>
  </tr>
  <tr>
    <td align="center"><b>DeepSeek Harness</b><br/><sub>AI 开发助手</sub></td>
    <td>由 DeepSeek 驱动的 AI 编程助手，负责从环境搭建、代码编写到每次构建与真机验证的全部工程工作。</td>
  </tr>
</table>

---

<div align="center">
<sub>© 2026 Wan · Habit</sub>
</div>
