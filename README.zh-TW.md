<div align="center">

<img src="docs/icon.png" width="104" alt="Habit 圖示" />

# Habit · 好習慣

**A good habit builds everything**

一款用積分、成就和一點點浪漫陪你把好習慣堅持下去的 Android 應用

![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF?logo=kotlin&logoColor=white)
![Material 3](https://img.shields.io/badge/Material-3-757575?logo=materialdesign&logoColor=white)
![Version](https://img.shields.io/badge/version-1.1-blue)
![License](https://img.shields.io/badge/license-MIT-green)

[简体中文](README.md) · **繁體中文** · [English](README.en.md) · [日本語](README.ja.md) · [Русский](README.ru.md)

</div>

---

## 它是什麼

想早睡、想讀書、想運動——道理都懂，堅持很難。

Habit 把「堅持」變成看得見的數字：

- **每完成一次，加分**；每違背一次，減分
- 攢下的積分能**兌換成自己定的小獎勵**（看一集劇、一杯奶茶、睡個懶覺）
- 連續堅持會解鎖**成就**，也會被記錄成**連續天數**
- 習慣資料全部存在你手機本機；登入只用於身分與回饋，**不做雲端同步**

---

## 功能

### 計畫管理

- 建立計畫：名稱、信念、契機、每次加分數、每次減分數、起始日期
- 卡片上直接 **加分 / 減分**，帶 🎉 和 💪 鼓勵浮層
- 連續加分計數（減分即清零），並記錄歷史最長連續
- 編輯、刪除（刪除會提示積分一起清空）

### 積分與商店

- 總積分 = 所有計畫積分之和 + 臨時任務獎勵 − 已兌換
- 商店裡的**獎勵由你自己建立**：想兌換什麼、需要多少分，都由你定
- 兌換有二次確認，扣分即時同步到頂部積分

### 成就

| 成就 | 條件 |
|---|---|
| 小試牛刀 | 單一計畫連續加分 10 次 |
| 自律專家 | 單一計畫連續加分 100 次 |
| 自律大師 | 單一計畫連續加分 1000 次 |
| 自律大神 | 所有計畫累計連續加分 5000 次 |

解鎖後永久保留，也可以一鍵清空重新挑戰。

### 工具頁

- **番茄鐘**：25 分鐘專注 + 5 分鐘休息，到點走系統鬧鐘（鬧鐘鈴聲），離開頁面再回來接著計時
- **記憶卡片**：自訂問答卡片，翻牌複習
- **艾森豪威爾法則**：重要 / 緊急四象限，與臨時任務共用一套資料

### 臨時任務

- 一次性任務：做完自動劃掉並加分
- 可設 **截止日期**（逾期標紅）與 **提醒時間**（系統鬧鐘，通知帶「停止鈴聲」）
- 按截止日期排序，快到期的靠前
- **自動緊急**：設定裡可選 N 天，臨近截止的非緊急任務會自動改寫象限（可回退）
- 列表可折疊，收起時顯示 `🔥 緊急數 / 未完成數`

### 帳號與回饋（可選）

- 用 Supabase 做身分驗證；使用者名稱自動對應為 `使用者名稱@habit.local`
- 登入後可**在應用內直接給作者發回饋**（不需要信箱）
- 登入前有**滑桿人機驗證**
- **不做雲端資料同步**：資料只在本機，可用「設定 → 資料與備份」匯出 JSON

### 其他

- **5 種語言**：简体中文 / 繁體中文 / English / 日本語 / Русский
- **深色 / 淺色 / 跟隨系統**
- 動態配色（Android 12+ 跟隨桌布）
- 使用者條款、隱私政策、開源授權（獨立閱讀頁，多語言）
- 開發者列表與詳情頁

---

## 三個彩蛋

<details>
<summary>⚠️ 這裡有劇透，想自己探索就別點開</summary>

<br/>

**① 你瞅啥呢**

`我的 → 彩蛋` 進去是一片空白，只有一個 🙃 和一句話。
表情會緩慢浮動；連點 5 下，它就不裝了（🙃 → 🤪），並把出口交給你。

**② 某些地方似乎發生了變化**

前置條件：**進過彩蛋①** + **解鎖「小試牛刀」**。

在「計畫」頁連點左上角 App 圖示 3 下（每下都有震動），第 3 下彈出提示。

**③ 冬天到了**

彩蛋②觸發後，去「我的」點**任意一行選單**，整個螢幕會被劫持：

> 黑場漸亮 → 雪夜、月亮、松林、亮著燈的雪中小屋 → 開始下雪
> → 四句詩直排逐字浮現（冬天到了 / 雪花飄揚 / 我的所愛 / 你在哪裡）
> → 落款印章 → 緩慢黑屏收束，共 18.5 秒

**整段動畫沒有使用任何圖片素材**，全部是 `Canvas` 手繪：天空漸層、星星閃爍、月亮環形山、遠山貝茲曲線、松樹積雪、炊煙、飄雪粒子、暖黃窗光。

</details>

---

## 技術棧

| 項目 | 說明 |
|---|---|
| 語言 | Kotlin |
| 建置 | Gradle 9 + AGP 9（內建 Kotlin 支援） |
| UI | XML 版面 + Material 3（不使用 Compose） |
| 目標 | minSdk 24 / targetSdk 37 / compileSdk 37 |
| 儲存 | SharedPreferences + org.json（無資料庫）；帳號與回饋走 Supabase REST |
| 動畫 | View 動畫 + 自訂 View `onDraw` |
| 依賴 | AndroidX + Material（無第三方 SDK，網路層用原生 HttpURLConnection） |

---

## 建置

**環境需求**：JDK 21+、Android SDK 37

```bash
# 除錯版（可直接安裝）
./gradlew assembleDebug
# 產物：app/build/outputs/apk/debug/app-debug.apk

# 正式版（需自備簽章）
./gradlew assembleRelease
```

### 關於簽章

倉庫裡**不包含**簽章金鑰。想打正式包，請先產生自己的 keystore：

```bash
keytool -genkeypair -v -keystore habit-release.jks \
  -storetype JKS -keyalg RSA -keysize 2048 -validity 10000 \
  -alias habit -storepass <你的密碼> -keypass <你的密碼> \
  -dname "CN=YourName, OU=Habit, O=YourName, C=CN"
```

然後在 `app/build.gradle.kts` 的 `signingConfigs` 裡改成你的路徑與密碼。

> **keystore 一旦遺失，你就再也無法更新這個 App**——系統要求同一包名的新版本必須用同一把金鑰簽章。請單獨備份。

---

## 說明

- 不讀取通訊錄、相簿、位置；無廣告、無埋點統計。僅使用網路權限登入與提交回饋
- 解除安裝或清除資料會永久刪除所有紀錄，請自行留意
- 本專案在真機（榮耀 BKQ-AN10 / Android 17）上完成全部功能驗證

---

<div align="center">
<sub>© 2026 Wan · Habit</sub>
</div>
