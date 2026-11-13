package com.deskflow.app

import android.app.Application
import com.deskflow.app.data.api.ApiClientFactory
import com.deskflow.app.data.local.ActivityStore
import com.deskflow.app.data.local.SecurePreferences
import com.deskflow.app.data.repository.FocusRepository

class DeskFlowApp : Application() {

    lateinit var securePreferences: SecurePreferences
        private set

    lateinit var activityStore: ActivityStore
        private set

    lateinit var focusRepository: FocusRepository
        private set

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
    }

    companion object {
        lateinit var instance: DeskFlowApp
            private set
    }
}
