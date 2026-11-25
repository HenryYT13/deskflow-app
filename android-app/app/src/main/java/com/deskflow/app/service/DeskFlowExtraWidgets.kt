package com.deskflow.app.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.deskflow.app.ui.MainActivity
import com.deskflow.app.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun bindCardWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetIds: IntArray,
    badge: String,
    title: String,
    subtitle: String
) {
    val intent = Intent(context, MainActivity::class.java)
    val pi = PendingIntent.getActivity(
        context,
        badge.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    for (id in appWidgetIds) {
        val views = RemoteViews(context.packageName, R.layout.widget_deskflow_card)
        views.setTextViewText(R.id.widget_card_badge, badge)
        views.setTextViewText(R.id.widget_card_title, title)
        views.setTextViewText(R.id.widget_card_subtitle, subtitle)
        views.setOnClickPendingIntent(R.id.widget_card_root, pi)
        appWidgetManager.updateAppWidget(id, views)
    }
}

/** #301 Widget 3: Clock + Weather AQI + Temp */
class DeskFlowWeatherWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        val now = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        bindCardWidget(context, mgr, ids, "ĐỒNG HỒ & AQI (#301)", "$now • Hạ Long", "Chạm để xem thời tiết & AQI chi tiết")
    }
}

/** #302 Widget 4: To-do List */
class DeskFlowTodoWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        bindCardWidget(context, mgr, ids, "TO-DO LIST (#302)", "Ghi chú & Việc cần làm", "Chạm để mở nhanh & tick hoàn thành")
    }
}

/** #303 Widget 5: ESP32 Quick Control */
class DeskFlowStationWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        bindCardWidget(context, mgr, ids, "ESP32 QUICK (#303)", "Đèn 24 LED & Màn hình", "Chạm để bật/tắt LED hoặc đổi chế độ")
    }
}

/** #304 Widget 6: Spotify Mini Player + Vinyl (#296) */
class DeskFlowSpotifyWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        bindCardWidget(context, mgr, ids, "SPOTIFY VINYL (#304)", "Đang phát trên DeskFlow", "Chạm để điều khiển nhạc & xem lời bài hát")
    }
}

/** #305 Widget 7: Exam / Event Countdown */
class DeskFlowCountdownWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        bindCardWidget(context, mgr, ids, "ĐẾM NGƯỢC (#305)", "Kỳ thi & Sự kiện", "Chạm để xem ngày thi & sự kiện sắp tới")
    }
}
