package com.fillit.app.model

import android.content.Context
import android.content.SharedPreferences

object SessionManager {

    private const val PREFS_NAME = "user_prefs"
    private const val KEY_USER_ID = "user_id"
    private var prefs: SharedPreferences? = null

    // Call this from the Application class
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveUserId(userId: String) {
        prefs?.edit()?.putString(KEY_USER_ID, userId)?.apply()
    }

    val userId: String?
        get() = prefs?.getString(KEY_USER_ID, null)

    fun clear() {
        prefs?.edit()?.clear()?.apply()
    }
}
