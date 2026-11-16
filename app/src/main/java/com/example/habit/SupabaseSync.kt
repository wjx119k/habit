package com.example.habit

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * 云同步：把本机数据整份存到 Supabase 的一行里。
 *
 * 为什么整份存？
 *   数据只有几 KB，一张表一行 JSON 最简单，也不会出现"同步到一半"的脏数据。
 *
 * 安全：
 *   只带 anon key + 用户的 access_token；
 *   数据能不能被别人读到，完全由数据库的 RLS 策略决定（auth.uid() = user_id）。
 */
object SupabaseSync {

    private const val TABLE = "/rest/v1/user_data"

    /** 拉取云端数据；没有则返回 null */
    fun pull(context: Context): String? {
        val userId = currentUserId(context) ?: return null
        val token = token(context) ?: return null

        val url = SupabaseAuth.URL + TABLE + "?user_id=eq." + userId + "&select=payload"
        val (code, text) = request("GET", url, null, token)
        if (code !in 200..299) return null

        return runCatching {
            val array = JSONArray(text)
            if (array.length() == 0) null
            else array.getJSONObject(0).opt("payload")?.toString()
        }.getOrNull()
    }

    /** 把本机数据整份推上去（有则覆盖，无则新建） */
    fun push(context: Context): Boolean {
        val userId = currentUserId(context) ?: return false
        val token = token(context) ?: return false

        val payload = Backup.toJson(context)
        val body = JSONObject().apply {
            put("user_id", userId)
            put("payload", JSONObject(payload))
            put("updated_at", System.currentTimeMillis())
        }.toString()

        // 用 upsert：不存在就插入，存在就覆盖
        val url = SupabaseAuth.URL + TABLE + "?on_conflict=user_id"
        val (code, _) = request("POST", url, body, token, prefer = "resolution=merge-duplicates")
        return code in 200..299
    }

    /** 用云端数据覆盖本机（登录时用） */
    fun restore(context: Context, payload: String): String? = Backup.restore(context, payload)


    /** 删除云端那一行数据（注销账号时用） */
    fun deleteRemote(context: Context): Boolean {
        val userId = currentUserId(context) ?: return false
        val token = token(context) ?: return false
        val url = SupabaseAuth.URL + TABLE + "?user_id=eq." + userId
        val (code, _) = request("DELETE", url, null, token)
        return code in 200..299
    }

    /** 清空本机全部业务数据（注销账号时用） */
    fun wipeLocal(context: Context) {
        ProjectStore.save(context, emptyList())
        HistoryStore.clear(context)
        StoreStore.clear(context)
        AchievementStore.clear(context)
        WinterEgg.reset(context)
    }

    // ---------------- 会话信息 ----------------

    private fun token(context: Context): String? =
        context.getSharedPreferences("habit_supabase", Context.MODE_PRIVATE)
            .getString("access_token", null)?.ifBlank { null }

    /** 从 JWT 里解出用户 id（JWT 的第二段是 base64 的 payload） */
    private fun currentUserId(context: Context): String? {
        val jwt = token(context) ?: return null
        return runCatching {
            val parts = jwt.split(".")
            if (parts.size < 2) return null
            val json = String(android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE))
            JSONObject(json).optString("sub").ifBlank { null }
        }.getOrNull()
    }

    // ---------------- 网络 ----------------

    private fun request(
        method: String,
        url: String,
        body: String?,
        token: String,
        prefer: String? = null
    ): Pair<Int, String> = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.setRequestProperty("apikey", SupabaseAuth.ANON_KEY)
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Content-Type", "application/json")
        prefer?.let { conn.setRequestProperty("Prefer", it) }

        if (body != null) {
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toByteArray()) }
        }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
        code to text
    }.getOrElse { -1 to (it.message ?: "网络错误") }
}
