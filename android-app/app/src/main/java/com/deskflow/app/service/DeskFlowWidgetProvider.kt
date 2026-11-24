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
import com.deskflow.app.data.model.LedControlRequest
import com.deskflow.app.data.model.TimerStartRequest
import com.deskflow.app.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * (#81) DeskFlow Home Screen Widget: displays Weather + Today's Timetable
 * and provides 1-tap actions for LED 24 Toggle & 25m Focus Timer.
 */
class DeskFlowWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_TOGGLE_LED = "com.deskflow.app.widget.ACTION_TOGGLE_LED"
        const val ACTION_START_FOCUS_25 = "com.deskflow.app.widget.ACTION_START_FOCUS_25"
        const val ACTION_REFRESH_WIDGET = "com.deskflow.app.widget.ACTION_REFRESH_WIDGET"

        private fun buildPendingAction(context: Context, action: String, reqCode: Int): PendingIntent {
            val intent = Intent(context, DeskFlowWidgetProvider::class.java).apply {
                this.action = action
            }
            return PendingIntent.getBroadcast(
                context,
                reqCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun updateWidgetViews(
            context: Context,
            manager: AppWidgetManager,
            appWidgetIds: IntArray,
            weatherText: String? = null,
            subtitleText: String? = null
        ) {
            val openAppIntent = Intent(context, MainActivity::class.java)
            val openAppPending = PendingIntent.getActivity(
                context,
                100,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            appWidgetIds.forEach { widgetId ->
                val views = RemoteViews(context.packageName, R.layout.widget_deskflow)
                if (!weatherText.isNullOrBlank()) {
                    views.setTextViewText(R.id.widget_weather, weatherText)
                }
                if (!subtitleText.isNullOrBlank()) {
                    views.setTextViewText(R.id.widget_subtitle, subtitleText)
                }
                views.setOnClickPendingIntent(R.id.widget_root, openAppPending)
                views.setOnClickPendingIntent(R.id.btn_widget_led, buildPendingAction(context, ACTION_TOGGLE_LED, 101))
                views.setOnClickPendingIntent(R.id.btn_widget_focus, buildPendingAction(context, ACTION_START_FOCUS_25, 102))
                views.setOnClickPendingIntent(R.id.btn_widget_refresh, buildPendingAction(context, ACTION_REFRESH_WIDGET, 103))
                manager.updateAppWidget(widgetId, views)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgetViews(context, appWidgetManager, appWidgetIds)
        refreshFromApiAsync(context, appWidgetManager, appWidgetIds, null)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (action !in setOf(ACTION_TOGGLE_LED, ACTION_START_FOCUS_25, ACTION_REFRESH_WIDGET)) return

        val pendingResult = goAsync()
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val widgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, DeskFlowWidgetProvider::class.java)
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = SecurePreferences(context)
                if (prefs.deviceToken.isNullOrBlank()) {
                    updateWidgetViews(context, appWidgetManager, widgetIds, "--", "Mở DeskFlow để ghép nối thiết bị")
                    return@launch
                }
                val api = ApiClientFactory.create(prefs)
                var feedback: String? = null
                when (action) {
                    ACTION_TOGGLE_LED -> {
                        val res = api.controlLed(LedControlRequest("toggle"))
                        feedback = if (res.isSuccessful) "Đã bật/tắt đèn LED 24 bóng" else "Lỗi kết nối LED"
                    }
                    ACTION_START_FOCUS_25 -> {
                        val res = api.startTimer(TimerStartRequest(duration = "25m", note = "Widget Focus"))
                        feedback = if (res.isSuccessful) "Đang chạy Focus 25 phút" else "Lỗi bật Focus Timer"
                    }
                }
                syncStatusInternal(context, api, appWidgetManager, widgetIds, feedback)
            } catch (e: Exception) {
                updateWidgetViews(context, appWidgetManager, widgetIds, "--", "Offline: ${e.message ?: "Network error"}")
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun refreshFromApiAsync(
        context: Context,
        manager: AppWidgetManager,
        widgetIds: IntArray,
        overrideSubtitle: String?
    ) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = SecurePreferences(context)
                if (prefs.deviceToken.isNullOrBlank()) return@launch
                val api = ApiClientFactory.create(prefs)
                syncStatusInternal(context, api, manager, widgetIds, overrideSubtitle)
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun syncStatusInternal(
        context: Context,
        api: com.deskflow.app.data.api.FocusApiService,
        manager: AppWidgetManager,
        widgetIds: IntArray,
        overrideSubtitle: String?
    ) {
        val statusRes = runCatching { api.getSystemStatus() }.getOrNull()
        val tkbRes = runCatching { api.getTodayTimetable() }.getOrNull()

        val weather = statusRes?.body()?.data?.station?.weather ?: "--°C"
        val today = tkbRes?.body()?.data?.today
        val subjects = ((today?.morning ?: emptyList()) + (today?.afternoon ?: emptyList()))
            .filter { it.isNotBlank() }

        val subtitle = overrideSubtitle ?: if (subjects.isNotEmpty()) {
            "TKB: ${subjects.take(4).joinToString(" • ")}"
        } else {
            "Hôm nay trống lịch • Trạm sẵn sàng"
        }
        updateWidgetViews(context, manager, widgetIds, weather, subtitle)
    }
}
