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
        startWatchdog()
        startTimerTicker()

        return START_STICKY
    }

    private var overlayView: android.view.View? = null
    private var overlayTimerView: android.widget.TextView? = null
    private var watchdogJob: Job? = null
    private var timerTickerJob: Job? = null

    private fun showOverlay() {
        if (overlayView != null) return
        if (!android.provider.Settings.canDrawOverlays(this)) return
        val wm = getSystemService(Context.WINDOW_SERVICE) as? android.view.WindowManager ?: return

        val density = resources.displayMetrics.density

        // Root FrameLayout
        val rootLayout = android.widget.FrameLayout(this).apply {
            setBackgroundColor(0xF50B0F19.toInt()) // Deep midnight slate, 96% opacity
            isClickable = true
            isFocusable = true
        }

        // Center Content Container
        val contentBox = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding((28 * density).toInt(), (32 * density).toInt(), (28 * density).toInt(), (32 * density).toInt())
        }

        // 🔒 Lock Icon
        val lockIcon = android.widget.TextView(this).apply {
            text = "🔒"
            textSize = 54f
            gravity = android.view.Gravity.CENTER
        }
        contentBox.addView(lockIcon)

        // Vietnamese Title
        val titleVi = android.widget.TextView(this).apply {
            text = "CHẾ ĐỘ TẬP TRUNG ĐANG BẬT"
            setTextColor(0xFFF8FAFC.toInt())
            textSize = 21f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            val lp = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (16 * density).toInt()
            }
            layoutParams = lp
        }
        contentBox.addView(titleVi)

        // English Subtitle
        val subEn = android.widget.TextView(this).apply {
            text = "Focus Mode is Active"
            setTextColor(0xFF94A3B8.toInt())
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            val lp = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (4 * density).toInt()
            }
            layoutParams = lp
        }
        contentBox.addView(subEn)

        // Advisory Message
        val adviceTv = android.widget.TextView(this).apply {
            text = "Thiết bị đang bị khóa để bạn tập trung học tập / làm việc. Vui lòng không xao nhãng!"
            setTextColor(0xFFCBD5E1.toInt())
            textSize = 15f
            gravity = android.view.Gravity.CENTER
            setLineSpacing(4 * density, 1f)
            val lp = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (14 * density).toInt()
            }
            layoutParams = lp
        }
        contentBox.addView(adviceTv)

        // Countdown Card Container
        val countdownCard = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            val shape = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 16 * density
                setColor(0xFF1E293B.toInt())
                setStroke((1.5f * density).toInt(), 0xFF334155.toInt())
            }
            background = shape
            setPadding((20 * density).toInt(), (14 * density).toInt(), (20 * density).toInt(), (14 * density).toInt())
            val lp = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (22 * density).toInt()
            }
            layoutParams = lp
        }

        val timerLabel = android.widget.TextView(this).apply {
            text = "THỜI GIAN CÒN LẠI / REMAINING"
            setTextColor(0xFF94A3B8.toInt())
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        countdownCard.addView(timerLabel)

        val timerTv = android.widget.TextView(this).apply {
            text = "25:00"
            setTextColor(0xFF38BDF8.toInt())
            textSize = 38f
            typeface = android.graphics.Typeface.MONOSPACE
            gravity = android.view.Gravity.CENTER
            val lp = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (4 * density).toInt()
            }
            layoutParams = lp
        }
        countdownCard.addView(timerTv)
        overlayTimerView = timerTv
        contentBox.addView(countdownCard)

        // Action Buttons Container
        val btnContainer = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            val lp = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (24 * density).toInt()
            }
            layoutParams = lp
        }

        // Button 1: Return to DeskFlow
        val returnBtn = android.widget.Button(this).apply {
            text = "Quay lại DeskFlow"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 15f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            val bg = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 14 * density
                setColor(0xFF0284C7.toInt())
            }
            background = bg
            setOnClickListener {
                val launchIntent = Intent(this@FocusNotificationService, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                }
                startActivity(launchIntent)
            }
            val lp = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                (48 * density).toInt()
            )
            layoutParams = lp
        }
        btnContainer.addView(returnBtn)

        // Button 2: Unlock with Math Challenge
        val challengeBtn = android.widget.Button(this).apply {
            text = "Giải toán để mở khóa"
            setTextColor(0xFFE2E8F0.toInt())
            textSize = 15f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            val bg = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 14 * density
                setColor(0xFF334155.toInt())
                setStroke((1 * density).toInt(), 0xFF475569.toInt())
            }
            background = bg
            setOnClickListener {
                val launchIntent = Intent(this@FocusNotificationService, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    putExtra(EXTRA_OPEN_CHALLENGE, true)
                }
                startActivity(launchIntent)
            }
            val lp = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                (48 * density).toInt()
            ).apply {
                topMargin = (12 * density).toInt()
            }
            layoutParams = lp
        }
        btnContainer.addView(challengeBtn)

        contentBox.addView(btnContainer)

        val frameParams = android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.CENTER
        }
        rootLayout.addView(contentBox, frameParams)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            android.view.WindowManager.LayoutParams.TYPE_PHONE
        }

        // Fullscreen touch-intercepting overlay
        val params = android.view.WindowManager.LayoutParams(
            android.view.WindowManager.LayoutParams.MATCH_PARENT,
            android.view.WindowManager.LayoutParams.MATCH_PARENT,
            layoutFlag,
            android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = android.view.Gravity.CENTER
        }

        try {
            wm.addView(rootLayout, params)
            overlayView = rootLayout
        } catch (_: Exception) {}
    }

    private fun hideOverlay() {
        overlayView?.let {
            try { (getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).removeView(it) } catch (_: Exception) {}
        }
        overlayView = null
        overlayTimerView = null
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = serviceScope.launch {
            while (isActive) {
                delay(2500L)
                val app = applicationContext as? DeskFlowApp
                val repository = app?.focusRepository
                val focusActive = repository?.focusState?.value?.isActive == true
                if (focusActive) {
                    withContext(Dispatchers.Main) {
                        try {
                            val intent = Intent(this@FocusNotificationService, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                            }
                            startActivity(intent)
                        } catch (_: Exception) {}
                    }
                } else {
                    break
                }
            }
        }
    }

    private fun startTimerTicker() {
        timerTickerJob?.cancel()
        timerTickerJob = serviceScope.launch {
            while (isActive) {
                val app = applicationContext as? DeskFlowApp
                val repository = app?.focusRepository
                val state = repository?.focusState?.value
                val remSec = state?.remainingSeconds
                val display = if (remSec != null && remSec > 0) {
                    val m = remSec / 60
                    val s = remSec % 60
                    String.format(Locale.getDefault(), "%02d:%02d", m, s)
                } else {
                    "TẬP TRUNG"
                }
                withContext(Dispatchers.Main) {
                    overlayTimerView?.text = display
                }
                delay(1000L)
            }
        }
    }

    override fun onDestroy() {
        watchdogJob?.cancel()
        timerTickerJob?.cancel()
        hideOverlay()
        super.onDestroy()
        syncJob?.cancel()
        serviceScope.cancel()
    }

    private fun stopFocusService() {
        watchdogJob?.cancel()
        timerTickerJob?.cancel()
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

        const val EXTRA_OPEN_CHALLENGE = "com.deskflow.app.OPEN_CHALLENGE"
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
