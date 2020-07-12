package com.acel.streamlivetool.util

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.Process
import androidx.preference.PreferenceManager
import com.acel.streamlivetool.base.MyApplication

val defaultSharedPreferences: SharedPreferences by lazy {
    PreferenceManager.getDefaultSharedPreferences(MyApplication.application)
}
object AppUtil {

    fun runOnUiThread(todo: () -> Unit) {
        Handler(Looper.getMainLooper()).post {
            todo.invoke()
        }
    }

}