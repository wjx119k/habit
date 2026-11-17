package com.example.habit

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 自动更新。
 *
 * 能做到什么、做不到什么（先说清楚）：
 *   ✅ 自动去 GitHub 查最新 Release
 *   ✅ 有新版本时下载 APK
 *   ✅ 拉起系统安装器
 *   ❌ 无法"静默安装"——Android 不允许普通应用自己装 APK，
 *      最后一步必须用户点「安装」（这是系统的安全设计，绕不过去）
 *
 * 所以这里的"自动更新"= 自动检查 + 自动下载 + 一键安装。
 */
object UpdateChecker {

    private const val API = "https://api.github.com/repos/wjx119k/habit/releases/latest"

    /** 检查结果 */
    sealed class Result {
        /** 已是最新 */
        object Latest : Result()

        /** 发现新版本：tag 名 + APK 下载地址 */
        data class Newer(val tag: String, val apkUrl: String) : Result()

        /** 检查失败（网络、没有 Release 等） */
        data class Failed(val message: String) : Result()

        /** 仓库里还没有任何 Release */
        object NoRelease : Result()
    }

    /**
     * 查询最新版本。必须在后台线程调用。
     */
    fun check(context: Context): Result {
        val json = runCatching { fetch(API) }.getOrElse {
            return Result.Failed(it.message ?: "网络错误")
        }

        val obj = runCatching { JSONObject(json) }.getOrElse {
            return Result.Failed("服务器返回异常")
        }

        // 没有发布过 Release
        if (obj.has("message")) {
            val msg = obj.optString("message")
            if (msg.contains("Not Found", true)) return Result.NoRelease
            return Result.Failed(msg)
        }

        val tag = obj.optString("tag_name")
        if (tag.isBlank()) return Result.Failed("没有找到版本号")

        val current = currentVersionName(context)
        if (!isNewer(tag, current)) return Result.Latest

        // 找第一个 .apk 资源
        val assets = obj.optJSONArray("assets")
        var apkUrl = ""
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                val name = a.optString("name").lowercase()
                if (name.endsWith(".apk")) {
                    apkUrl = a.optString("browser_download_url")
                    break
                }
            }
        }
        return Result.Newer(tag, apkUrl)
    }

    /** 下载 APK 到缓存目录，返回文件；失败返回 null */
    fun download(context: Context, url: String): File? {
        if (url.isBlank()) return null
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, "habit-latest.apk")

        return runCatching {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20000
                readTimeout = 30000
                instanceFollowRedirects = true
            }
            conn.inputStream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            target
        }.getOrNull()
    }

    /** 拉起系统安装界面 */
    fun install(context: Context, apk: File): Boolean {
        // Android 8+ 需要用户先允许"安装未知应用"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            runCatching {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + context.packageName)
                    )
                )
            }
            return false
        }

        return runCatching {
            val uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                apk
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    // ---------------- 版本比较 ----------------

    /** 把 "v1.1" / "beta1.1" / "1.0" 都归一成数字序列 */
    private fun versionNumbers(text: String): List<Int> =
        Regex("\\d+").findAll(text).map { it.value.toInt() }.toList()

    /**
     * release 的 tag 是否比当前版本新。
     * 例：tag=v1.2，当前=beta1.1 → [1,2] > [1,1] → true
     */
    private fun isNewer(tag: String, current: String): Boolean {
        val a = versionNumbers(tag)
        val b = versionNumbers(current)
        if (a.isEmpty() || b.isEmpty()) return tag != current

        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun currentVersionName(context: Context): String =
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
        }.getOrDefault("0")

    private fun fetch(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Habit-Android")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }
    }
}
