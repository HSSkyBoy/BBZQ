package io.github.biliz

import android.content.Context

object LinkerGuard {
    fun hasConflict(context: Context): Boolean = false

    fun triggerConflict(context: Context) {}

    fun isFrameworkEnvironmentAbnormal(frameworkVersionCode: String, frameworkVersion: String): Boolean = false
}
