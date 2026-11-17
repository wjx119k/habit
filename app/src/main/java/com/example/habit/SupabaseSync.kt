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
        val userId = currentUserId(context)
        if (userId == null) { log(context, "push: 拿不到用户 id（JWT 解码失败）"); return false }
        val token = token(context)
        if (token == null) { log(context, "push: 没有 access_token"); return false }

        val payload = runCatching { Backup.toJson(context) }.getOrElse {
            log(context, "push: 生成本机数据失败 " + it); return false
        }

        val body = runCatching {
            JSONObject().apply {
                put("user_id", userId)
                put("payload", JSONObject(payload))
                // 必须传 ISO-8601 字符串：数据库列是 timestamptz，
                // 传毫秒整数会报 "date/time field value out of range"
                put(
                    "updated_at",
                    java.time.Instant.ofEpochMilli(System.currentTimeMillis()).toString()
                )
            }.toString()
        }.getOrElse {
            log(context, "push: 组装 JSON 失败 " + it); return false
        }

        val url = SupabaseAuth.URL + TABLE + "?on_conflict=user_id"
        // 保护：本机没有任何计划也没有任何历史时，绝不覆盖云端
        // （清空 App 数据后重新登录就是这种情形，一推就把云端数据冲掉了）
        val emptyLocal = runCatching {
            val o = JSONObject(payload)
            (o.optJSONArray("plans")?.length() ?: 0) == 0 &&
                (o.optJSONArray("history")?.length() ?: 0) == 0
        }.getOrDefault(true)
        if (emptyLocal) {
            log(context, "push skipped: 本机为空，避免覆盖云端")
            return false
        }

        val (code, text) = request("POST", url, body, token, prefer = "resolution=merge-duplicates")
        log(context, "push code=" + code + " body=" + text.take(200))
        return code in 200..299
    }

    /** 同步诊断日志 */
    private fun log(context: Context, line: String) {
        runCatching {
            val f = java.io.File(context.filesDir, "sync_debug.txt")
            f.appendText(
                java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.US)
                    .format(java.util.Date()) + "  " + line + "\n"
            )
        }
    }

    /** 用云端数据覆盖本机（登录时用） */
    fun restore(context: Context, payload: String): String? = Backup.restore(context, payload)


    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pendingPush: Runnable? = null

    /**
     * 防抖推送：连续操作只在上一次之后 2 秒推一次。
     *
     * 为什么防抖？
     *   用户可能连着点七八次加分，每次都发一个请求既费流量又容易超时。
     *   等手停下来再推，既省事又不丢数据。
     */
    fun pushDebounced(context: Context) {
        if (!SupabaseAuth.isLoggedIn(context)) return

        pendingPush?.let { mainHandler.removeCallbacks(it) }
        val app = context.applicationContext
        val runnable = Runnable {
            Thread { runCatching { push(app) } }.start()
        }
        pendingPush = runnable
        mainHandler.postDelayed(runnable, 150L)
    }

    /**
     * 立刻推送（不等防抖）。
     * 用于 App 退到后台、退出登录、清数据之前——避免"刚改完就切走"把改动丢掉。
     */
    fun flush(context: Context) {
        if (!SupabaseAuth.isLoggedIn(context)) return
        pendingPush?.let { mainHandler.removeCallbacks(it) }
        pendingPush = null
        val app = context.applicationContext
        Thread { runCatching { push(app) } }.start()
    }

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
            // JWT 的 payload 是"无填充"的 base64url，必须先补上 = 才能解码，
            // 否则 Base64.decode 会抛异常，用户 id 拿不到 -> 整个同步静默失败
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
