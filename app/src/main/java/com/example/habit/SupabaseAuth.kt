package com.example.habit

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Supabase 账号（只用 REST，不引入任何第三方库）。
 *
 * 安全约定：
 *   · 这里只允许出现 anon public key —— 它是设计上公开的，只受 RLS 策略约束
 *   · service_role key 拥有全库权限，绝不能出现在客户端代码里
 */
object SupabaseAuth {

    const val URL = "https://kxoppvaamdkowaizbige.supabase.co"
    const val ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imt4b3BwdmFhbWRrb3dhaXpiaWdlIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTQ4MDk1MTEsImV4cCI6MjExMDM4NTUxMX0.vzj6_ppb00ddDTikHFR-Kuh1MOk8vz6Xhs0P8pRb7as"

    /** 临时保存 context 供网络层写日志（仅调试用） */
    private lateinit var contextHolder: Context

    private const val PREFS = "habit_supabase"
    private const val KEY_ACCESS = "access_token"
    private const val KEY_REFRESH = "refresh_token"
    private const val KEY_EMAIL = "email"

    /** 用户名 → 内部邮箱（Supabase 只接受邮箱格式） */
    fun toEmail(name: String): String =
        if (name.contains("@")) name else name.trim() + "@habit.local"

    // ---------------- 会话 ----------------

    fun currentEmail(context: Context): String? =
        prefs(context).getString(KEY_EMAIL, null)?.ifBlank { null }

    fun isLoggedIn(context: Context): Boolean = currentEmail(context) != null

    /** 给界面看的名字：去掉内部域名 */
    fun displayName(context: Context): String? =
        currentEmail(context)?.substringBefore("@")?.ifBlank { null }

    fun logout(context: Context) {
        prefs(context).edit().clear().apply()
    }

    private fun saveSession(context: Context, json: JSONObject) {
        prefs(context).edit()
            .putString(KEY_ACCESS, json.optString("access_token"))
            .putString(KEY_REFRESH, json.optString("refresh_token"))
            .putString(KEY_EMAIL, json.optJSONObject("user")?.optString("email") ?: "")
            .apply()
    }

    // ---------------- 注册 / 登录 ----------------

    fun register(context: Context, email: String, password: String): String? {
        val body = JSONObject().put("email", email).put("password", password).toString()
        val (code, text) = post("/auth/v1/signup", body)
        if (code in 200..299) {
            val json = runCatching { JSONObject(text) }.getOrNull()
            if (json?.optString("access_token").isNullOrBlank()) {
                // 项目开了"邮箱确认"，注册成功但还没登录
                return "注册成功，请先去邮箱确认后再登录"
            }
            saveSession(context, json!!)
            return null
        }
        // 诊断：把服务器原文写进文件，方便排查；同时显示在红字里
        val friendly = friendlyError(text)
        debugLog(context, "login code=" + code + " body=" + text.take(300))
        return friendly + " [" + code + ": " + text.take(120) + "]"
    }


    fun login(context: Context, email: String, password: String): String? {
        contextHolder = context.applicationContext
        val body = JSONObject().put("email", email).put("password", password).toString()
        val (code, text) = post("/auth/v1/token?grant_type=password", body)

        // 网络层直接失败（连不上 / 超时）：异常信息在 text 里
        if (code == -1) {
            debugLog(context, "NETWORK FAIL " + text)
            return "网络连接失败：" + text
        }

        if (code in 200..299) {
            val json = runCatching { JSONObject(text) }.getOrNull() ?: return "服务器返回异常"
            saveSession(context, json)
            return null
        }

        debugLog(context, "login code=" + code + " body=" + text.take(300))
        return friendlyError(text) + " [" + code + ": " + text.take(120) + "]"
    }

    /** 把调试信息追加到 files/login_debug.txt */
    private fun debugLog(context: Context, line: String) {
        runCatching {
            val f = java.io.File(context.filesDir, "login_debug.txt")
            f.appendText(
                java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.US)
                    .format(java.util.Date()) + "  " + line + "\n"
            )
        }
    }

    // ---------------- 邮箱验证码（两步注册） ----------------

    /** 第 1 步：给邮箱发验证码；成功返回 null */
    fun sendCode(email: String): String? {
        val body = JSONObject().put("email", email).put("create_user", true).toString()
        val (code, text) = post("/auth/v1/otp", body)
        return if (code in 200..299) null else friendlyError(text)
    }

    /** 第 2 步：校验验证码；成功即登录，返回 null */
    fun verifyCode(context: Context, email: String, token: String): String? {
        val body = JSONObject()
            .put("type", "email")
            .put("email", email)
            .put("token", token)
            .toString()
        val (code, text) = post("/auth/v1/verify", body)
        if (code in 200..299) {
            val json = runCatching { JSONObject(text) }.getOrNull() ?: return "服务器返回异常"
            saveSession(context, json)
            return null
        }
        return friendlyError(text)
    }

    // ---------------- 网络 ----------------

    private fun post(path: String, body: String): Pair<Int, String> {
        return runCatching {
            val conn = URL(URL + path).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("apikey", ANON_KEY)
            conn.setRequestProperty("Authorization", "Bearer $ANON_KEY")
            conn.outputStream.use { it.write(body.toByteArray()) }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            code to text
        }.getOrElse { e ->
            val detail = e.javaClass.simpleName + " / " + (e.message ?: "无消息") +
                " / " + (e.cause?.javaClass?.simpleName ?: "-")
            debugLog(contextHolder, "POST FAIL " + path + " :: " + detail +
                "\n" + e.stackTraceToString().take(500))
            -1 to detail
        }
    }

    /** 把 Supabase 的英文错误翻成人话 */
    private fun friendlyError(raw: String): String {
        val msg = runCatching {
            JSONObject(raw).optString("error_description").ifBlank {
                JSONObject(raw).optString("msg").ifBlank {
                    JSONObject(raw).optString("message")
                }
            }
        }.getOrNull().orEmpty()

        return when {
            msg.contains("Invalid login credentials", true) -> "用户名或密码不正确"
            msg.contains("already registered", true) -> "这个邮箱已经注册过了"
            msg.contains("Email not confirmed", true) -> "请先去邮箱点确认链接"
            msg.contains("rate limit", true) -> "操作太频繁，稍后再试"
            msg.contains("Password should be", true) -> "密码至少 6 位"
            msg.isBlank() -> "登录失败，请稍后再试"
            else -> msg
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
