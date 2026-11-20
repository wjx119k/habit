<div align="center">

<img src="docs/icon.png" width="104" alt="Habit icon" />

# Habit

**A good habit builds everything**

An Android app that helps you keep good habits with points, achievements and a little romance

![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF?logo=kotlin&logoColor=white)
![Material 3](https://img.shields.io/badge/Material-3-757575?logo=materialdesign&logoColor=white)
![Version](https://img.shields.io/badge/version-1.1-blue)
![License](https://img.shields.io/badge/license-MIT-green)

[简体中文](README.md) · [繁體中文](README.zh-TW.md) · **English** · [日本語](README.ja.md) · [Русский](README.ru.md)

</div>

---

## What it is

You know you should sleep earlier, read more, move more. Knowing is easy — keeping it up is hard.

Habit turns "keeping it up" into numbers you can see:

- **Every completion earns points**, every slip costs points
- Points can be **redeemed for small rewards you define yourself** (one episode, a milk tea, a lazy morning)
- Streaks unlock **achievements** and are recorded as **consecutive days**
- Habit data stays on your phone. Signing in is only for identity and feedback — **there is no cloud sync**

---

## Features

### Plans

- Create a plan: name, belief, trigger, points per completion / per slip, start date
- **+ / −** directly on the card, with encouraging overlays
- Streak counter (reset on a slip) and all-time best streak
- Edit and delete (deleting warns that its points go away)

### Points & Store

- Total points = sum of all plan points plus one-off task rewards, minus redeemed rewards
- **You create the rewards yourself** — what to redeem and how much it costs
- Redemption asks for confirmation; points update instantly

### Achievements

| Achievement | Condition |
|---|---|
| First steps | 10 consecutive completions on one plan |
| Disciplined | 100 consecutive completions on one plan |
| Master | 1000 consecutive completions on one plan |
| Legend | 5000 consecutive completions across all plans |

Unlocked achievements stay forever, and you can clear them to start over.

### Tools

- **Pomodoro**: 25 min focus + 5 min break, rings through the system alarm; keeps counting when you leave the screen
- **Flashcards**: your own question/answer cards, flip to review
- **Eisenhower matrix**: important / urgent quadrants, sharing data with one-off tasks

### One-off tasks

- One-time tasks: tick them off and earn points immediately
- Optional **due date** (overdue shown in red) and **reminder time** (system alarm; the notification has a "Stop ringing" button)
- Sorted by due date, the soonest first
- **Auto-urgent**: pick N days in Settings; non-urgent tasks due within N days are moved into an urgent quadrant (reversible)
- Collapsible list; when collapsed it shows `🔥 urgent / open`

### Account & feedback (optional)

- Supabase handles authentication; a username maps to `username@habit.local`
- When signed in you can **send feedback from inside the app** — no email client needed
- A slider check runs before login
- **No cloud sync**: data lives on this device; export JSON from Settings → Data & backup

### Other

- **5 languages**: 简体中文 / 繁體中文 / English / 日本語 / Русский
- **Dark / light / follow system**
- Dynamic colour (Android 12+ wallpaper colours)
- In-app terms of service, privacy policy and open-source licences (all localized)
- Developer list and detail pages

---

## Three easter eggs

<details>
<summary>⚠️ Spoilers ahead — don't open if you want to find them yourself</summary>

<br/>

**① What are you looking at**

`Me → Easter egg` opens a blank screen with a single 🙃 and one line of text.
The face floats slowly; tap it five times and it drops the act (🙃 → 🤪) and hands you the exit.

**② Something seems different**

Prerequisite: **visited easter egg ①** and **unlocked "First steps"**.

Tap the app icon in the top-left of the Plans page three times (each tap vibrates); the third shows a hint.

**③ Winter has come**

After ②, tap **any row in the Me page** and the whole screen is taken over:

> fade in from black → snowy night, moon, pine forest, a lit cabin → it starts to snow
> → four lines of poetry appear vertically, character by character
> → a seal stamp → slow fade to black, 18.5 s in total

**No image assets are used** — everything is drawn with `Canvas`: sky gradient, twinkling stars, lunar craters, distant mountains, snow-laden pines, chimney smoke, snow particles, warm window light.

</details>

---

## Tech stack

| Item | Notes |
|---|---|
| Language | Kotlin |
| Build | Gradle 9 + AGP 9 (built-in Kotlin) |
| UI | XML layouts + Material 3 (no Compose) |
| Targets | minSdk 24 / targetSdk 37 / compileSdk 37 |
| Storage | SharedPreferences + org.json (no database); account & feedback over Supabase REST |
| Animation | View animations + custom `onDraw` |
| Dependencies | AndroidX + Material (no third-party SDK; networking uses plain HttpURLConnection) |

---

## Build

**Requirements**: JDK 21+, Android SDK 37

```bash
# Debug build (installable)
./gradlew assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk

# Release build (bring your own signing key)
./gradlew assembleRelease
```

### Signing

The repository does **not** contain a signing key. To build a release APK, generate your own keystore:

```bash
keytool -genkeypair -v -keystore habit-release.jks \
  -storetype JKS -keyalg RSA -keysize 2048 -validity 10000 \
  -alias habit -storepass <your-password> -keypass <your-password> \
  -dname "CN=YourName, OU=Habit, O=YourName, C=CN"
```

Then point `signingConfigs` in `app/build.gradle.kts` at it.

> **Lose the keystore and you can never update this app again** — Android requires the same key for every new version of the same package name. Back it up separately.

---

## Notes

- No access to contacts, photos or location; no ads, no analytics. The network permission is used only for sign-in and feedback
- Uninstalling or clearing data deletes everything permanently
- Every feature was verified on a real device (Honor BKQ-AN10 / Android 17)

---

<div align="center">
<sub>© 2026 Wan · Habit</sub>
</div>
