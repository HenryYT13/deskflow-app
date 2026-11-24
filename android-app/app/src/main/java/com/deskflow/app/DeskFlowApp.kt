package com.deskflow.app

import android.app.Application
import com.deskflow.app.data.api.ApiClientFactory
import com.deskflow.app.data.local.ActivityStore
import com.deskflow.app.data.local.SecurePreferences
import com.deskflow.app.data.repository.FocusRepository
import com.deskflow.app.service.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DeskFlowApp : Application() {

    lateinit var securePreferences: SecurePreferences
        private set

    lateinit var activityStore: ActivityStore
        private set

    lateinit var focusRepository: FocusRepository
        private set

    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        instance = this

        securePreferences = SecurePreferences(applicationContext)
        // Prime the inline-string bridge before the first composition so even the
        // first frame uses the saved language.
        com.deskflow.app.ui.i18n.AppStrings.language = securePreferences.language
        activityStore = ActivityStore(applicationContext)

        val apiService = ApiClientFactory.create(securePreferences)
        focusRepository = FocusRepository(
            apiService = apiService,
            securePreferences = securePreferences,
            activityStore = activityStore
        )

        NotificationHelper.ensureChannel(applicationContext)
        startPhoneAlertPolling()
    }

    private fun startPhoneAlertPolling() {
        appScope.launch {
            var lastSeenTs = System.currentTimeMillis() / 1000.0 - 60.0
            val seenIds = HashSet<String>()
            while (isActive) {
                if (securePreferences.isPaired && securePreferences.hasScope("control")) {
                    try {
                        val items = focusRepository.fetchNotifications(lastSeenTs)
                        for (item in items) {
                            if (item.id.isNotBlank() && seenIds.add(item.id)) {
                                if (item.timestamp > lastSeenTs) lastSeenTs = item.timestamp
                                NotificationHelper.showAlert(
                                    context = applicationContext,
                                    title = item.title.ifBlank { "DeskFlow Alert" },
                                    body = item.body,
                                    notifId = (item.id.hashCode() and 0x7FFFFFFF).coerceAtLeast(3000),
                                    urgent = item.priority == "urgent" || item.priority == "high"
                                )
                            }
                        }
                        if (seenIds.size > 150) seenIds.clear()
                    } catch (_: Exception) {
                        // Silent background retry
                    }
                }
                delay(12_000L)
            }
        }
    }

    companion object {
        lateinit var instance: DeskFlowApp
            private set
    }
}
