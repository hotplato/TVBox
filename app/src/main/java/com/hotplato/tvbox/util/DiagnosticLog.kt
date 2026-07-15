package com.hotplato.tvbox.util

import android.util.Log
import android.content.pm.ApplicationInfo
import com.hotplato.tvbox.base.App
import com.orhanobut.hawk.Hawk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DiagnosticLogEntry(
    val timestamp: Long,
    val level: String,
    val module: String,
    val message: String,
    val durationMs: Long? = null,
)

/** Lightweight in-process diagnostics for TV devices where adb is not always available. */
object DiagnosticLog {
    private const val TAG = "TVBoxDiag"
    private const val MAX_ENTRIES = 500
    private val lock = Any()
    private val entries = ArrayDeque<DiagnosticLogEntry>(MAX_ENTRIES)
    private val _logs = MutableStateFlow<List<DiagnosticLogEntry>>(emptyList())
    val logs: StateFlow<List<DiagnosticLogEntry>> = _logs.asStateFlow()

    @JvmStatic
    @JvmOverloads
    fun info(module: String, message: String, durationMs: Long? = null) = append("I", module, message, durationMs)

    @JvmStatic
    @JvmOverloads
    fun warn(module: String, message: String, durationMs: Long? = null) = append("W", module, message, durationMs)

    @JvmStatic
    @JvmOverloads
    fun error(module: String, message: String, durationMs: Long? = null) = append("E", module, message, durationMs)

    @JvmStatic
    fun clear() {
        synchronized(lock) {
            entries.clear()
            _logs.value = emptyList()
        }
    }

    @JvmStatic
    fun exportText(): String = synchronized(lock) {
        entries.asReversed().joinToString("\n") { entry ->
            "${formatTime(entry.timestamp)} ${entry.level}/${entry.module}: ${entry.message}" +
                (entry.durationMs?.let { " (${it}ms)" } ?: "")
        }
    }

    @JvmStatic
    fun redactUrl(value: String?): String {
        if (value.isNullOrBlank()) return ""
        val withoutQuery = value.substringBefore('?').substringBefore('#')
        return if (withoutQuery.length > 160) withoutQuery.take(157) + "..." else withoutQuery
    }

    private fun append(level: String, module: String, message: String, durationMs: Long?) {
        if (!isEnabled()) return
        val entry = DiagnosticLogEntry(System.currentTimeMillis(), level, module, message, durationMs)
        synchronized(lock) {
            if (entries.size == MAX_ENTRIES) entries.removeFirst()
            entries.addLast(entry)
            _logs.value = entries.toList().asReversed()
        }
        val line = "$module: $message" + (durationMs?.let { " (${it}ms)" } ?: "")
        when (level) {
            "E" -> Log.e(TAG, line)
            "W" -> Log.w(TAG, line)
            else -> Log.i(TAG, line)
        }
    }

    /** Debug 包始终记录；Release 包仅在设置中主动开启调试后记录。 */
    private fun isEnabled(): Boolean = try {
        val debugBuild = App.getInstance().applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        debugBuild || Hawk.get(HawkConfig.DEBUG_OPEN, false)
    } catch (_: Throwable) {
        false
    }

    fun formatTime(timestamp: Long): String =
        SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
}
