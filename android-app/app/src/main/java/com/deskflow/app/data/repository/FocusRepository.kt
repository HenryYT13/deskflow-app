package com.deskflow.app.data.repository

import com.deskflow.app.data.api.FocusApiService
import com.deskflow.app.data.local.ActivityStore
import com.deskflow.app.data.local.SecurePreferences
import com.deskflow.app.data.model.*
import com.deskflow.app.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import retrofit2.Response

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val code: String, val message: String, val retryAfter: Int? = null, val statusCode: Int = 400) : ApiResult<Nothing>()
}

data class ApiHealthTestResult(
    val isSuccess: Boolean,
    val statusCode: Int? = null,
    val latencyMs: Long = 0L,
    val timestamp: Long = System.currentTimeMillis(),
    val service: String? = null,
    val version: String? = null,
    val errorMessage: String? = null,
    val errorType: String? = null
)

class FocusRepository(
    private var apiService: FocusApiService,
    private val securePreferences: SecurePreferences,
    private val activityStore: ActivityStore
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val _focusState = MutableStateFlow(
        FocusState(
            enabled = securePreferences.lastKnownFocusActive,
            focusActive = securePreferences.lastKnownFocusActive
        )
    )
    val focusState: StateFlow<FocusState> = _focusState.asStateFlow()

    private val _isPaired = MutableStateFlow(securePreferences.isPaired)
    val isPaired: StateFlow<Boolean> = _isPaired.asStateFlow()

    private val _scopes = MutableStateFlow(securePreferences.scopes)
    val scopes: StateFlow<List<String>> = _scopes.asStateFlow()

    private val _alarmState = MutableStateFlow(AlarmActiveDto())
    val alarmState: StateFlow<AlarmActiveDto> = _alarmState.asStateFlow()

    private val _spotifyState = MutableStateFlow(SpotifyStatusDto())
    val spotifyState: StateFlow<SpotifyStatusDto> = _spotifyState.asStateFlow()

    private val _timetableState = MutableStateFlow<TimetableDataWrapper?>(null)
    val timetableState: StateFlow<TimetableDataWrapper?> = _timetableState.asStateFlow()

    private val _focusHistory = MutableStateFlow<List<FocusSessionDto>>(emptyList())
    val focusHistory: StateFlow<List<FocusSessionDto>> = _focusHistory.asStateFlow()

    private val _systemStatus = MutableStateFlow<SystemStatusWrapper?>(null)
    val systemStatus: StateFlow<SystemStatusWrapper?> = _systemStatus.asStateFlow()

    private val _settings = MutableStateFlow<StationSettingsDto?>(null)
    val settings: StateFlow<StationSettingsDto?> = _settings.asStateFlow()

    private fun updateFocusState(newState: FocusState) {
        _focusState.value = newState
        securePreferences.lastKnownFocusActive = newState.isActive
    }

    fun updateApiService(newService: FocusApiService) {
        this.apiService = newService
    }

    suspend fun refreshStatus(): ApiResult<FocusState> {
        if (!securePreferences.isPaired) {
            val emptyState = FocusState(enabled = false, focusActive = false, isOnline = true)
            updateFocusState(emptyState)
            return ApiResult.Success(emptyState)
        }

        return try {
            val response = apiService.getStatus()
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val chInfo = body.challenge?.let { ch ->
                    if (ch.active == true) {
                        ChallengeInfo(
                            challengeId = ch.challengeId,
                            question = ch.question ?: "",
                            language = ch.language ?: "vi",
                            expiresAt = ch.expiresAt ?: "",
                            expiresInSeconds = ch.expiresIn ?: 120L
                        )
                    } else null
                }

                val newState = FocusState(
                    enabled = body.enabled,
                    focusActive = body.focusActive,
                    version = body.version,
                    language = body.language ?: "vi",
                    serverTime = body.serverTime,
                    activeChallenge = chInfo,
                    lastSyncedTime = System.currentTimeMillis(),
                    isOnline = true,
                    remainingSeconds = body.remainingSeconds ?: _focusState.value.remainingSeconds,
                    endsAt = body.endsAt ?: _focusState.value.endsAt,
                    durationMinutes = _focusState.value.durationMinutes,
                    reason = _focusState.value.reason
                )
                updateFocusState(newState)
                ApiResult.Success(newState)
            } else {
                val err = parseError(response)
                if (response.code() == 401) {
                    securePreferences.clearAuth()
                    _isPaired.value = false
                    updateFocusState(FocusState())
                } else {
                    updateFocusState(_focusState.value.copy(isOnline = false))
                }
                err
            }
        } catch (e: Exception) {
            updateFocusState(_focusState.value.copy(isOnline = false))
            ApiResult.Error(code = "network_error", message = e.message ?: "Network unreachable", statusCode = 0)
        }
    }

    suspend fun pairDevice(code: String, customDeviceName: String? = null): ApiResult<PairResponse> {
        val name = customDeviceName?.takeIf { it.isNotBlank() } ?: securePreferences.deviceName
        val req = PairRequest(
            pairingCode = code.trim().uppercase(),
            deviceName = name,
            platform = "android"
        )

        return try {
            val response = apiService.pair(req)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (!body.deviceToken.isNullOrBlank() && !body.deviceId.isNullOrBlank()) {
                    securePreferences.deviceToken = body.deviceToken
                    securePreferences.deviceId = body.deviceId
                    securePreferences.deviceName = body.deviceName ?: name
                    securePreferences.pairedAt = body.pairedAt
                    securePreferences.scopes = body.scopes
                    _scopes.value = body.scopes
                    _isPaired.value = true

                    activityStore.logEvent(
                        type = ActivityType.DEVICE_PAIRED,
                        description = "Ghép đôi thành công thiết bị ${body.deviceName ?: name}"
                    )
                    refreshStatus()
                    ApiResult.Success(body)
                } else {
                    ApiResult.Error(code = "invalid_payload", message = "Missing token in response")
                }
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network connection failed")
        }
    }

    suspend fun enableFocus(durationMinutes: Int = 25, reason: String = ""): ApiResult<FocusState> {
        val req = EnableRequest(
            durationMinutes = durationMinutes,
            reason = reason.ifBlank { "Study Session" },
            language = securePreferences.language
        )

        return try {
            val response = apiService.enable(req)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val newState = _focusState.value.copy(
                    enabled = body.enabled,
                    focusActive = body.focusActive,
                    version = body.version,
                    serverTime = body.serverTime,
                    lastSyncedTime = System.currentTimeMillis(),
                    isOnline = true,
                    remainingSeconds = (durationMinutes * 60).toLong(),
                    durationMinutes = durationMinutes,
                    reason = reason
                )
                updateFocusState(newState)
                activityStore.logEvent(
                    type = ActivityType.FOCUS_STARTED,
                    description = "Bắt đầu Focus Mode ($durationMinutes phút)",
                    details = reason
                )
                ApiResult.Success(newState)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network error")
        }
    }

    suspend fun requestChallenge(forceNew: Boolean = false): ApiResult<ChallengeInfo> {
        val req = ChallengeRequest(
            language = securePreferences.language,
            forceNew = forceNew
        )

        return try {
            val response = apiService.requestChallenge(req)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                val info = ChallengeInfo(
                    challengeId = body.challengeId,
                    question = body.question,
                    language = body.language,
                    expiresAt = body.expiresAt,
                    expiresInSeconds = body.expiresIn
                )
                updateFocusState(_focusState.value.copy(activeChallenge = info))
                ApiResult.Success(info)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network error")
        }
    }

    suspend fun verifyChallenge(challengeId: String, answer: Int): ApiResult<VerifyResponse> {
        val req = VerifyRequest(challengeId = challengeId, answer = answer)
        return try {
            val response = apiService.verifyChallenge(req)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.correct) {
                    updateFocusState(
                        _focusState.value.copy(
                            enabled = false,
                            focusActive = false,
                            version = body.version,
                            activeChallenge = null,
                            lastSyncedTime = System.currentTimeMillis()
                        )
                    )
                    activityStore.logEvent(
                        type = ActivityType.FOCUS_STOPPED,
                        description = "Đã giải đúng thử thách toán học và tắt Focus Mode"
                    )
                } else {
                    activityStore.logEvent(
                        type = ActivityType.CHALLENGE_FAILED,
                        description = "Nhập sai đáp án thử thách toán học"
                    )
                    if (body.newChallenge != null) {
                        val nc = body.newChallenge
                        updateFocusState(
                            _focusState.value.copy(
                                activeChallenge = ChallengeInfo(
                                    challengeId = nc.challengeId,
                                    question = nc.question,
                                    language = nc.language,
                                    expiresAt = nc.expiresAt,
                                    expiresInSeconds = nc.expiresIn
                                )
                            )
                        )
                    }
                }
                ApiResult.Success(body)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network error")
        }
    }

    suspend fun classifyUrl(url: String, title: String? = null): ApiResult<ClassifyUrlResponse> {
        val req = ClassifyUrlRequest(
            url = url,
            title = title,
            language = securePreferences.language
        )
        return try {
            val response = apiService.classifyUrl(req)
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            // Strict Fail-Closed fallback
            ApiResult.Error(code = "network_fail_closed", message = e.message ?: "Fail closed")
        }
    }

    suspend fun getDevices(): ApiResult<List<DeviceInfo>> {
        return try {
            val response = apiService.getDevices()
            if (response.isSuccessful && response.body() != null) {
                val list = response.body()!!.devices.map { d ->
                    DeviceInfo(
                        deviceId = d.deviceId,
                        deviceName = d.deviceName,
                        platform = d.platform,
                        pairedAt = d.pairedAt,
                        lastSeenAt = d.lastSeenAt,
                        isCurrent = d.isCurrent
                    )
                }
                ApiResult.Success(list)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network error")
        }
    }

    suspend fun renameDevice(newName: String): ApiResult<Boolean> {
        val id = securePreferences.deviceId
        val name = newName.trim()
        if (id.isNullOrBlank() || name.isEmpty()) return ApiResult.Error("invalid_input", "Missing device id or name")
        return try {
            val response = apiService.renameDevice(id, mapOf("name" to name))
            if (response.isSuccessful) {
                securePreferences.deviceName = name
                ApiResult.Success(true)
            } else {
                ApiResult.Error("http_${response.code()}", response.message())
            }
        } catch (e: Exception) {
            ApiResult.Error("network_error", e.message ?: "")
        }
    }

    suspend fun unpairDevice(): ApiResult<Boolean> {
        return try {
            val response = apiService.unpair()
            securePreferences.clearAuth()
            _isPaired.value = false
            updateFocusState(FocusState())
            activityStore.logEvent(type = ActivityType.DEVICE_PAIRED, description = "Thiết bị đã hủy ghép đôi")
            ApiResult.Success(true)
        } catch (e: Exception) {
            securePreferences.clearAuth()
            _isPaired.value = false
            updateFocusState(FocusState())
            ApiResult.Success(true)
        }
    }

    suspend fun unpairOtherDevice(deviceId: String): ApiResult<Boolean> {
        return try {
            val response = apiService.unpair(mapOf("device_id" to deviceId))
            if (response.isSuccessful && response.body()?.success == true) {
                ApiResult.Success(true)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network error")
        }
    }

    suspend fun approvePairSession(
        serverUrl: String? = null,
        sessionId: String,
        deviceName: String,
        platform: String = "desktop"
    ): ApiResult<PairSessionApproveResponse> {
        val req = PairSessionApproveRequest(
            deviceName = deviceName.ifBlank { "DeskFlow Client" },
            scopes = listOf("focus", "control", "spotify"),
            platform = platform
        )
        return try {
            val response = if (!serverUrl.isNullOrBlank()) {
                val fullUrl = serverUrl.trimEnd('/') + "/api/focus/pair/session/$sessionId/approve"
                apiService.approvePairSessionUrl(fullUrl, req)
            } else {
                apiService.approvePairSession(sessionId, req)
            }
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network error")
        }
    }

    suspend fun rejectPairSession(
        serverUrl: String? = null,
        sessionId: String
    ): ApiResult<ActionSuccessResponse> {
        return try {
            val response = if (!serverUrl.isNullOrBlank()) {
                val fullUrl = serverUrl.trimEnd('/') + "/api/focus/pair/session/$sessionId/reject"
                apiService.rejectPairSessionUrl(fullUrl)
            } else {
                apiService.rejectPairSession(sessionId)
            }
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network error")
        }
    }

    // --- Alarm & Control Operations ---

    suspend fun refreshAlarm(): ApiResult<AlarmActiveDto> {
        if (!securePreferences.isPaired || !securePreferences.hasScope("control")) {
            return ApiResult.Success(AlarmActiveDto())
        }
        return try {
            val response = apiService.getActiveAlarm()
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                _alarmState.value = body
                ApiResult.Success(body)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Failed to refresh alarm")
        }
    }

    suspend fun solveAlarm(answer: Int): ApiResult<ActionSuccessResponse> {
        val sid = _alarmState.value.sessionId ?: return ApiResult.Error("no_active_alarm", "Không có báo thức đang kêu")
        return try {
            val response = apiService.solveAlarm(AlarmSolveRequest(sessionId = sid, answer = answer))
            if (response.isSuccessful && response.body() != null) {
                _alarmState.value = AlarmActiveDto(active = false)
                ApiResult.Success(response.body()!!)
            } else {
                val err = parseError(response)
                refreshAlarm()
                err
            }
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network error")
        }
    }

    suspend fun cancelAlarm(): ApiResult<ActionSuccessResponse> {
        val sid = _alarmState.value.sessionId ?: return ApiResult.Error("no_active_alarm", "Không có báo thức đang kêu")
        return try {
            val response = apiService.cancelAlarm(AlarmCancelRequest(sessionId = sid))
            if (response.isSuccessful && response.body() != null) {
                _alarmState.value = AlarmActiveDto(active = false)
                ApiResult.Success(response.body()!!)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network error")
        }
    }

    suspend fun emergencyStop(): ApiResult<ActionSuccessResponse> {
        return try {
            val response = apiService.emergencyStop()
            if (response.isSuccessful && response.body() != null) {
                _alarmState.value = AlarmActiveDto(active = false)
                ApiResult.Success(response.body()!!)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network error")
        }
    }

    suspend fun refreshTimetable(): ApiResult<TimetableDataWrapper?> {
        if (!securePreferences.isPaired || !securePreferences.hasScope("control")) {
            return ApiResult.Success(null)
        }
        return try {
            val response = apiService.getTodayTimetable()
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                _timetableState.value = body.data
                ApiResult.Success(body.data)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network error")
        }
    }

    // --- Spotify Operations ---

    suspend fun refreshSpotify(): ApiResult<SpotifyStatusDto> {
        if (!securePreferences.isPaired || !securePreferences.hasScope("spotify")) {
            return ApiResult.Success(SpotifyStatusDto())
        }
        return try {
            val response = apiService.getSpotifyStatus()
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                _spotifyState.value = body
                ApiResult.Success(body)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network error")
        }
    }

    suspend fun controlSpotify(command: String): ApiResult<ActionSuccessResponse> {
        return try {
            val response = apiService.controlSpotifyPlayback(SpotifyPlaybackRequest(command = command))
            if (response.isSuccessful && response.body() != null) {
                refreshSpotify()
                ApiResult.Success(response.body()!!)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network error")
        }
    }

    suspend fun setSpotifyVolume(volume: Int): ApiResult<ActionSuccessResponse> {
        return try {
            val response = apiService.setSpotifyVolume(SpotifyVolumeRequest(volume = volume))
            if (response.isSuccessful && response.body() != null) {
                _spotifyState.value = _spotifyState.value.copy(progressPct = volume)
                ApiResult.Success(response.body()!!)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network error")
        }
    }

    // --- Focus History ---
    suspend fun refreshFocusHistory(): ApiResult<FocusHistoryResponse> {
        if (!securePreferences.isPaired) {
            return ApiResult.Success(FocusHistoryResponse())
        }
        return try {
            val response = apiService.getFocusHistory()
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                _focusHistory.value = body.history
                ApiResult.Success(body)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network error")
        }
    }

    // --- Station Status & Settings ---
    suspend fun refreshSystemStatus(): ApiResult<SystemStatusResponse> {
        if (!securePreferences.isPaired || !securePreferences.hasScope("control")) {
            return ApiResult.Success(SystemStatusResponse())
        }
        return try {
            val response = apiService.getSystemStatus()
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                _systemStatus.value = body.data
                ApiResult.Success(body)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network error")
        }
    }

    suspend fun refreshSettings(): ApiResult<StationSettingsResponse> {
        if (!securePreferences.isPaired || !securePreferences.hasScope("control")) {
            return ApiResult.Success(StationSettingsResponse())
        }
        return try {
            val response = apiService.getSettings()
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                _settings.value = body.data
                ApiResult.Success(body)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network error")
        }
    }

    suspend fun updateSettings(updates: StationSettingsDto): ApiResult<StationSettingsResponse> {
        return try {
            val response = apiService.updateSettings(updates)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                _settings.value = body.data
                ApiResult.Success(body)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network error")
        }
    }

    // --- Display & Screen ---
    suspend fun switchDisplayView(view: String): ApiResult<ActionSuccessResponse> {
        val targetView = view.trim().lowercase()
        return try {
            val response = apiService.switchDisplayView(ViewSwitchRequest(view = targetView))
            if (response.isSuccessful && response.body() != null) {
                refreshSystemStatus()
                ApiResult.Success(response.body()!!)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network error")
        }
    }

    suspend fun closeTkb(): ApiResult<ActionSuccessResponse> {
        return try {
            val response = apiService.closeTkb()
            if (response.isSuccessful && response.body() != null) {
                refreshSystemStatus()
                ApiResult.Success(response.body()!!)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network error")
        }
    }

    // --- Lighting & LEDs ---
    suspend fun setLampColor(hex: String): ApiResult<ActionSuccessResponse> {
        return try {
            val response = apiService.setLampColor(LampColorRequest(hex = hex))
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network error")
        }
    }

    suspend fun setLampDim(led: Int?, screen: Int?): ApiResult<ActionSuccessResponse> {
        return try {
            val response = apiService.setLampDim(LampDimRequest(led = led, screen = screen))
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network error")
        }
    }

    suspend fun turnLampOff(): ApiResult<ActionSuccessResponse> {
        return try {
            val response = apiService.turnLampOff()
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network error")
        }
    }

    suspend fun controlLed(action: String): ApiResult<ActionSuccessResponse> {
        return try {
            val response = apiService.controlLed(LedControlRequest(action = action))
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network error")
        }
    }

    suspend fun getClockConfig(): ApiResult<ClockConfigDto> {
        return try {
            val response = apiService.getClockConfig()
            if (response.isSuccessful && response.body() != null && response.body()!!.success) {
                val data = response.body()!!.data ?: ClockConfigDto()
                ApiResult.Success(data)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network error")
        }
    }

    suspend fun updateClockConfig(config: ClockConfigDto): ApiResult<ClockConfigDto> {
        return try {
            val response = apiService.updateClockConfig(config)
            if (response.isSuccessful && response.body() != null && response.body()!!.success) {
                val data = response.body()!!.data ?: config
                refreshSettings()
                ApiResult.Success(data)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network error")
        }
    }

    suspend fun setClockStyle(style: String): ApiResult<ActionSuccessResponse> {
        return try {
            val response = apiService.setClockStyle(ClockStyleRequest(style = style))
            if (response.isSuccessful && response.body() != null) {
                refreshSettings()
                ApiResult.Success(response.body()!!)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network error")
        }
    }

    // --- Buzzer & Schedule ---
    suspend fun triggerBuzz(duration: Int = 250): ApiResult<ActionSuccessResponse> {
        return try {
            val response = apiService.triggerBuzz(BuzzRequest(duration = duration))
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network error")
        }
    }

    suspend fun playMorse(pattern: String, unitMs: Int = 80, freq: Int = 2400): ApiResult<ActionSuccessResponse> {
        return try {
            val response = apiService.playMorse(MorsePlayRequest(pattern = pattern, unitMs = unitMs, freq = freq))
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network error")
        }
    }

    suspend fun stopMorse(): ApiResult<ActionSuccessResponse> {
        return try {
            val response = apiService.stopMorse()
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network error")
        }
    }

    suspend fun reportTelemetry(cpu: Float, ramPct: Float, battery: Int, temp: Float = 0f): ApiResult<ActionSuccessResponse> {
        return try {
            val map = mapOf(
                "cpu" to cpu,
                "ram_pct" to ramPct,
                "battery" to battery,
                "temp" to temp,
                "timestamp" to (System.currentTimeMillis() / 1000.0)
            )
            val response = apiService.reportTelemetry(map)
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network error")
        }
    }

    suspend fun scheduleAlarm(time: String): ApiResult<ActionSuccessResponse> {
        return try {
            val response = apiService.scheduleAlarm(AlarmScheduleRequest(time = time))
            if (response.isSuccessful && response.body() != null) {
                refreshAlarm()
                ApiResult.Success(response.body()!!)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network error")
        }
    }

    // --- Tools ---
    suspend fun sendStickyNote(note: String): ApiResult<ActionSuccessResponse> {
        return try {
            val response = apiService.sendStickyNote(StickyNoteRequest(note = note))
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network error")
        }
    }

    suspend fun setExamCountdown(name: String, date: String): ApiResult<ActionSuccessResponse> {
        return try {
            val response = apiService.setExamCountdown(ExamCountdownRequest(name = name, date = date))
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network error")
        }
    }

    // --- Authoritative Cached State & In-Flight Guards ---
    private val localNotes = mutableListOf<NoteItemDto>()
    private var localActiveNoteId: String? = null
    private val inFlightNoteDeletes = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    private val localCountdowns = mutableListOf<CountdownItemDto>()
    private var localActiveCountdownId: String? = null
    private val inFlightCountdownDeletes = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    fun getCachedNotes(): List<NoteItemDto> = localNotes.toList()
    fun getCachedCountdowns(): List<CountdownItemDto> = localCountdowns.toList()

    // --- Notes CRUD & Station Pinning (Authoritative Backend Confirmation Required) ---
    suspend fun getNotes(): ApiResult<NotesListResponse> {
        return try {
            val response = apiService.getNotes()
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                localNotes.clear()
                localNotes.addAll(body.allNotes)
                localActiveNoteId = body.currentActiveNoteId
                ApiResult.Success(
                    body.copy(
                        notes = localNotes.toList(),
                        data = localNotes.toList(),
                        activeStationNoteId = localActiveNoteId
                    )
                )
            } else {
                parseError(response)
            }
        } catch (e: java.net.SocketTimeoutException) {
            ApiResult.Error(code = "timeout", message = e.message ?: "Request timed out")
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network error")
        }
    }

    suspend fun createNote(text: String, color: String = "#FDE047"): ApiResult<NotesListResponse> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return ApiResult.Error(code = "invalid_text", message = "Nội dung ghi chú không được để trống", statusCode = 400)
        }
        return try {
            val response = apiService.createNote(CreateNoteRequest(text = trimmed, color = color))
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                localNotes.clear()
                localNotes.addAll(body.allNotes)
                localActiveNoteId = body.currentActiveNoteId
                ApiResult.Success(
                    body.copy(
                        notes = localNotes.toList(),
                        data = localNotes.toList(),
                        activeStationNoteId = localActiveNoteId
                    )
                )
            } else {
                parseError(response)
            }
        } catch (e: java.net.SocketTimeoutException) {
            ApiResult.Error(code = "timeout", message = e.message ?: "Request timed out")
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network error")
        }
    }

    suspend fun deleteNote(id: String): ApiResult<NotesListResponse> {
        val cleanId = id.trim()
        if (cleanId.isEmpty()) {
            return ApiResult.Error(code = "invalid_id", message = "Note ID cannot be empty", statusCode = 400)
        }
        if (!inFlightNoteDeletes.add(cleanId)) {
            return ApiResult.Error(code = "duplicate_request", message = "Note deletion already in progress", statusCode = 409)
        }

        return try {
            val response = apiService.deleteNote(cleanId)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                // Authoritative sync: update localNotes even when allNotes is empty (last note deleted)
                localNotes.clear()
                localNotes.addAll(body.allNotes)
                localActiveNoteId = body.currentActiveNoteId
                ApiResult.Success(
                    NotesListResponse(
                        success = true,
                        notes = localNotes.toList(),
                        data = localNotes.toList(),
                        activeNoteId = localActiveNoteId,
                        activeStationNoteId = localActiveNoteId
                    )
                )
            } else {
                parseError(response)
            }
        } catch (e: java.net.SocketTimeoutException) {
            ApiResult.Error(code = "timeout", message = e.message ?: "Request timed out")
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network error")
        } finally {
            inFlightNoteDeletes.remove(cleanId)
        }
    }

    suspend fun pinNote(id: String): ApiResult<ActionSuccessResponse> {
        val cleanId = id.trim()
        if (cleanId.isEmpty()) {
            return ApiResult.Error(code = "invalid_id", message = "Note ID cannot be empty", statusCode = 400)
        }
        return try {
            val response = apiService.pinNote(cleanId)
            if (response.isSuccessful && response.body() != null) {
                localActiveNoteId = cleanId
                ApiResult.Success(response.body()!!)
            } else {
                parseError(response)
            }
        } catch (e: java.net.SocketTimeoutException) {
            ApiResult.Error(code = "timeout", message = e.message ?: "Request timed out")
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network error")
        }
    }

    suspend fun unpinNote(): ApiResult<ActionSuccessResponse> {
        return try {
            val response = apiService.unpinNote()
            if (response.isSuccessful && response.body() != null) {
                localActiveNoteId = null
                ApiResult.Success(response.body()!!)
            } else {
                parseError(response)
            }
        } catch (e: java.net.SocketTimeoutException) {
            ApiResult.Error(code = "timeout", message = e.message ?: "Request timed out")
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network error")
        }
    }

    // --- Countdowns CRUD & Station Pinning (Authoritative Backend Confirmation Required) ---
    suspend fun getCountdowns(): ApiResult<CountdownsListResponse> {
        return try {
            val response = apiService.getCountdowns()
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                localCountdowns.clear()
                localCountdowns.addAll(body.allCountdowns)
                localActiveCountdownId = body.currentActiveId
                ApiResult.Success(
                    body.copy(
                        countdowns = localCountdowns.toList(),
                        data = localCountdowns.toList(),
                        activeCountdownId = localActiveCountdownId,
                        activeStationCountdownId = localActiveCountdownId
                    )
                )
            } else {
                parseError(response)
            }
        } catch (e: java.net.SocketTimeoutException) {
            ApiResult.Error(code = "timeout", message = e.message ?: "Request timed out")
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network error")
        }
    }

    suspend fun createCountdown(name: String, targetDate: String): ApiResult<CountdownsListResponse> {
        val cleanName = name.trim()
        val cleanDate = targetDate.trim()
        if (cleanName.isEmpty() || cleanDate.isEmpty()) {
            return ApiResult.Error(code = "invalid_countdown", message = "Tên sự kiện và ngày đích không được để trống", statusCode = 400)
        }
        return try {
            val response = apiService.createCountdown(CreateCountdownRequest(name = cleanName, targetDate = cleanDate))
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                localCountdowns.clear()
                localCountdowns.addAll(body.allCountdowns)
                localActiveCountdownId = body.currentActiveId
                ApiResult.Success(
                    body.copy(
                        countdowns = localCountdowns.toList(),
                        data = localCountdowns.toList(),
                        activeCountdownId = localActiveCountdownId,
                        activeStationCountdownId = localActiveCountdownId
                    )
                )
            } else {
                parseError(response)
            }
        } catch (e: java.net.SocketTimeoutException) {
            ApiResult.Error(code = "timeout", message = e.message ?: "Request timed out")
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network error")
        }
    }

    suspend fun deleteCountdown(id: String): ApiResult<CountdownsListResponse> {
        val cleanId = id.trim()
        if (cleanId.isEmpty()) {
            return ApiResult.Error(code = "invalid_id", message = "Countdown ID cannot be empty", statusCode = 400)
        }
        if (!inFlightCountdownDeletes.add(cleanId)) {
            return ApiResult.Error(code = "duplicate_request", message = "Countdown deletion already in progress", statusCode = 409)
        }

        return try {
            val response = apiService.deleteCountdown(cleanId)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                localCountdowns.clear()
                localCountdowns.addAll(body.allCountdowns)
                localActiveCountdownId = body.currentActiveId
                ApiResult.Success(
                    CountdownsListResponse(
                        success = true,
                        countdowns = localCountdowns.toList(),
                        data = localCountdowns.toList(),
                        activeCountdownId = localActiveCountdownId,
                        activeStationCountdownId = localActiveCountdownId
                    )
                )
            } else {
                parseError(response)
            }
        } catch (e: java.net.SocketTimeoutException) {
            ApiResult.Error(code = "timeout", message = e.message ?: "Request timed out")
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network error")
        } finally {
            inFlightCountdownDeletes.remove(cleanId)
        }
    }

    suspend fun pinCountdown(id: String): ApiResult<ActionSuccessResponse> {
        val cleanId = id.trim()
        if (cleanId.isEmpty()) {
            return ApiResult.Error(code = "invalid_id", message = "Countdown ID cannot be empty", statusCode = 400)
        }
        return try {
            val response = apiService.pinCountdown(cleanId)
            if (response.isSuccessful && response.body() != null) {
                localActiveCountdownId = cleanId
                ApiResult.Success(response.body()!!)
            } else {
                parseError(response)
            }
        } catch (e: java.net.SocketTimeoutException) {
            ApiResult.Error(code = "timeout", message = e.message ?: "Request timed out")
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network error")
        }
    }

    // --- Weather Forecast ---
    suspend fun getWeatherForecast(city: String? = null): ApiResult<WeatherForecastResponse> {
        return try {
            val response = apiService.getWeatherForecast(city)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                ApiResult.Success(
                    body.copy(
                        city = body.resolvedCity,
                        intervals = body.allIntervals
                    )
                )
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network error")
        }
    }

    suspend fun generateQuote(topic: String, limit: Int = 75): ApiResult<QuoteResponse> {
        return try {
            val response = apiService.generateQuote(QuoteGenerateRequest(topic = topic, limit = limit))
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                val err = parseError(response)
                ApiResult.Error(code = err.code, message = err.message)
            }
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network error")
        }
    }

    suspend fun startTimer(duration: String, note: String): ApiResult<ActionSuccessResponse> {
        return try {
            val response = apiService.startTimer(TimerStartRequest(duration = duration, note = note))
            if (response.isSuccessful && response.body() != null) {
                ApiResult.Success(response.body()!!)
            } else {
                parseError(response)
            }
        } catch (e: Exception) {
            ApiResult.Error(code = "network_error", message = e.message ?: "Network error")
        }
    }

    suspend fun refreshAll() {
        refreshStatus()
        refreshFocusHistory()
        if (securePreferences.hasScope("control")) {
            refreshAlarm()
            refreshTimetable()
            refreshSystemStatus()
            refreshSettings()
        }
        if (securePreferences.hasScope("spotify")) {
            refreshSpotify()
        }
    }

    private fun <T> parseError(response: Response<T>): ApiResult.Error {
        val code = response.code()
        val errorBody = response.errorBody()?.string()
        val headerRetryAfter = response.headers()["Retry-After"]?.toIntOrNull()

        if (!errorBody.isNullOrBlank()) {
            try {
                val envelope = json.decodeFromString<ErrorEnvelope>(errorBody)
                return ApiResult.Error(
                    code = envelope.error.code,
                    message = envelope.error.code,
                    retryAfter = envelope.error.retryAfter ?: headerRetryAfter,
                    statusCode = code
                )
            } catch (_: Exception) {}

            try {
                // Secondary check for flat JSON error, message, or detail
                val jsonObj = json.parseToJsonElement(errorBody)
                if (jsonObj is kotlinx.serialization.json.JsonObject) {
                    val flatError = jsonObj["error"]?.let {
                        if (it is kotlinx.serialization.json.JsonPrimitive) it.content else null
                    }
                    val flatMsg = jsonObj["message"]?.let {
                        if (it is kotlinx.serialization.json.JsonPrimitive) it.content else null
                    }
                    val detail = jsonObj["detail"]?.let {
                        if (it is kotlinx.serialization.json.JsonPrimitive) it.content else null
                    }
                    val retry = jsonObj["retry_after"]?.let {
                        if (it is kotlinx.serialization.json.JsonPrimitive) it.content.toIntOrNull() else null
                    } ?: headerRetryAfter

                    if (flatError != null || detail != null || flatMsg != null) {
                        val errCode = flatError ?: (if (code == 429) "rate_limited" else "api_error")
                        val errMsg = flatMsg ?: detail ?: flatError ?: "HTTP Error $code"
                        return ApiResult.Error(
                            code = errCode,
                            message = errMsg,
                            retryAfter = retry,
                            statusCode = code
                        )
                    }
                }
            } catch (_: Exception) {}
        }

        return ApiResult.Error(
            code = when (code) {
                400 -> "bad_request"
                401 -> "unauthorized"
                403 -> "forbidden"
                404 -> "not_found"
                413 -> "too_large"
                429 -> "rate_limited"
                500 -> "server_error"
                503 -> "storage_unavailable"
                else -> "http_error_$code"
            },
            message = "HTTP Error $code",
            retryAfter = headerRetryAfter,
            statusCode = code
        )
    }

    suspend fun testApiHealth(): ApiHealthTestResult {
        val startTime = System.currentTimeMillis()
        return try {
            val response = apiService.getHealth()
            val latency = System.currentTimeMillis() - startTime
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                ApiHealthTestResult(
                    isSuccess = true,
                    statusCode = response.code(),
                    latencyMs = latency,
                    service = body.service,
                    version = body.version
                )
            } else {
                ApiHealthTestResult(
                    isSuccess = false,
                    statusCode = response.code(),
                    latencyMs = latency,
                    errorType = "HTTP_${response.code()}",
                    errorMessage = "Server returned HTTP ${response.code()}: ${response.message()}"
                )
            }
        } catch (e: java.net.UnknownHostException) {
            ApiHealthTestResult(
                isSuccess = false,
                latencyMs = System.currentTimeMillis() - startTime,
                errorType = "DNS_ERROR",
                errorMessage = "Cannot resolve hostname (${e.message}). Check internet/DNS."
            )
        } catch (e: java.net.SocketTimeoutException) {
            ApiHealthTestResult(
                isSuccess = false,
                latencyMs = System.currentTimeMillis() - startTime,
                errorType = "TIMEOUT",
                errorMessage = "Connection timed out (${e.message}). Server did not respond."
            )
        } catch (e: java.io.IOException) {
            ApiHealthTestResult(
                isSuccess = false,
                latencyMs = System.currentTimeMillis() - startTime,
                errorType = "NETWORK_ERROR",
                errorMessage = "Network I/O error: ${e.message}"
            )
        } catch (e: Exception) {
            ApiHealthTestResult(
                isSuccess = false,
                latencyMs = System.currentTimeMillis() - startTime,
                errorType = "UNKNOWN_ERROR",
                errorMessage = e.message ?: "Unexpected error occurred"
            )
        }
    }
}
