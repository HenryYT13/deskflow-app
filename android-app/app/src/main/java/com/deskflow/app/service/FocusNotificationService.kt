package com.deskflow.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.deskflow.app.DeskFlowApp
import com.deskflow.app.R
import com.deskflow.app.data.repository.ApiResult
import com.deskflow.app.ui.MainActivity
import kotlinx.coroutines.*
import java.util.Locale

/**
 * Standard foreground service maintaining the persistent Focus Mode notification
 * and periodic background status synchronization with the DeskFlow backend.
 *
 * Does NOT act as a VPN, DNS filter, firewall, or network proxy.
 * Network traffic and all ordinary phone functionality remain completely untouched.
 */
class FocusNotificationService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var syncJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopFocusService()
            return START_NOT_STICKY
        }

        val notification = buildPersistentNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startBackgroundStatusSync()
        showOverlay()

        return START_STICKY
    }

    private var overlayView: android.view.View? = null

    private fun showOverlay() {
        if (overlayView != null) return
        if (!android.provider.Settings.canDrawOverlays(this)) return
        val wm = getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        val tv = android.widget.TextView(this).apply {
            text = getString(R.string.overlay_banner)
            setTextColor(android.graphics.Color.WHITE)
            textSize = 12f
            setPadding(32, 12, 32, 12)
            setBackgroundColor(0xCC000000.toInt())
        }
        val params = android.view.WindowManager.LayoutParams(
            android.view.WindowManager.LayoutParams.WRAP_CONTENT,
            android.view.WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") android.view.WindowManager.LayoutParams.TYPE_PHONE,
            android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply { gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL }
        try {
            wm.addView(tv, params)
            overlayView = tv
        } catch (_: Exception) {}
    }

    private fun hideOverlay() {
        overlayView?.let {
            try { (getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).removeView(it) } catch (_: Exception) {}
        }
        overlayView = null
    }

    override fun onDestroy() {
        hideOverlay()
        super.onDestroy()
        syncJob?.cancel()
        serviceScope.cancel()
    }

    private fun stopFocusService() {
        hideOverlay()
        syncJob?.cancel()
        syncJob = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun startBackgroundStatusSync() {
        if (syncJob?.isActive == true) return

        syncJob = serviceScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                try {
                    val app = applicationContext as? DeskFlowApp
                    val repository = app?.focusRepository
                    if (repository != null) {
                        val result = repository.refreshStatus()
                        if (result is ApiResult.Success && !result.data.isActive) {
                            // Backend reported focus mode is no longer active
                            // (e.g. deactivated via Discord bot, web browser extension, or timer expired)
                            withContext(Dispatchers.Main) {
                                stopFocusService()
                            }
                            break
                        }
                    }
                } catch (_: Exception) {
                    // Network disconnected or temporary server hiccup.
                    // Keep in-app lock intact and retry on next interval.
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.persistent_notification_title)
            val descriptionText = getString(R.string.persistent_notification_content)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun buildPersistentNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Localize based on user's selected language
        val app = applicationContext as? DeskFlowApp
        val lang = app?.securePreferences?.language ?: "vi"
        val locale = Locale(lang)
        val config = Configuration(resources.configuration).apply {
            setLocale(locale)
        }
        val localizedContext = createConfigurationContext(config)

        val title = localizedContext.getString(R.string.persistent_notification_title)
        val content = localizedContext.getString(R.string.persistent_notification_content)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "deskflow_focus_channel"
        private const val NOTIFICATION_ID = 2001
        private const val POLL_INTERVAL_MS = 30_000L

        const val ACTION_START = "com.deskflow.app.START_FOCUS_SERVICE"
        const val ACTION_STOP = "com.deskflow.app.STOP_FOCUS_SERVICE"

        fun startService(context: Context) {
            val intent = Intent(context, FocusNotificationService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, FocusNotificationService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
