package com.deskflow.app.data.local

import android.content.Context
import com.deskflow.app.domain.model.ActivityItem
import com.deskflow.app.domain.model.ActivityType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable

@Serializable
private data class PersistedEvent(
    val id: String,
    val typeName: String,
    val timestamp: Long,
    val description: String,
    val details: String? = null
)

class ActivityStore(context: Context) {

    private val prefs = context.getSharedPreferences("deskflow_activity_log", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _events = MutableStateFlow<List<ActivityItem>>(loadEvents())
    val events: StateFlow<List<ActivityItem>> = _events.asStateFlow()

    @Synchronized
    fun logEvent(type: ActivityType, description: String, details: String? = null) {
        val item = ActivityItem(
            type = type,
            description = description,
            details = details
        )
        val current = _events.value.toMutableList()
        current.add(0, item)
        // Keep at most 100 recent entries to conserve memory and disk
        val trimmed = if (current.size > 100) current.take(100) else current
        _events.value = trimmed
        persist(trimmed)
    }

    private fun loadEvents(): List<ActivityItem> {
        val raw = prefs.getString("events_json", null) ?: return emptyList()
        return try {
            val list = json.decodeFromString<List<PersistedEvent>>(raw)
            list.map { p ->
                ActivityItem(
                    id = p.id,
                    type = ActivityType.valueOf(p.typeName),
                    timestamp = p.timestamp,
                    description = p.description,
                    details = p.details
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun persist(items: List<ActivityItem>) {
        try {
            val dtoList = items.map {
                PersistedEvent(
                    id = it.id,
                    typeName = it.type.name,
                    timestamp = it.timestamp,
                    description = it.description,
                    details = it.details
                )
            }
            prefs.edit().putString("events_json", json.encodeToString(dtoList)).apply()
        } catch (_: Exception) {}
    }
}
