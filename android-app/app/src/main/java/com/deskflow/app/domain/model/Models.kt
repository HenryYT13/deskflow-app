package com.deskflow.app.domain.model

data class FocusState(
    val enabled: Boolean = false,
    val focusActive: Boolean = false,
    val version: Int = 0,
    val language: String = "vi",
    val serverTime: String? = null,
    val activeChallenge: ChallengeInfo? = null,
    val lastSyncedTime: Long = 0L,
    val isOnline: Boolean = true,
    val remainingSeconds: Long? = null,
    val endsAt: String? = null,
    val durationMinutes: Int = 25,
    val reason: String = ""
) {
    val isActive: Boolean
        get() = enabled && focusActive
}

data class ChallengeInfo(
    val challengeId: String,
    val question: String,
    val language: String,
    val expiresAt: String,
    val expiresInSeconds: Long = 120L
)

data class DeviceInfo(
    val deviceId: String,
    val deviceName: String,
    val platform: String,
    val pairedAt: String,
    val lastSeenAt: String? = null,
    val isCurrent: Boolean = false
)

enum class ActivityType {
    FOCUS_STARTED,
    FOCUS_STOPPED,
    DEVICE_PAIRED,
    WEBSITE_BLOCKED,
    CHALLENGE_FAILED
}

data class ActivityItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: ActivityType,
    val timestamp: Long = System.currentTimeMillis(),
    val description: String,
    val details: String? = null
)
