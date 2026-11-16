package com.example.habit

import android.content.Context
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * 本地账号系统。
 *
 * 为什么是"本地"？
 *   Habit 目前没有服务器，也不联网。所以账号只存在这台手机里：
 *   · 注册 = 在本机记下 用户名 + 密码的哈希
 *   · 登录 = 在本机核对密码
 *   · 换手机 / 卸载 = 账号消失（这是本地账号的代价）
 *
 * 密码怎么存？
 *   绝不存明文。用 PBKDF2 + 随机盐 迭代 10000 次，只存结果。
 *   即使有人拿到手机里的文件，也反推不出原密码。
 */
data class Account(
    val username: String,
    val salt: String,
    val hash: String,
    val createdAt: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("username", username)
        put("salt", salt)
        put("hash", hash)
        put("createdAt", createdAt)
    }

    companion object {
        fun fromJson(o: JSONObject) = Account(
            username = o.optString("username"),
            salt = o.optString("salt"),
            hash = o.optString("hash"),
            createdAt = o.optLong("createdAt")
        )
    }
}

/** 注册 / 登录的结果 */
sealed class AuthResult {
    object Ok : AuthResult()
    data class Fail(val messageRes: Int) : AuthResult()
}

object AccountStore {

    private const val PREFS = "habit_account"
    private const val KEY_ACCOUNTS = "accounts"
    private const val KEY_CURRENT = "current"

    private const val ITERATIONS = 10000
    private const val KEY_LENGTH = 256

    // ---------------- 账号表 ----------------

    private fun accounts(context: Context): MutableList<Account> {
        val raw = prefs(context).getString(KEY_ACCOUNTS, "") ?: ""
        val list = mutableListOf<Account>()
        if (raw.isBlank()) return list
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) list.add(Account.fromJson(array.getJSONObject(i)))
        }
        return list
    }

    private fun saveAccounts(context: Context, list: List<Account>) {
        val array = JSONArray()
        list.forEach { array.put(it.toJson()) }
        prefs(context).edit().putString(KEY_ACCOUNTS, array.toString()).apply()
    }

    fun hasAnyAccount(context: Context): Boolean = accounts(context).isNotEmpty()

    // ---------------- 当前登录者 ----------------

    /** 当前登录的用户名；没登录返回 null */
    fun currentUser(context: Context): String? =
        prefs(context).getString(KEY_CURRENT, null)?.ifBlank { null }

    fun isLoggedIn(context: Context): Boolean = currentUser(context) != null

    fun logout(context: Context) {
        prefs(context).edit().remove(KEY_CURRENT).apply()
    }

    // ---------------- 注册 / 登录 ----------------

    fun register(context: Context, username: String, password: String): AuthResult {
        val list = accounts(context)
        if (list.any { it.username.equals(username, ignoreCase = true) }) {
            return AuthResult.Fail(R.string.auth_error_exists)
        }

        val salt = newSalt()
        val hash = hash(password, salt)
        list.add(Account(username, salt, hash, System.currentTimeMillis()))
        saveAccounts(context, list)

        // 注册完直接登录
        prefs(context).edit().putString(KEY_CURRENT, username).apply()
        return AuthResult.Ok
    }

    fun login(context: Context, username: String, password: String): AuthResult {
        val account = accounts(context)
            .firstOrNull { it.username.equals(username, ignoreCase = true) }
            ?: return AuthResult.Fail(R.string.auth_error_not_found)

        val hash = hash(password, account.salt)
        if (hash != account.hash) return AuthResult.Fail(R.string.auth_error_wrong_password)

        prefs(context).edit().putString(KEY_CURRENT, account.username).apply()
        return AuthResult.Ok
    }

    /** 删掉当前账号（连带退出）—— 目前没入口，留给以后 */
    fun deleteCurrent(context: Context) {
        val user = currentUser(context) ?: return
        val list = accounts(context).filterNot { it.username == user }
        saveAccounts(context, list)
        logout(context)
    }

    // ---------------- 密码学细节 ----------------

    private fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun hash(password: String, saltBase64: String): String {
        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val key = factory.generateSecret(spec).encoded
        return Base64.encodeToString(key, Base64.NO_WRAP)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
