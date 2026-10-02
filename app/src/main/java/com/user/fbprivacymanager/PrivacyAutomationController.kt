package com.user.fbprivacymanager

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class AutomationLogEntry(
    val timestamp: Long,
    val message: String
)

object PrivacyAutomationController {
    private const val PREFERENCES = "privacy_manager_activity"
    private const val LOG_KEY = "entries"
    private const val MAX_ENTRIES = 100

    private val lock = Any()
    private val mutableEntries = MutableStateFlow<List<AutomationLogEntry>>(emptyList())
    private val mutableRunning = MutableStateFlow(false)
    private val entriesPreferences get() = preferences

    @Volatile
    private var preferences: android.content.SharedPreferences? = null

    val entries: StateFlow<List<AutomationLogEntry>> = mutableEntries.asStateFlow()
    val runningState: StateFlow<Boolean> = mutableRunning.asStateFlow()

    val running: Boolean
        get() = mutableRunning.value

    @Volatile
    var dryRun: Boolean = true
        private set

    fun initialize(context: Context) {
        synchronized(lock) {
            if (preferences != null) return
            preferences = context.applicationContext.getSharedPreferences(
                PREFERENCES,
                Context.MODE_PRIVATE
            )
            mutableEntries.value = readEntries(entriesPreferences?.getString(LOG_KEY, null))
        }
    }

    fun start(isDryRun: Boolean) {
        dryRun = isDryRun
        mutableRunning.value = true
        log(if (isDryRun) "Dry run started. No audience changes will be made." else
            "Write mode started. Only positively identified audience controls may be used.")
    }

    fun stop(message: String = "Stopped by user.") {
        val wasRunning = mutableRunning.value
        mutableRunning.value = false
        if (wasRunning) log(message)
    }

    fun log(message: String) {
        synchronized(lock) {
            val updated = (mutableEntries.value + AutomationLogEntry(
                timestamp = System.currentTimeMillis(),
                message = message
            )).takeLast(MAX_ENTRIES)
            mutableEntries.value = updated
            val serialized = JSONArray().apply {
                updated.forEach { entry ->
                    put(JSONObject().put("timestamp", entry.timestamp).put("message", entry.message))
                }
            }
            entriesPreferences?.edit()?.putString(LOG_KEY, serialized.toString())?.apply()
        }
    }

    fun clearLog() {
        synchronized(lock) {
            mutableEntries.value = emptyList()
            entriesPreferences?.edit()?.remove(LOG_KEY)?.apply()
        }
    }

    private fun readEntries(serialized: String?): List<AutomationLogEntry> {
        if (serialized.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(serialized)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                AutomationLogEntry(
                    timestamp = item.optLong("timestamp"),
                    message = item.optString("message")
                )
            }.takeLast(MAX_ENTRIES)
        }.getOrDefault(emptyList())
    }
}
