package com.fillit.app

import android.util.Log
import kotlin.system.exitProcess

object CrashLogger {
    private const val TAG = "CrashLogger"
    fun init() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "Uncaught crash in thread=${thread.name}", throwable)
            // Forward to original (may show system crash dialog)
            defaultHandler?.uncaughtException(thread, throwable) ?: run {
                // Fallback exit to avoid silent freeze
                exitProcess(2)
            }
        }
    }
}

