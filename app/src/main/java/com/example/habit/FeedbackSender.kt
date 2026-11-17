package com.example.habit

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * 应用内反馈：登录用户写完直接提交，不需要邮箱。
 *
 * 数据落到 Supabase 的 feedback 表：
 *   · 只允许"已登录用户写入"，RLS 保证 user_id 一定等于 auth.uid()
 *   · 客户端只能写、不能读别人的；作者在 Supabase 控制台里看
 */
object FeedbackSender {

    private const val TABLE = "/rest/v1/feedback"

    /** 提交成功返回 null，否则返回错误提示 */
    fun send(context: Context, message: String): String? {
        val token = accessToken(context) ?: return "请先登录再提交反馈"
        val userId = userId(token) ?: return "登录状态异常，请退出后重新登录"

        val body = JSONObject().apply {
            put("user_id", userId)
            put("message", message)
            put("app_version", versionName(context))
            put("device", Build.MODEL + " / Android " + Build.VERSION.RELEASE)
        }.toString()

        val (code, text) = post(token, body)
        return if (code in 200..299) null else "提交失败(" + code + ")：" + text.take(120)
    }

    // ---------------- 内部 ----------------

    private fun accessToken(context: Context): String? =
        context.getSharedPreferences("habit_supabase", Context.MODE_PRIVATE)
            .getString("access_token", null)?.ifBlank { null }

    private fun userId(token: String): String? = runCatching {
        val parts = token.split(".")
        if (parts.size < 2) return null
        var seg = parts[1]
        while (seg.length % 4 != 0) seg += "="
        val json = String(
            android.util.Base64.decode(
                seg,
                android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or
                    android.util.Base64.NO_PADDING
            )
        )
        JSONObject(json).optString("sub").ifBlank { null }
    }.getOrNull()

    private fun versionName(context: Context): String = runCatching {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    }.getOrDefault("?")

    private fun post(token: String, body: String): Pair<Int, String> = runCatching {
        val conn = (URL(SupabaseAuth.URL + TABLE).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 15000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("apikey", SupabaseAuth.ANON_KEY)
            setRequestProperty("Authorization", "Bearer " + token)
            setRequestProperty("Prefer", "return=minimal")
        }
        conn.outputStream.use { it.write(body.toByteArray()) }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
        code to text
    }.getOrElse { -1 to (it.message ?: "网络错误") }
}
