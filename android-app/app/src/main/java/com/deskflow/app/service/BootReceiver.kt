package com.deskflow.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.deskflow.app.data.local.SecurePreferences

/**
 * Restores the persistent Focus Mode notification after device reboot if the session
 * was active prior to reboot.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val securePreferences = SecurePreferences(context.applicationContext)
            if (securePreferences.isPaired && securePreferences.lastKnownFocusActive) {
                FocusNotificationService.startService(context.applicationContext)
            }
        }
    }
}
