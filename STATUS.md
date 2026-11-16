# Habit 项目状态

> 本文件记录"现在到哪一步了"。上下文丢失或隔几天回来时，先读这里。

---

## ⚠️ 版本状态（重要）

```text
beta 1.0   ✅ 已发布（main 分支）—— 用户手上的就是这个
beta 1.1   🚧 开发中，尚未完成，未发布（feature/beta-1.1 分支）
```

**beta 1.1 不叫"正式版"，只能算开发快照。** 想发的时候才合并到 main、打 tag、打正式包。

| 分支 | 内容 | 能不能发 |
|---|---|---|
| `main` | beta 1.0 全部源码 | ✅ 随时可发 |
| `feature/beta-1.1` | 1.1 的全部新功能（开发中） | ❌ 未完成 |

---

## 已完成（beta 1.0 基线）

| 模块 | 说明 |
|---|---|
| 计划 | 增删改、加减分、连续次数 |
| 商店 | 自定义奖励、兑换、撤销兑换 |
| 成就 | 4 个，连续加分解锁 |
| 其他 | 5 种语言、深浅色主题、3 个彩蛋 |

## beta 1.1 新增（截至当前提交）

| 模块 | 说明 |
|---|---|
| 计划 | 长按拖动排序（RecyclerView + ItemTouchHelper）；积分支持一位小数（0.1~999.9） |
| 历史 | 总积分 / 单计划曲线（PointsChartView 手绘）、流水、按计划筛选 |
| 备份 | 导出 JSON + 分享 + 从文件导入 |
| 账号 | Supabase 邮箱密码注册登录；用户名自动映射为 `用户名@habit.local` |
| 注销 | 删云端 `user_data` + 清本机 + 退出 |
| 同步 | 登录/注册时整份 JSON 推送与拉取 |
| 顶栏 | 图标 / 积分 / 头像在三个标签页通用；"+" 仅留在计划页 |
| 个人主页 | 圆形头像 + 用户名；未登录显示"登录/注册"，已登录变"退出登录" |
| 成就弹窗 | Steam 风格，从顶部滑入，3 秒自动消失 |
| 隐私页 | 设置 → 隐私，完整说明数据去向（不再是占位 Toast） |
| 通知页 | 设置 → 通知，AlarmManager 每日提醒 + 测试按钮 + 开机重排 |
| 协议 | 首次启动弹出用户协议，同意后才进主界面（`TERMS_VERSION` 可升级重问） |
| 彩蛋 | 时长 27.5 秒：镜头推近 + 雪夜归人 + 开门进屋 + 冷风/钢琴/开关门音效（全部程序化合成，零音频资源） |

---

## 已知问题

| # | 问题 | 状态 |
|---|---|---|
| 1 | 注册后再次登录报"登录失败，请稍后再试" | 已加 `[HTTP xxx]` 诊断输出，待复现定位 |
| 2 | 加分/兑换等日常改动**不会自动同步云端** | 未做，当前只有登录/注册时同步一次 |
| 3 | 未登录也能使用全部功能 | 产品决策待定 |
| 4 | 登录页仍有"邮箱"字样残留 | 未清理 |

---

## 技术要点

- **云同步**：整份 JSON 存 `user_data.payload`，靠 RLS `auth.uid() = user_id` 隔离；只使用 anon key，service_role 绝不进客户端
- **签名**：debug 与 release 用同一把 `habit-release.jks`，可覆盖安装保数据；**jks 不在 git 里**
- **R8**：release 关闭了包优化（`optimization { enable = false }`），否则启动崩溃 `IllegalAccessError`
- **音频**：`PianoMusic`（钢琴）与 `WinterAudio`（风声/门声）全部用 `AudioTrack` 现场合成，不打包音频文件
- **页面刷新**：`MainActivity.onResume()` 必须重新读盘，否则跨页面改数据（如清空成就）后会用到过期内存

---

## 下一步

1. 修登录 bug（看诊断输出里的 HTTP 状态码）
2. 日常改动防抖推送云端（这是目前最"不合逻辑"的地方：用户以为同步了，其实没有）
3. 决定是否强制登录
4. 清理文案残留
5. 全部就绪后：合并 main → 打 tag `v1.1` → `assembleRelease` → 更新 README

---

## 常用命令

```cmd
cd C:\Users\wandg\AndroidStudioProjects\Habit
git status                 # 看改了什么
gradlew.bat :app:assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
git push                   # 已绑定远程分支，直接推
```
