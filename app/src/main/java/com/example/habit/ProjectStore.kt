package com.example.habit

import android.content.Context

/**
 * 负责把项目列表存到手机里、再从手机里读出来。
 *
 * SharedPreferences（简称 SP）是安卓最简单的本地存储：
 * 本质就是手机里一个私有的键值对文件，App 卸载才会消失。
 * 适合存"少量、结构简单"的数据，比如设置项、几条待办。
 *
 * object 关键字表示"单例"：整个 App 只有这一份，直接 HabitStore 就能用，
 * 不需要 new。
 */
object ProjectStore {

    private const val PREFS_NAME = "habit_store"
    private const val KEY_PROJECTS = "projects"

    /** 读取所有项目；没存过就返回空列表 */
    fun load(context: Context): MutableList<Project> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_PROJECTS, "") ?: ""
        return Project.listFromJson(raw)
    }

    /** 保存整个列表（覆盖式） */
    fun save(context: Context, projects: List<Project>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PROJECTS, Project.listToJson(projects))
            .apply()
    }
}
