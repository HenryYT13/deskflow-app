package com.deskflow.app.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.deskflow.app.R
import com.deskflow.app.data.api.ApiClientFactory
import com.deskflow.app.data.local.SecurePreferences
import com.deskflow.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * (#177) 2nd Android Home Widget: Displays Today's Timetable per-period,
 * Uniform & Dismissal Time directly on the home screen.
 */
class DeskFlowTkbWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH_TKB_WIDGET = "com.deskflow.app.widget.ACTION_REFRESH_TKB"

        private fun buildRefreshPending(context: Context): PendingIntent {
            val intent = Intent(context, DeskFlowTkbWidgetProvider::class.java).apply {
                action = ACTION_REFRESH_TKB_WIDGET
            }
            return PendingIntent.getBroadcast(
                context,
                201,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun updateTkbViews(
            context: Context,
            manager: AppWidgetManager,
            appWidgetIds: IntArray,
            dayHeader: String,
            dismissalText: String,
            uniformText: String,
            bodyText: String
        ) {
            val openAppIntent = Intent(context, MainActivity::class.java)
            val openPending = PendingIntent.getActivity(
                context,
                200,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            appWidgetIds.forEach { widgetId ->
                val views = RemoteViews(context.packageName, R.layout.widget_deskflow_tkb)
                views.setTextViewText(R.id.tkb_widget_day, dayHeader)
                views.setTextViewText(R.id.tkb_widget_dismissal, dismissalText)
                views.setTextViewText(R.id.tkb_widget_uniform, uniformText)
                views.setTextViewText(R.id.tkb_widget_body, bodyText)
                views.setOnClickPendingIntent(R.id.tkb_widget_root, openPending)
                views.setOnClickPendingIntent(R.id.tkb_widget_refresh, buildRefreshPending(context))
                manager.updateAppWidget(widgetId, views)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateTkbViews(
            context,
            appWidgetManager,
            appWidgetIds,
            "TKB HÔM NAY",
            "Tan: 11:35",
            "Đang tải đồng phục...",
            "Nhấn Sync để cập nhật TKB từng tiết"
        )
        refreshAsync(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_TKB_WIDGET) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, DeskFlowTkbWidgetProvider::class.java))
            refreshAsync(context, manager, ids)
        }
    }

    private fun refreshAsync(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = SecurePreferences(context)
                if (prefs.deviceToken.isNullOrBlank()) {
                    updateTkbViews(context, manager, ids, "TKB HÔM NAY", "--", "Chưa ghép nối", "Mở app DeskFlow để ghép nối")
                    return@launch
                }
                val api = ApiClientFactory.create(prefs)
                val res = runCatching { api.getTodayTimetable() }.getOrNull()
                val today = res?.body()?.data?.today
                if (today == null) {
                    updateTkbViews(context, manager, ids, "TKB HÔM NAY", "--", "Không có dữ liệu", "Nhấn Sync để thử lại")
                    return@launch
                }
                val m = today.morning.filter { it.isNotBlank() }
                val a = today.afternoon.filter { it.isNotBlank() }
                val dismissal = when {
                    a.isNotEmpty() -> "Tan: 16:25"
                    m.isNotEmpty() -> "Tan: 11:35"
                    else -> "Nghỉ"
                }
                val body = buildString {
                    if (m.isNotEmpty()) append("Sáng: ${m.joinToString(", ")}")
                    if (a.isNotEmpty()) {
                        if (isNotEmpty()) append("\n")
                        append("Chiều: ${a.joinToString(", ")}")
                    }
                    if (isEmpty()) append("Hôm nay không có tiết học.")
                }
                updateTkbViews(
                    context,
                    manager,
                    ids,
                    "TKB • ${today.day.uppercase()}",
                    dismissal,
                    "Đ.phục: ${today.uniformMorning}${if (today.uniformAfternoon.isNotBlank()) " / ${today.uniformAfternoon}" else ""}",
                    body
                )
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }
}
