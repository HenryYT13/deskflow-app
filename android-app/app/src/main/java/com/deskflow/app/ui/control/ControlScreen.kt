package com.deskflow.app.ui.control

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deskflow.app.R
import com.deskflow.app.data.local.SecurePreferences
import com.deskflow.app.data.model.*
import com.deskflow.app.data.repository.ApiResult
import com.deskflow.app.data.repository.FocusRepository
import com.deskflow.app.ui.morse.MorseConverterScreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.deskflow.app.ui.i18n.AppStrings

// ================= HIERARCHY DATA DEFINITIONS =================

data class ControlCategoryItem(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    val icon: ImageVector,
    val subcategories: List<ControlSubCategoryItem>
)

data class ControlSubCategoryItem(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    val icon: ImageVector
)

data class ControlNavState(
    val categoryId: String?,
    val subCategoryId: String?
) {
    val level: Int get() = when {
        categoryId == null -> 1
        subCategoryId == null -> 2
        else -> 3
    }
}

val CONTROL_CATEGORIES = listOf(
    ControlCategoryItem(
        id = "hardware",
        titleRes = R.string.cat_hardware,
        descRes = R.string.cat_hardware_desc,
        icon = Icons.Default.Memory,
        subcategories = listOf(
            ControlSubCategoryItem(
                id = "subcat_led",
                titleRes = R.string.subcat_led,
                descRes = R.string.subcat_led_desc,
                icon = Icons.Default.Lightbulb
            ),
            ControlSubCategoryItem(
                id = "subcat_display",
                titleRes = R.string.subcat_display,
                descRes = R.string.subcat_display_desc,
                icon = Icons.Default.Screenshot
            ),
            ControlSubCategoryItem(
                id = "subcat_buzzer",
                titleRes = R.string.subcat_buzzer,
                descRes = R.string.subcat_buzzer_desc,
                icon = Icons.Default.NotificationsActive
            )
        )
    ),
    ControlCategoryItem(
        id = "alarms",
        titleRes = R.string.cat_alarms,
        descRes = R.string.cat_alarms_desc,
        icon = Icons.Default.Alarm,
        subcategories = listOf(
            ControlSubCategoryItem(
                id = "subcat_alarm_active",
                titleRes = R.string.subcat_alarm_active,
                descRes = R.string.subcat_alarm_active_desc,
                icon = Icons.Default.Alarm
            ),
            ControlSubCategoryItem(
                id = "subcat_alarm_schedule",
                titleRes = R.string.subcat_alarm_schedule,
                descRes = R.string.subcat_alarm_schedule_desc,
                icon = Icons.Default.AccessTime
            )
        )
    ),
    ControlCategoryItem(
        id = "tools",
        titleRes = R.string.cat_tools,
        descRes = R.string.cat_tools_desc,
        icon = Icons.Default.AutoAwesome,
        subcategories = listOf(
            ControlSubCategoryItem(
                id = "subcat_tools_notes",
                titleRes = R.string.subcat_tools_notes,
                descRes = R.string.subcat_tools_notes_desc,
                icon = Icons.Default.NoteAlt
            ),
            ControlSubCategoryItem(
                id = "subcat_morse",
                titleRes = R.string.subcat_morse,
                descRes = R.string.subcat_morse_desc,
                icon = Icons.Default.GraphicEq
            ),
            ControlSubCategoryItem(
                id = "subcat_tools_ai",
                titleRes = R.string.subcat_tools_ai,
                descRes = R.string.subcat_tools_ai_desc,
                icon = Icons.Default.Psychology
            ),
            ControlSubCategoryItem(
                id = "subcat_tools_timer",
                titleRes = R.string.subcat_tools_timer,
                descRes = R.string.subcat_tools_timer_desc,
                icon = Icons.Default.Timer
            )
        )
    ),
    ControlCategoryItem(
        id = "settings",
        titleRes = R.string.cat_settings,
        descRes = R.string.cat_settings_desc,
        icon = Icons.Default.Settings,
        subcategories = listOf(
            ControlSubCategoryItem(
                id = "subcat_settings_display",
                titleRes = R.string.subcat_settings_display,
                descRes = R.string.subcat_settings_display_desc,
                icon = Icons.Default.DarkMode
            ),
            ControlSubCategoryItem(
                id = "subcat_settings_power",
                titleRes = R.string.subcat_settings_power,
                descRes = R.string.subcat_settings_power_desc,
                icon = Icons.Default.BatteryChargingFull
            )
        )
    ),
    ControlCategoryItem(
        id = "system",
        titleRes = R.string.cat_system,
        descRes = R.string.cat_system_desc,
        icon = Icons.Default.Analytics,
        subcategories = listOf(
            ControlSubCategoryItem(
                id = "subcat_system_telemetry",
                titleRes = R.string.subcat_system_telemetry,
                descRes = R.string.subcat_system_telemetry_desc,
                icon = Icons.Default.Speed
            )
        )
    )
)

// ================= MAIN CONTROL SCREEN COMPOSABLE =================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlScreen(
    repository: FocusRepository,
    securePreferences: SecurePreferences,
    onNavigateHome: () -> Unit = {},
    onOpenScreenSwitcher: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val alarmState by repository.alarmState.collectAsState()
    val systemStatus by repository.systemStatus.collectAsState()
    val settingsState by repository.settings.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Navigation hierarchy state
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedSubCategory by rememberSaveable { mutableStateOf<String?>(null) }

    // Alarm state
    var answerText by rememberSaveable { mutableStateOf("") }
    var isSubmittingAlarm by remember { mutableStateOf(false) }
    var showEmergencyConfirm by remember { mutableStateOf(false) }
    var scheduleAlarmTime by rememberSaveable { mutableStateOf("06:30") }
    var isSchedulingAlarm by remember { mutableStateOf(false) }

    // Display & Lighting
    var currentView by remember { mutableStateOf("main") }
    var selectedColorHex by rememberSaveable { mutableStateOf("#5865F2") }
    var ledBrightness by remember { mutableFloatStateOf(80f) }
    var lastSentLedBright by remember { mutableIntStateOf(80) }
    var ledDimJob by remember { mutableStateOf<Job?>(null) }
    var lastLedSentTime by remember { mutableLongStateOf(0L) }

    var selectedClockStyle by rememberSaveable { mutableStateOf("digital_classic") }
    var clockConfig by remember { mutableStateOf(ClockConfigDto()) }
    var clockConfigLoading by remember { mutableStateOf(false) }
    var clockConfigSaving by remember { mutableStateOf(false) }

    // Tools
    var stickyNoteText by rememberSaveable { mutableStateOf("") }
    var examNameText by rememberSaveable { mutableStateOf("") }
    var examDateText by rememberSaveable { mutableStateOf("15/01") }
    var quoteTopicText by rememberSaveable { mutableStateOf(AppStrings.t("học tập và kiên trì")) }
    var pomodoroDurationText by rememberSaveable { mutableStateOf("25m") }
    var pomodoroNoteText by rememberSaveable { mutableStateOf("Tập trung học") }

    // Settings
    var editDarkMode by rememberSaveable { mutableStateOf("off") }
    var editDarkModeStart by rememberSaveable { mutableStateOf("22:00") }
    var editDarkModeEnd by rememberSaveable { mutableStateOf("06:00") }
    var editLedBrightness by rememberSaveable { mutableIntStateOf(100) }
    var editBuzzerEnabled by rememberSaveable { mutableStateOf(true) }
    var editBuzzerDuration by rememberSaveable { mutableIntStateOf(40) }
    var editWeatherAlertEnabled by rememberSaveable { mutableStateOf(true) }
    var editWeatherAlertTime by rememberSaveable { mutableStateOf("06:15") }
    var editWeatherCity by rememberSaveable { mutableStateOf("Ha Long") }
    var editBatteryLow by rememberSaveable { mutableIntStateOf(30) }
    var editBatteryHigh by rememberSaveable { mutableIntStateOf(80) }

    var isRefreshing by remember { mutableStateOf(false) }

    // Notes CRUD State
    var notesList by remember { mutableStateOf<List<NoteItemDto>>(emptyList()) }
    var activeStationNoteId by remember { mutableStateOf<String?>(null) }
    var isLoadingNotes by remember { mutableStateOf(false) }
    var deletingNoteId by remember { mutableStateOf<String?>(null) }

    // Countdowns CRUD State
    var countdownsList by remember { mutableStateOf<List<CountdownItemDto>>(emptyList()) }
    var activeStationCountdownId by remember { mutableStateOf<String?>(null) }
    var isLoadingCountdowns by remember { mutableStateOf(false) }
    var deletingCountdownId by remember { mutableStateOf<String?>(null) }

    // Weather Forecast State
    var weatherForecastIntervals by remember { mutableStateOf<List<WeatherIntervalDto>>(emptyList()) }
    var isLoadingWeatherForecast by remember { mutableStateOf(false) }

    fun showFeedback(msg: String) {
        coroutineScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val job = launch {
                snackbarHostState.showSnackbar(msg)
            }
            kotlinx.coroutines.delay(1500L)
            job.cancel()
            snackbarHostState.currentSnackbarData?.dismiss()
        }
    }

    fun formatControlError(action: String, error: ApiResult.Error): String {
        return when {
            error.statusCode == 400 || error.code.startsWith("invalid_") ->
                AppStrings.tpl("$action: Dữ liệu không hợp lệ (${error.message.ifBlank { error.code }})")
            error.statusCode == 401 || error.code == "unauthorized" ->
                AppStrings.tpl("$action: Phiên xác thực hết hạn hoặc thiết bị chưa ghép đôi (401)")
            error.statusCode == 403 || error.code == "forbidden" || error.code == "insufficient_scope" ->
                AppStrings.tpl("$action: Thiết bị không có quyền 'control' (403)")
            error.statusCode == 404 || error.code == "not_found" || error.code.contains("not_found") ->
                AppStrings.tpl("$action: Mục này không còn tồn tại trên máy chủ (404)")
            error.statusCode == 405 ->
                AppStrings.tpl("$action: Endpoint chưa kích hoạt trên máy chủ sản xuất (405)")
            error.statusCode == 409 || error.code == "duplicate_request" ->
                AppStrings.tpl("$action: Đang xử lý yêu cầu trước đó, vui lòng chờ...")
            error.statusCode == 500 || error.code == "server_error" ->
                AppStrings.tpl("$action: Lỗi máy chủ nội bộ (500)")
            error.code == "timeout" ->
                AppStrings.tpl("$action: Quá thời gian chờ phản hồi từ máy chủ (Timeout)")
            error.code == "network_error" ->
                AppStrings.tpl("$action: Không thể kết nối máy chủ (${error.message})")
            else ->
                AppStrings.tpl("$action: Thất bại (${error.message.ifBlank { error.code }})")
        }
    }

    fun refreshNotes() {
        coroutineScope.launch {
            isLoadingNotes = true
            val res = repository.getNotes()
            if (res is ApiResult.Success) {
                notesList = res.data.allNotes
                activeStationNoteId = res.data.currentActiveNoteId
            } else if (res is ApiResult.Error) {
                showFeedback(formatControlError(AppStrings.t("Tải ghi chú"), res))
            }
            isLoadingNotes = false
        }
    }

    fun refreshCountdowns() {
        coroutineScope.launch {
            isLoadingCountdowns = true
            val res = repository.getCountdowns()
            if (res is ApiResult.Success) {
                countdownsList = res.data.allCountdowns
                activeStationCountdownId = res.data.currentActiveId
            } else if (res is ApiResult.Error) {
                showFeedback(formatControlError(AppStrings.t("Tải đếm ngược"), res))
            }
            isLoadingCountdowns = false
        }
    }

    fun refreshWeatherForecast() {
        coroutineScope.launch {
            isLoadingWeatherForecast = true
            val res = repository.getWeatherForecast(editWeatherCity)
            if (res is ApiResult.Success) {
                weatherForecastIntervals = res.data.allIntervals
            }
            isLoadingWeatherForecast = false
        }
    }

    fun handleAddNote(text: String, color: String) {
        coroutineScope.launch {
            val res = repository.createNote(text, color)
            if (res is ApiResult.Success) {
                notesList = res.data.allNotes
                activeStationNoteId = res.data.currentActiveNoteId
                showFeedback(AppStrings.t("Đã lưu ghi chú thành công!"))
            } else if (res is ApiResult.Error) {
                showFeedback(formatControlError(AppStrings.t("Lưu ghi chú"), res))
            }
        }
    }

    fun handleDeleteNote(id: String) {
        if (deletingNoteId == id) return
        coroutineScope.launch {
            deletingNoteId = id
            val res = repository.deleteNote(id)
            deletingNoteId = null
            if (res is ApiResult.Success) {
                notesList = res.data.allNotes
                activeStationNoteId = res.data.currentActiveNoteId
                showFeedback(AppStrings.t("Đã xóa ghi chú khỏi máy chủ và trạm!"))
            } else if (res is ApiResult.Error) {
                showFeedback(formatControlError("Xóa ghi chú", res))
                if (res.statusCode == 404 || res.code.contains("not_found")) {
                    refreshNotes()
                }
            }

        }
    }

    fun handlePinNote(id: String) {
        coroutineScope.launch {
            val res = repository.pinNote(id)
            if (res is ApiResult.Success) {
                activeStationNoteId = id
                showFeedback(AppStrings.t("Đã ghim ghi chú lên màn hình trạm!"))
            } else if (res is ApiResult.Error) {
                showFeedback(formatControlError("Ghim ghi chú", res))
            }
        }
    }

    fun handleUnpinNote() {
        coroutineScope.launch {
            val res = repository.unpinNote()
            if (res is ApiResult.Success) {
                activeStationNoteId = null
                showFeedback(AppStrings.t("Đã bỏ ghim ghi chú khỏi màn hình trạm!"))
            } else if (res is ApiResult.Error) {
                showFeedback(formatControlError("Bỏ ghim", res))
            }
        }
    }

    fun handleAddCountdown(name: String, targetDate: String) {
        coroutineScope.launch {
            val res = repository.createCountdown(name, targetDate)
            if (res is ApiResult.Success) {
                countdownsList = res.data.allCountdowns
                activeStationCountdownId = res.data.currentActiveId
                showFeedback(AppStrings.tpl("Đã thêm đếm ngược $name!"))
            } else if (res is ApiResult.Error) {
                showFeedback(formatControlError(AppStrings.t("Thêm đếm ngược"), res))
            }
        }
    }

    fun handleDeleteCountdown(id: String) {
        if (deletingCountdownId == id) return
        coroutineScope.launch {
            deletingCountdownId = id
            val res = repository.deleteCountdown(id)
            deletingCountdownId = null
            if (res is ApiResult.Success) {
                countdownsList = res.data.allCountdowns
                activeStationCountdownId = res.data.currentActiveId
                showFeedback(AppStrings.t("Đã xóa đếm ngược!"))
            } else if (res is ApiResult.Error) {
                showFeedback(formatControlError(AppStrings.t("Xóa đếm ngược"), res))
                if (res.statusCode == 404 || res.code.contains("not_found")) {
                    refreshCountdowns()
                }
            }

        }
    }

    fun handlePinCountdown(id: String) {
        coroutineScope.launch {
            val res = repository.pinCountdown(id)
            if (res is ApiResult.Success) {
                activeStationCountdownId = id
                showFeedback(AppStrings.t("Đã ghim đếm ngược lên màn hình trạm!"))
            } else if (res is ApiResult.Error) {
                showFeedback(formatControlError(AppStrings.t("Ghim đếm ngược"), res))
            }
        }
    }

    // Back handling for 3-level hierarchy: Level 3 -> Level 2 -> Level 1 -> Home
    BackHandler(enabled = true) {
        if (selectedSubCategory != null) {
            selectedSubCategory = null
        } else if (selectedCategory != null) {
            selectedCategory = null
        } else {
            onNavigateHome()
        }
    }

    LaunchedEffect(Unit) {
        repository.refreshAlarm()
        repository.refreshSystemStatus()
        repository.refreshSettings()
        coroutineScope.launch {
            clockConfigLoading = true
            val res = repository.getClockConfig()
            if (res is ApiResult.Success) {
                clockConfig = res.data
                selectedClockStyle = res.data.style
            }
            clockConfigLoading = false
        }
    }

    // Poll the station status so the OLED preview follows whatever the ESP32 is
    // actually showing (including screens changed from another app or buttons).
    LaunchedEffect(Unit) {
        var lastSyncedLang: String? = null
        while (true) {
            repository.refreshSystemStatus()
            val clkRes = repository.getClockConfig()
            if (clkRes is ApiResult.Success) {
                clockConfig = clkRes.data
                selectedClockStyle = clkRes.data.style
                val srvLang = clkRes.data.language.trim().lowercase()
                if ((srvLang == "vi" || srvLang == "en") && srvLang != lastSyncedLang) {
                    lastSyncedLang = srvLang
                    if (securePreferences.language != srvLang) {
                        securePreferences.language = srvLang
                    }
                }
            }
            kotlinx.coroutines.delay(3000L)
        }
    }

    LaunchedEffect(systemStatus) {
        systemStatus?.station?.currentView?.let { currentView = it }
    }

    LaunchedEffect(settingsState) {
        settingsState?.let { s ->
            editDarkMode = s.darkmodeMode
            editDarkModeStart = s.darkmodeStart
            editDarkModeEnd = s.darkmodeEnd
            editLedBrightness = s.ledBrightness
            editBuzzerEnabled = s.buzzerEnabled
            editBuzzerDuration = s.buzzerDuration
            editWeatherAlertEnabled = s.weatherAlertEnabled
            editWeatherAlertTime = s.weatherAlertTime
            editWeatherCity = s.weatherCity
            editBatteryLow = s.batteryLowThresh
            editBatteryHigh = s.batteryHighThresh
            selectedClockStyle = s.clockStyle
        }
    }

    LaunchedEffect(selectedCategory, selectedSubCategory) {
        if (selectedCategory == "tools" || selectedSubCategory == "subcat_tools_notes") {
            refreshNotes()
            refreshCountdowns()
        } else if (selectedSubCategory == "subcat_settings_power") {
            refreshWeatherForecast()
        }
    }

    // Emergency Stop Confirmation Dialog
    if (showEmergencyConfirm) {
        AlertDialog(
            onDismissRequest = { showEmergencyConfirm = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(stringResource(R.string.alarm_emergency_stop)) },
            text = { Text(AppStrings.t("Dừng còi khẩn cấp và thiết lập lại trạng thái báo thức trên thiết bị?")) },
            confirmButton = {
                Button(
                    onClick = {
                        showEmergencyConfirm = false
                        coroutineScope.launch {
                            val res = repository.emergencyStop()
                            if (res is ApiResult.Success) {
                                showFeedback(AppStrings.t("Đã dừng khẩn cấp còi báo!"))
                            }
                            repository.refreshAlarm()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.alarm_emergency_stop))
                }
            }
,
            dismissButton = {
                TextButton(onClick = { showEmergencyConfirm = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }

        )
    }

    val currentCatItem = CONTROL_CATEGORIES.find { it.id == selectedCategory }
    val currentSubCatItem = currentCatItem?.subcategories?.find { it.id == selectedSubCategory }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    AnimatedVisibility(
                        visible = selectedCategory != null,
                        enter = fadeIn(animationSpec = tween(200)) + scaleIn(initialScale = 0.8f),
                        exit = fadeOut(animationSpec = tween(150)) + scaleOut(targetScale = 0.8f)
                    ) {
                        IconButton(onClick = {
                            if (selectedSubCategory != null) {
                                selectedSubCategory = null
                            } else {
                                selectedCategory = null
                            }
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                title = {
                    AnimatedContent(
                        targetState = Pair(selectedCategory, selectedSubCategory),
                        transitionSpec = {
                            fadeIn(animationSpec = tween(220)).togetherWith(fadeOut(animationSpec = tween(150)))
                        },
                        label = "TopAppBarTitleTransition"
                    ) { (cat, subcat) ->
                        val catItem = CONTROL_CATEGORIES.find { it.id == cat }
                        val subCatItem = catItem?.subcategories?.find { it.id == subcat }
                        Column {
                            Text(
                                text = when {
                                    subCatItem != null -> stringResource(subCatItem.titleRes)
                                    catItem != null -> stringResource(catItem.titleRes)
                                    else -> stringResource(R.string.nav_control)
                                },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = when {
                                    subCatItem != null -> stringResource(subCatItem.descRes)
                                    catItem != null -> stringResource(catItem.descRes)
                                    else -> stringResource(R.string.card_control_title)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                isRefreshing = true
                                repository.refreshAlarm()
                                repository.refreshSystemStatus()
                                repository.refreshSettings()
                                if (selectedSubCategory == "subcat_tools_notes") {
                                    refreshNotes()
                                    refreshCountdowns()
                                }
                                isRefreshing = false
                            }
                        },
                        enabled = !isRefreshing
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                    IconButton(onClick = onOpenScreenSwitcher) {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = stringResource(R.string.screen_switcher_title),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        if (!securePreferences.hasScope("control")) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Scope 'control' Required",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = stringResource(R.string.scope_control_required),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            return@Scaffold
        }

        val navState = remember(selectedCategory, selectedSubCategory) {
            ControlNavState(selectedCategory, selectedSubCategory)
        }

        AnimatedContent(
            targetState = navState,
            transitionSpec = {
                val forward = targetState.level >= initialState.level
                if (forward) {
                    (slideInHorizontally(animationSpec = tween(280)) { fullWidth -> fullWidth / 3 } +
                     fadeIn(animationSpec = tween(280))).togetherWith(
                        slideOutHorizontally(animationSpec = tween(240)) { fullWidth -> -fullWidth / 4 } +
                        fadeOut(animationSpec = tween(180))
                    )
                } else {
                    (slideInHorizontally(animationSpec = tween(280)) { fullWidth -> -fullWidth / 4 } +
                     fadeIn(animationSpec = tween(280))).togetherWith(
                        slideOutHorizontally(animationSpec = tween(240)) { fullWidth -> fullWidth / 3 } +
                        fadeOut(animationSpec = tween(180))
                    )
                }
            },
            label = "ControlNavigationTransition",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { state ->
            val targetCat = CONTROL_CATEGORIES.find { it.id == state.categoryId }
            val targetSubCat = targetCat?.subcategories?.find { it.id == state.subCategoryId }

            when {
                // ================= LEVEL 1: CATEGORIES =================
                state.categoryId == null -> {
                    ControlCategoryListView(
                        categories = CONTROL_CATEGORIES,
                        alarmActive = alarmState.active,
                        onCategorySelect = { catId -> selectedCategory = catId },
                        onQuickSolveAlarm = {
                            selectedCategory = "alarms"
                            selectedSubCategory = "subcat_alarm_active"
                        }
                    )
                }

                // ================= LEVEL 2: SUB-CATEGORIES =================
                state.subCategoryId == null && targetCat != null -> {
                    ControlSubCategoryListView(
                        category = targetCat,
                        alarmActive = alarmState.active,
                        onSubCategorySelect = { subcatId -> selectedSubCategory = subcatId },
                        notes = notesList,
                        activeNoteId = activeStationNoteId,
                        isLoadingNotes = isLoadingNotes,
                        onAddNote = ::handleAddNote,
                        onDeleteNote = ::handleDeleteNote,
                        onPinNote = ::handlePinNote,
                        onUnpinNote = ::handleUnpinNote,
                        countdowns = countdownsList,
                        activeCountdownId = activeStationCountdownId,
                        isLoadingCountdowns = isLoadingCountdowns,
                        onAddCountdown = ::handleAddCountdown,
                        onDeleteCountdown = ::handleDeleteCountdown,
                        onPinCountdown = ::handlePinCountdown
                    )
                }

                // ================= LEVEL 3: SETTINGS & CONTROLS =================
                targetSubCat != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        Spacer(modifier = Modifier.height(2.dp))

                        when (targetSubCat.id) {
                            // Hardware > Lighting & LEDs
                            "subcat_led" -> {
                                LedControlsCard(
                                    selectedColorHex = selectedColorHex,
                                    onColorSelect = { hex ->
                                        selectedColorHex = hex
                                        coroutineScope.launch {
                                            repository.setLampColor(hex)
                                            showFeedback(AppStrings.tpl("Đã chuyển màu đèn: $hex"))
                                        }
                                    },
                                    ledBrightness = ledBrightness,
                                    onLedBrightnessChange = { newFloat ->
                                        ledBrightness = newFloat
                                        val target = newFloat.roundToInt()
                                        if (target != lastSentLedBright) {
                                            ledDimJob?.cancel()
                                            ledDimJob = coroutineScope.launch {
                                                val now = System.currentTimeMillis()
                                                val elapsed = now - lastLedSentTime
                                                if (elapsed < 50) {
                                                    delay(50 - elapsed)
                                                }
                                                lastLedSentTime = System.currentTimeMillis()
                                                lastSentLedBright = target
                                                repository.setLampDim(led = target)
                                            }
                                        }
                                    },
                                    onLedBrightnessFinished = {
                                        val finalTarget = ledBrightness.roundToInt()
                                        ledDimJob?.cancel()
                                        ledDimJob = coroutineScope.launch {
                                            lastLedSentTime = System.currentTimeMillis()
                                            lastSentLedBright = finalTarget
                                            repository.setLampDim(led = finalTarget)
                                        }
                                    },
                                    onTurnOnLamp = {
                                        coroutineScope.launch {
                                            repository.controlLed("on")
                                            showFeedback(AppStrings.t("Đã bật đèn LED!"))
                                        }
                                    },
                                    onTurnOffLamp = {
                                        coroutineScope.launch {
                                            repository.turnLampOff()
                                            showFeedback(AppStrings.t("Đã tắt đèn LED!"))
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                ShopeeRgbEffectsCard(
                                    repository = repository,
                                    settingsState = settingsState,
                                    coroutineScope = coroutineScope,
                                    onFeedback = { showFeedback(it) }
                                )
                            }

                            // Hardware > Display & Watchface
                            "subcat_display" -> {
                                DisplayControlsCard(
                                    currentView = currentView,
                                    onSwitchView = { vKey, vLabel ->
                                        currentView = vKey
                                        coroutineScope.launch {
                                            val res = repository.switchDisplayView(vKey)
                                            if (res is ApiResult.Success) {
                                                showFeedback(AppStrings.tpl("Đã chuyển màn hình sang $vLabel"))
                                            } else if (res is ApiResult.Error) {
                                                showFeedback(formatControlError(AppStrings.t("Chuyển màn hình"), res))
                                            }
                                        }
                                    },
                                    onCloseTkb = {
                                        coroutineScope.launch {
                                            val res = repository.closeTkb()
                                            if (res is ApiResult.Success) {
                                                currentView = "main"
                                                showFeedback(AppStrings.t("Đã đưa màn hình về Dashboard chính"))
                                            } else if (res is ApiResult.Error) {
                                                showFeedback(formatControlError(AppStrings.t("Đóng TKB"), res))
                                            }
                                        }
                                    }
                                )

                                ClockStyleStudioCard(
                                    clockConfig = clockConfig,
                                    isLoading = clockConfigLoading,
                                    isSaving = clockConfigSaving,
                                    onUpdateConfig = { newConfig ->
                                        clockConfigSaving = true
                                        coroutineScope.launch {
                                            val res = repository.updateClockConfig(newConfig)
                                            if (res is ApiResult.Success) {
                                                clockConfig = res.data
                                                selectedClockStyle = res.data.style
                                                if (res.data.appliedByStation) {
                                                    showFeedback(AppStrings.t("✓ Đã đồng bộ & Áp dụng trên trạm ESP32!"))
                                                } else {
                                                    showFeedback(AppStrings.t("Đã lưu máy chủ! Đang chờ trạm ESP32 kết nối để áp dụng..."))
                                                }
                                            } else if (res is ApiResult.Error) {
                                                showFeedback(formatControlError(AppStrings.t("Cài đặt mặt đồng hồ"), res))
                                            }
                                            clockConfigSaving = false
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                MiniGamesAndBootLogoCard(
                                    repository = repository,
                                    coroutineScope = coroutineScope,
                                    onFeedback = { showFeedback(it) }
                                )
                            }

                            // Hardware > Buzzer & Sounds
                            "subcat_buzzer" -> {
                                BuzzerControlsCard(
                                    buzzerEnabled = editBuzzerEnabled,
                                    onBuzzerEnabledChange = { editBuzzerEnabled = it },
                                    buzzerDuration = editBuzzerDuration,
                                    onBuzzerDurationChange = { editBuzzerDuration = it },
                                    savedDuration = settingsState?.buzzerDuration ?: 40,
                                    onSaveSettings = {
                                        val newSettings = StationSettingsDto(
                                            darkmodeMode = editDarkMode,
                                            darkmodeStart = editDarkModeStart,
                                            darkmodeEnd = editDarkModeEnd,
                                            ledBrightness = editLedBrightness,
                                            buzzerEnabled = editBuzzerEnabled,
                                            buzzerDuration = editBuzzerDuration,
                                            weatherAlertEnabled = editWeatherAlertEnabled,
                                            weatherAlertTime = editWeatherAlertTime,
                                            weatherCity = editWeatherCity,
                                            batteryLowThresh = editBatteryLow,
                                            batteryHighThresh = editBatteryHigh,
                                            quoteSchedules = settingsState?.quoteSchedules ?: listOf("06:30", "12:00", "20:00"),
                                            clockStyle = selectedClockStyle
                                        )
                                        coroutineScope.launch {
                                            val res = repository.updateSettings(newSettings)
                                            if (res is ApiResult.Success) {
                                                showFeedback(AppStrings.t("✓ Đã lưu cài đặt còi thành công!"))
                                            } else if (res is ApiResult.Error) {
                                                showFeedback(formatControlError(AppStrings.t("Cài đặt còi"), res))
                                            }
                                        }
                                    },
                                    onTriggerBuzz = { dur ->
                                        coroutineScope.launch {
                                            val res = repository.triggerBuzz(dur)
                                            if (res is ApiResult.Success) {
                                                showFeedback(AppStrings.tpl("Đã kích hoạt còi bíp trên trạm ($dur ms)!"))
                                            } else if (res is ApiResult.Error) {
                                                showFeedback(formatControlError(AppStrings.t("Kích hoạt còi"), res))
                                            }
                                        }
                                    },
                                    onEmergencyStop = { showEmergencyConfirm = true },
                                    onOpenMorseConverter = {
                                        selectedCategory = "tools"
                                        selectedSubCategory = "subcat_morse"
                                    }
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                MidiJukeboxCard(
                                    repository = repository,
                                    coroutineScope = coroutineScope,
                                    onShowMessage = { showFeedback(it) }
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                SavedMelodyAndMiniPianoCard(
                                    repository = repository,
                                    coroutineScope = coroutineScope,
                                    onFeedback = { showFeedback(it) }
                                )
                            }

                            // Alarms > Active Math Alarm
                            "subcat_alarm_active" -> {
                                ActiveAlarmCard(
                                    alarmState = alarmState,
                                    answerText = answerText,
                                    onAnswerChange = { answerText = it.filter { c -> c.isDigit() || c == '-' } },
                                    isSubmittingAlarm = isSubmittingAlarm,
                                    onSolveAlarm = {
                                        val ans = answerText.toIntOrNull()
                                        if (ans != null && alarmState.sessionId != null) {
                                            isSubmittingAlarm = true
                                            coroutineScope.launch {
                                                val res = repository.solveAlarm(ans)
                                                isSubmittingAlarm = false
                                                if (res is ApiResult.Success) {
                                                    answerText = ""
                                                    showFeedback(AppStrings.t("Đã giải đúng và dập tắt còi!"))
                                                    repository.refreshAlarm()
                                                } else if (res is ApiResult.Error) {
                                                    if (res.code == "invalid_request") {
                                                        showFeedback(AppStrings.t("Phiên báo thức đã kết thúc hoặc không khớp"))
                                                        repository.refreshAlarm()
                                                    } else {
                                                        showFeedback(AppStrings.tpl("Đáp án chưa chính xác: ${res.code}"))
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    onEmergencyStop = { showEmergencyConfirm = true },
                                    focusManager = focusManager
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                ShakeAlarmDismissCard(
                                    alarmActive = alarmState.active,
                                    repository = repository,
                                    coroutineScope = coroutineScope,
                                    onDismissed = { showFeedback(it) }
                                )
                            }

                            // Alarms > Alarm Scheduling
                            "subcat_alarm_schedule" -> {
                                ScheduleAlarmCard(
                                    scheduleTime = scheduleAlarmTime,
                                    onScheduleTimeChange = { scheduleAlarmTime = it },
                                    isScheduling = isSchedulingAlarm,
                                    onScheduleClick = {
                                        if (scheduleAlarmTime.isNotBlank()) {
                                            isSchedulingAlarm = true
                                            coroutineScope.launch {
                                                val res = repository.scheduleAlarm(scheduleAlarmTime.trim())
                                                isSchedulingAlarm = false
                                                if (res is ApiResult.Success) {
                                                    showFeedback(AppStrings.tpl("Đã hẹn giờ báo thức lúc $scheduleAlarmTime!"))
                                                } else if (res is ApiResult.Error) {
                                                    showFeedback(formatControlError(AppStrings.t("Hẹn giờ báo thức"), res))
                                                }
                                            }
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                PersistentAlarmsAndBriefingCard(
                                    repository = repository,
                                    settingsState = settingsState,
                                    coroutineScope = coroutineScope,
                                    onFeedback = { showFeedback(it) }
                                )
                            }

                            // Tools > Persistent Notes & Countdowns Manager
                            "subcat_tools_notes" -> {
                                NotesAndCountdownsManagerCard(
                                    notes = notesList,
                                    activeNoteId = activeStationNoteId,
                                    isLoadingNotes = isLoadingNotes,
                                    deletingNoteId = deletingNoteId,
                                    onAddNote = ::handleAddNote,
                                    onDeleteNote = ::handleDeleteNote,
                                    onPinNote = ::handlePinNote,
                                    onUnpinNote = ::handleUnpinNote,
                                    countdowns = countdownsList,
                                    activeCountdownId = activeStationCountdownId,
                                    isLoadingCountdowns = isLoadingCountdowns,
                                    deletingCountdownId = deletingCountdownId,
                                    onAddCountdown = ::handleAddCountdown,
                                    onDeleteCountdown = ::handleDeleteCountdown,
                                    onPinCountdown = ::handlePinCountdown
                                )
                            }

                            // Tools > Morse Code Converter
                            "subcat_morse" -> {
                                MorseConverterScreen(
                                    repository = repository,
                                    onBack = { selectedSubCategory = null },
                                    isEmbedded = true,
                                    onShowMessage = { showFeedback(it) }
                                )
                            }

                            // Tools > Groq AI Quote
                            "subcat_tools_ai" -> {
                                QuoteGeneratorCard(
                                    quoteTopicText = quoteTopicText,
                                    onQuoteTopicChange = { quoteTopicText = it },
                                    onGenerateQuote = {
                                        coroutineScope.launch {
                                            // pin=true so the quote also appears on the station screen.
                                            val res = repository.generateQuote(quoteTopicText.trim(), 75, pin = true)
                                            if (res is ApiResult.Success) {
                                                showFeedback(AppStrings.t("Danh ngôn: ") + "\"${res.data.quote}\"")
                                            } else if (res is ApiResult.Error) {
                                                showFeedback(formatControlError(AppStrings.t("Tạo danh ngôn"), res))
                                            }
                                        }
                                    }
                                )
                            }

                            // Tools > Countdown timer (own section)
                            "subcat_tools_timer" -> {
                                CountdownTimerCard(
                                    timerDurationText = pomodoroDurationText,
                                    onTimerDurationChange = { pomodoroDurationText = it },
                                    timerNoteText = pomodoroNoteText,
                                    onTimerNoteChange = { pomodoroNoteText = it },
                                    onStartTimer = {
                                        coroutineScope.launch {
                                            val res = repository.startTimer(
                                                duration = pomodoroDurationText.trim(),
                                                note = pomodoroNoteText.trim()
                                            )
                                            if (res is ApiResult.Success) {
                                                showFeedback(AppStrings.tpl("Đã bắt đầu hẹn giờ đếm ngược $pomodoroDurationText!"))
                                            } else if (res is ApiResult.Error) {
                                                showFeedback(formatControlError(AppStrings.t("Bắt đầu hẹn giờ"), res))
                                            }
                                        }
                                    }
                                )
                            }

                            // Settings > Dark Mode Schedule
                            "subcat_settings_display" -> {
                                DarkModeSettingsCard(
                                    editDarkMode = editDarkMode,
                                    onDarkModeChange = { editDarkMode = it },
                                    editDarkModeStart = editDarkModeStart,
                                    onDarkModeStartChange = { editDarkModeStart = it },
                                    editDarkModeEnd = editDarkModeEnd,
                                    onDarkModeEndChange = { editDarkModeEnd = it },
                                    savedMode = settingsState?.darkmodeMode ?: "off",
                                    savedStart = settingsState?.darkmodeStart ?: "22:00",
                                    savedEnd = settingsState?.darkmodeEnd ?: "06:00",
                                    onSaveSettings = {
                                        val newSettings = StationSettingsDto(
                                            darkmodeMode = editDarkMode,
                                            darkmodeStart = editDarkModeStart,
                                            darkmodeEnd = editDarkModeEnd,
                                            ledBrightness = editLedBrightness,
                                            buzzerEnabled = editBuzzerEnabled,
                                            buzzerDuration = editBuzzerDuration,
                                            weatherAlertEnabled = editWeatherAlertEnabled,
                                            weatherAlertTime = editWeatherAlertTime,
                                            weatherCity = editWeatherCity,
                                            batteryLowThresh = editBatteryLow,
                                            batteryHighThresh = editBatteryHigh,
                                            clockStyle = selectedClockStyle
                                        )
                                        coroutineScope.launch {
                                            val res = repository.updateSettings(newSettings)
                                            if (res is ApiResult.Success) {
                                                showFeedback(AppStrings.t("✓ Đã lưu cấu hình Dark Mode lên trạm!"))
                                            } else if (res is ApiResult.Error) {
                                                showFeedback(formatControlError(AppStrings.t("Lưu cấu hình Dark Mode"), res))
                                            }
                                        }
                                    }
                                )
                            }

                            // Settings > Weather & Battery Protection
                            "subcat_settings_power" -> {
                                PowerSettingsCard(
                                    weatherAlertEnabled = editWeatherAlertEnabled,
                                    onWeatherAlertToggle = { editWeatherAlertEnabled = it },
                                    weatherAlertTime = editWeatherAlertTime,
                                    onWeatherAlertTimeChange = { editWeatherAlertTime = it },
                                    weatherCity = editWeatherCity,
                                    forecastIntervals = weatherForecastIntervals,
                                    isLoadingForecast = isLoadingWeatherForecast,
                                    onRefreshForecast = { refreshWeatherForecast() },
                                    batteryLow = editBatteryLow,
                                    onBatteryLowChange = { editBatteryLow = it },
                                    batteryHigh = editBatteryHigh,
                                    onBatteryHighChange = { editBatteryHigh = it },
                                    onSaveSettings = {
                                        val newSettings = StationSettingsDto(
                                            darkmodeMode = editDarkMode,
                                            weatherAlertEnabled = editWeatherAlertEnabled,
                                            weatherAlertTime = editWeatherAlertTime,
                                            weatherCity = editWeatherCity,
                                            batteryLowThresh = editBatteryLow,
                                            batteryHighThresh = editBatteryHigh,
                                            clockStyle = selectedClockStyle
                                        )
                                        coroutineScope.launch {
                                            val res = repository.updateSettings(newSettings)
                                            if (res is ApiResult.Success) {
                                                showFeedback(AppStrings.t("Đã lưu cấu hình cảnh báo thời tiết & pin!"))
                                            } else if (res is ApiResult.Error) {
                                                showFeedback(formatControlError(AppStrings.t("Lưu cấu hình"), res))
                                            }
                                        }
                                    }
                                )
                            }

                            // System & Telemetry > Live Telemetry
                            "subcat_system_telemetry" -> {
                                TelemetryDetailCard(
                                    systemStatus = systemStatus,
                                    onRefresh = {
                                        coroutineScope.launch {
                                            val res = repository.refreshSystemStatus()
                                            if (res is ApiResult.Success) {
                                                showFeedback(AppStrings.t("Đã cập nhật thông số telemetry!"))
                                            } else if (res is ApiResult.Error) {
                                                showFeedback(formatControlError("Telemetry", res))
                                            }
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                OtaFirmwareCard(
                                    repository = repository,
                                    coroutineScope = coroutineScope,
                                    onFeedback = { showFeedback(it) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }

        }
    }
}

// ================= LEVEL 1: CATEGORIES LIST VIEW =================

@Composable
fun ControlCategoryListView(
    categories: List<ControlCategoryItem>,
    alarmActive: Boolean,
    onCategorySelect: (String) -> Unit,
    onQuickSolveAlarm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // High priority alarm notice banner if ringing
        AnimatedVisibility(visible = alarmActive) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onQuickSolveAlarm() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onError
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.alarm_active_warning),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = AppStrings.t("Chạm để giải toán dập tắt còi ngay!"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

        }

        // Render each Category card
        categories.forEach { category ->
            ElevatedCard(
                onClick = { onCategorySelect(category.id) },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.elevatedCardElevation(
                    defaultElevation = 2.dp,
                    pressedElevation = 8.dp
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = category.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(category.titleRes),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (category.id == "alarms" && alarmActive) {
                                Surface(
                                    color = MaterialTheme.colorScheme.error,
                                    shape = CircleShape
                                ) {
                                    Text(
                                        text = "RINGING",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onError,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = stringResource(category.descRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Text(
                            text = AppStrings.tpl("${category.subcategories.size} mục điều khiển"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ================= LEVEL 2: SUB-CATEGORIES LIST VIEW =================

@Composable
fun ControlSubCategoryListView(
    category: ControlCategoryItem,
    alarmActive: Boolean,
    onSubCategorySelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    notes: List<NoteItemDto> = emptyList(),
    activeNoteId: String? = null,
    isLoadingNotes: Boolean = false,
    onAddNote: ((String, String) -> Unit)? = null,
    onDeleteNote: ((String) -> Unit)? = null,
    onPinNote: ((String) -> Unit)? = null,
    onUnpinNote: (() -> Unit)? = null,
    countdowns: List<CountdownItemDto> = emptyList(),
    activeCountdownId: String? = null,
    isLoadingCountdowns: Boolean = false,
    onAddCountdown: ((String, String) -> Unit)? = null,
    onDeleteCountdown: ((String) -> Unit)? = null,
    onPinCountdown: ((String) -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // Category Summary Header Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Column {
                    Text(
                        text = stringResource(category.titleRes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(category.descRes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

        }

        // Subcategories list
        category.subcategories.forEach { subcat ->
            ElevatedCard(
                onClick = { onSubCategorySelect(subcat.id) },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.elevatedCardElevation(
                    defaultElevation = 2.dp,
                    pressedElevation = 8.dp
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = subcat.icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(subcat.titleRes),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (subcat.id == "subcat_alarm_active" && alarmActive) {
                                Surface(
                                    color = MaterialTheme.colorScheme.error,
                                    shape = CircleShape
                                ) {
                                    Text(
                                        text = "RINGING",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onError,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = stringResource(subcat.descRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

        }

        // Parent Category Tools Manager: Direct access to notes & exam countdowns
        if (category.id == "tools" && onAddNote != null && onDeleteNote != null && onPinNote != null && onUnpinNote != null && onAddCountdown != null && onDeleteCountdown != null && onPinCountdown != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = AppStrings.t("Ghi chú & Đếm ngược"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = { onSubCategorySelect("subcat_tools_notes") }) {
                    Text(AppStrings.t("Toàn màn hình"))
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(imageVector = Icons.Default.OpenInFull, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }


            NotesAndCountdownsManagerCard(
                notes = notes,
                activeNoteId = activeNoteId,
                isLoadingNotes = isLoadingNotes,
                onAddNote = onAddNote,
                onDeleteNote = onDeleteNote,
                onPinNote = onPinNote,
                onUnpinNote = onUnpinNote,
                countdowns = countdowns,
                activeCountdownId = activeCountdownId,
                isLoadingCountdowns = isLoadingCountdowns,
                onAddCountdown = onAddCountdown,
                onDeleteCountdown = onDeleteCountdown,
                onPinCountdown = onPinCountdown
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ================= LEVEL 3: SETTINGS & CONTROLS CARDS =================

@Composable
fun LedControlsCard(
    selectedColorHex: String,
    onColorSelect: (String) -> Unit,
    ledBrightness: Float,
    onLedBrightnessChange: (Float) -> Unit,
    onLedBrightnessFinished: () -> Unit,
    onTurnOnLamp: () -> Unit,
    onTurnOffLamp: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(imageVector = Icons.Default.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = stringResource(R.string.control_lighting_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Color swatches
            Text(
                text = stringResource(R.string.lamp_color_label),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            // 7 màu có sẵn trên ESP32 (khớp preset trong firmware), 4 ô mỗi hàng.
            val presetColors = listOf(
                "#FFFFFF" to Color(0xFFFFFFFF),
                "#FF0000" to Color(0xFFFF0000),
                "#00FF00" to Color(0xFF00FF00),
                "#0000FF" to Color(0xFF0000FF),
                "#FFFF00" to Color(0xFFFFFF00),
                "#FF00FF" to Color(0xFFFF00FF),
                "#00FFFF" to Color(0xFF00FFFF)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                presetColors.take(4).forEach { (hex, col) ->
                    val isSelected = selectedColorHex.equals(hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(CircleShape)
                            .background(col)
                            .clickable { onColorSelect(hex) }
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = if (col == Color(0xFFFFFFFF)) Color.Black else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                presetColors.drop(4).forEach { (hex, col) ->
                    val isSelected = selectedColorHex.equals(hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(CircleShape)
                            .background(col)
                            .clickable { onColorSelect(hex) }
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = if (col == Color(0xFFFFFFFF)) Color.Black else Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Custom Color Palette / Hex Picker (Phase 6)
            var customHexInput by remember(selectedColorHex) { mutableStateOf(selectedColorHex) }
            val parsedColor = remember(customHexInput) {
                try {
                    val clean = customHexInput.trim().removePrefix("#")
                    if (clean.length == 6) Color(android.graphics.Color.parseColor("#$clean")) else null
                } catch (e: Exception) {
                    null
                }
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(10.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(parsedColor ?: MaterialTheme.colorScheme.outline)
                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    )
                    OutlinedTextField(
                        value = customHexInput,
                        onValueChange = { customHexInput = it },
                        label = { Text(AppStrings.t("Mã màu HEX (#RRGGBB)")) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            if (parsedColor != null) {
                                val hex = if (customHexInput.startsWith("#")) customHexInput else "#$customHexInput"
                                onColorSelect(hex)
                            }
                        },
                        enabled = parsedColor != null,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(AppStrings.t("Áp dụng"))
                    }
                }
            }

            // Desk Lamp LED Brightness Slider
            Text(
                text = stringResource(R.string.led_bright_label, ledBrightness.roundToInt()),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            Slider(
                value = ledBrightness,
                onValueChange = onLedBrightnessChange,
                onValueChangeFinished = onLedBrightnessFinished,
                valueRange = 0f..100f
            )

            // Quick lamp power: explicit ON / OFF
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onTurnOnLamp,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.lamp_on_btn))
                }

                FilledTonalButton(
                    onClick = onTurnOffLamp,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.lamp_off_btn))
                }
            }

        }
    }
}

private data class OledPreviewQuadruple(
    val badge: String,
    val title: String,
    val main: String,
    val sub: String
)

@Composable
fun OledSimulatorView(viewKey: String) {
    val meta = when (viewKey) {
        "main" -> OledPreviewQuadruple(
            "MAIN DASHBOARD",
            "DESKFLOW STATION (DEFAULT)",
            "12:45 • Ha Long 28°C",
            "♫ Spotify Playing • Quotes & Notes"
        )
        "stats" -> OledPreviewQuadruple(
            "SERVER TELEMETRY",
            "HOST & ESP32 STATS",
            "CPU 24% • RAM 58%",
            "UPTIME: 4d 12h • WIFI OK"
        )
        "tkb" -> OledPreviewQuadruple(
            "TIMETABLE",
            AppStrings.t("TKB — SÁNG THỨ 2"),
            AppStrings.t("Toán • Vật Lý • Hóa Học"),
            AppStrings.t("Anh Văn • Sinh Học")
        )
        "weather" -> OledPreviewQuadruple(
            "WEATHER FORECAST",
            AppStrings.t("THỜI TIẾT HẠ LONG"),
            AppStrings.t("28°C  NẮNG ĐẸP"),
            AppStrings.t("POP: 20% • ĐỘ ẨM: 65%")
        )
        "lyrics" -> OledPreviewQuadruple(
            "SPOTIFY LYRICS",
            "♫ NOW PLAYING",
            "Starboy — The Weeknd",
            "\"I'm tryna put you in the worst mood...\""
        )
        "clock" -> OledPreviewQuadruple(
            "DIGITAL CLOCK",
            "DESKFLOW WATCHFACE",
            "12:30 :45",
            "CN 08 TH11 • 28°C HCM"
        )
        "notes" -> OledPreviewQuadruple(
            "STICKY NOTE",
            "★ PINNED NOTE",
            "Họp nhóm KHKT 14:00",
            "Review DeskFlow firmware build"
        )
        "countdown" -> OledPreviewQuadruple(
            "TARGET COUNTDOWN",
            "KỲ THI KHKT 2026",
            AppStrings.t("CÒN 45 NGÀY"),
            AppStrings.t("MỤC TIÊU: 15/01/2026")
        )
        "focus" -> OledPreviewQuadruple(
            "FOCUS SESSION",
            AppStrings.t("CHẾ ĐỘ TẬP TRUNG"),
            "24:58",
            "[████████░░░░] CÒN 72%"
        )
        else -> OledPreviewQuadruple(
            "OLED DISPLAY",
            "DESKFLOW ESP32",
            "READY",
            "320×240 TFT"
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f),   // 320x240 TFT, same aspect as the real panel
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF000000)),
        border = BorderStroke(1.5.dp, Color(0xFF334155))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header row with dot, monitor tag, and view badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                    )
                    Text(
                        text = "320×240 TFT (ST7789)",
                        color = Color(0xFF94A3B8),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF0F172A)
                ) {
                    Text(
                        text = meta.badge,
                        color = Color(0xFF38BDF8),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Screen content
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = meta.title,
                    color = Color(0xFF94A3B8),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                Text(
                    text = meta.main,
                    color = Color(0xFFFFFFFF),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                Text(
                    text = meta.sub,
                    color = Color(0xFF38BDF8),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }

            // Bottom subtle bar
            Text(
                text = "● LIVE STATION DISPLAY EMULATOR",
                color = Color(0xFF64748B),
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun DisplayControlsCard(
    currentView: String,
    onSwitchView: (String, String) -> Unit,
    onCloseTkb: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(imageVector = Icons.Default.Screenshot, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = stringResource(R.string.control_views_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Live OLED Display Simulator
            Text(
                text = AppStrings.t("Ví dụ hiển thị màn hình OLED (Live Preview)"),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OledSimulatorView(viewKey = currentView)

            Spacer(modifier = Modifier.height(2.dp))

            val views = listOf(
                "main" to stringResource(R.string.view_main),
                "stats" to stringResource(R.string.view_stats),
                "tkb" to stringResource(R.string.view_tkb),
                "calendar" to stringResource(R.string.view_calendar),
                "weather" to stringResource(R.string.view_weather),
                "lyrics" to stringResource(R.string.view_lyrics),
                "clock" to stringResource(R.string.view_clock),
                "notes" to stringResource(R.string.view_notes),
                "countdown" to stringResource(R.string.view_countdown),
                "focus" to stringResource(R.string.view_focus)
            )

            views.chunked(3).forEach { rowViews ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    rowViews.forEach { (vKey, vLabel) ->
                        val isSelected = currentView == vKey
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSwitchView(vKey, vLabel) },
                            label = { Text(vLabel) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(3 - rowViews.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }


            OutlinedButton(
                onClick = onCloseTkb,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Default.Close, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.control_close_tkb_btn))
            }
        }
    }
}

data class ClockStyleMeta(
    val id: String,
    val name: String,
    val desc: String
)

fun clockStylesList(): List<ClockStyleMeta> = listOf(
    ClockStyleMeta("digital_classic", AppStrings.t("Số cổ điển"), "Chữ số lớn 24h/12h quen thuộc"),
    ClockStyleMeta("digital_minimal", AppStrings.t("Tối giản"), AppStrings.t("Thanh lịch, không viền rườm rà")),
    ClockStyleMeta("digital_neon", AppStrings.t("Đèn Neon"), AppStrings.t("Khung sáng viền kép công nghệ cao")),
    ClockStyleMeta("analog", AppStrings.t("Đồng hồ kim"), AppStrings.t("Mặt tròn 3 kim chuyển động mượt")),
    ClockStyleMeta("flip", AppStrings.t("Đồng hồ lật"), AppStrings.t("Thẻ lật 2D chia nửa trên dưới")),
    ClockStyleMeta("binary", AppStrings.t("Đồng hồ nhị phân"), AppStrings.t("Ma trận LED mã BCD 8-4-2-1")),
    ClockStyleMeta("word", AppStrings.t("Đồng hồ chữ"), "Hiển thị giờ bằng chữ viết")
)

@Composable
fun ClockStyleStudioCard(
    clockConfig: ClockConfigDto,
    isLoading: Boolean,
    isSaving: Boolean,
    onUpdateConfig: (ClockConfigDto) -> Unit
) {
    var previewStyle by remember(clockConfig.style) { mutableStateOf(clockConfig.style) }
    var editTimeFormat by remember(clockConfig.timeFormat) { mutableStateOf(clockConfig.timeFormat) }
    var editShowSeconds by remember(clockConfig.showSeconds) { mutableStateOf(clockConfig.showSeconds) }
    var editShowDate by remember(clockConfig.showDate) { mutableStateOf(clockConfig.showDate) }
    var editShowAmPm by remember(clockConfig.showAmPm) { mutableStateOf(clockConfig.showAmPm) }
    var editLanguage by remember(clockConfig.language) { mutableStateOf(clockConfig.language) }
    var editAnimationEnabled by remember(clockConfig.animationEnabled) { mutableStateOf(clockConfig.animationEnabled) }
    var editAnimationSpeed by remember(clockConfig.animationSpeed) { mutableFloatStateOf(clockConfig.animationSpeed) }
    var editAccentColor by remember(clockConfig.accentColor) { mutableStateOf(clockConfig.accentColor) }

    val accentColorParsed = remember(editAccentColor) {
        try {
            Color(android.graphics.Color.parseColor(editAccentColor))
        } catch (e: Exception) {
            Color(0xFF38BDF8)
        }
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = "Clock Style Studio",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = AppStrings.t("Mặt đồng hồ & Hoạt ảnh thời gian thực"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }


            // Sync Status Banner (Requirement #6: Distinct states)
            val badgeBg = when {
                isLoading || isSaving -> Color(0xFF332A1C)
                clockConfig.appliedByStation -> Color(0xFF1B3825)
                else -> Color(0xFF382E18)
            }
            val badgeBorder = when {
                isLoading || isSaving -> Color(0xFFFAC772)
                clockConfig.appliedByStation -> Color(0xFF38B26C)
                else -> Color(0xFFF59E0B)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(badgeBg)
                    .border(1.dp, badgeBorder.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isLoading || isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else if (clockConfig.appliedByStation) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF38B26C),
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        val statusText = when {
                            isLoading -> AppStrings.t("Đang đồng bộ cấu hình từ máy chủ...")
                            isSaving -> AppStrings.t("Đang gửi cấu hình lên máy chủ & trạm...")
                            clockConfig.appliedByStation -> AppStrings.tpl("Đã đồng bộ & Áp dụng thành công trên trạm ESP32 (v${clockConfig.configVersion})")
                            else -> AppStrings.tpl("Đã lưu máy chủ — Chờ trạm ESP32 nhận (v${clockConfig.configVersion})")
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (clockConfig.appliedByStation) Color(0xFFE2FBE8) else Color(0xFFFDF6E2)
                        )
                        if (previewStyle != clockConfig.lastAppliedStyle) {
                            Text(
                                text = AppStrings.tpl("Xem trước: $previewStyle | Đang chạy trên trạm: ${clockConfig.lastAppliedStyle}"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }


            // Style Carousel / Selector
            Text(
                text = AppStrings.t("Chọn kiểu mặt đồng hồ (7 Kiểu)"),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(clockStylesList()) { meta ->
                    val isSelected = previewStyle == meta.id
                    val isLiveOnStation = clockConfig.lastAppliedStyle == meta.id

                    Card(
                        modifier = Modifier
                            .width(136.dp)
                            .clickable { previewStyle = meta.id },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                        ),
                        border = if (isSelected) {
                            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Mini Graphic Preview Box
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0B0F19)),
                                contentAlignment = Alignment.Center
                            ) {
                                when (meta.id) {
                                    "analog" -> {
                                        Canvas(modifier = Modifier.size(48.dp)) {
                                            val c = center
                                            val r = size.minDimension / 2 - 2
                                            drawCircle(color = Color(0xFF23334D), radius = r, style = Stroke(1.5f))
                                            // 12 ticks
                                            for (i in 0 until 12) {
                                                val a = Math.toRadians((i * 30).toDouble())
                                                val x1 = c.x + (Math.cos(a) * (r - 4)).toFloat()
                                                val y1 = c.y + (Math.sin(a) * (r - 4)).toFloat()
                                                val x2 = c.x + (Math.cos(a) * r).toFloat()
                                                val y2 = c.y + (Math.sin(a) * r).toFloat()
                                                drawLine(color = Color(0xFF94A3B8), start = Offset(x1, y1), end = Offset(x2, y2), strokeWidth = 1f)
                                            }
                                            // Hands
                                            drawLine(color = Color.White, start = c, end = Offset(c.x + r * 0.45f, c.y - r * 0.25f), strokeWidth = 2f, cap = StrokeCap.Round)
                                            drawLine(color = accentColorParsed, start = c, end = Offset(c.x - r * 0.2f, c.y + r * 0.65f), strokeWidth = 1.5f, cap = StrokeCap.Round)
                                            drawLine(color = Color(0xFFF59E0B), start = c, end = Offset(c.x - r * 0.7f, c.y - r * 0.4f), strokeWidth = 1f)
                                            drawCircle(color = Color.White, radius = 2f)
                                        }
                                    }
                                    "flip" -> {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp, 32.dp)
                                                    .background(Color(0xFF151F32), RoundedCornerShape(3.dp))
                                                    .border(0.8.dp, Color(0xFF23334D), RoundedCornerShape(3.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("12", color = Color.White, fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp), fontWeight = FontWeight.Bold)
                                            }
                                            Text(":", color = Color(0xFFF59E0B), fontSize = androidx.compose.ui.unit.TextUnit(12f, androidx.compose.ui.unit.TextUnitType.Sp), fontWeight = FontWeight.Bold)
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp, 32.dp)
                                                    .background(Color(0xFF151F32), RoundedCornerShape(3.dp))
                                                    .border(0.8.dp, Color(0xFF23334D), RoundedCornerShape(3.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text("30", color = Color.White, fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp), fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                    "digital_neon" -> {
                                        Box(
                                            modifier = Modifier
                                                .padding(4.dp)
                                                .border(1.2.dp, accentColorParsed, RoundedCornerShape(6.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("12:30", color = accentColorParsed, fontWeight = FontWeight.ExtraBold)
                                        }
                                    }
                                    "binary" -> {
                                        Canvas(modifier = Modifier.size(52.dp, 36.dp)) {
                                            for (c in 0 until 4) {
                                                for (row in 0 until 4) {
                                                    val active = (c + row) % 2 == 1
                                                    val cx = 6f + c * 13f
                                                    val cy = 5f + row * 8f
                                                    if (active) {
                                                        drawCircle(color = accentColorParsed, radius = 2.5f, center = Offset(cx, cy))
                                                    } else {
                                                        drawCircle(color = Color(0xFF23334D), radius = 2.5f, center = Offset(cx, cy), style = Stroke(0.8f))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    "word" -> {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("MUOI HAI", color = Color.White, fontSize = androidx.compose.ui.unit.TextUnit(8f, androidx.compose.ui.unit.TextUnitType.Sp), fontWeight = FontWeight.Bold)
                                            Text("BA MUOI", color = accentColorParsed, fontSize = androidx.compose.ui.unit.TextUnit(8f, androidx.compose.ui.unit.TextUnitType.Sp), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    "digital_minimal" -> {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("12:30:45", color = Color.White, fontWeight = FontWeight.Medium, fontSize = androidx.compose.ui.unit.TextUnit(11f, androidx.compose.ui.unit.TextUnitType.Sp))
                                            Text("Thu 3, 03/11", color = Color(0xFF94A3B8), fontSize = androidx.compose.ui.unit.TextUnit(7f, androidx.compose.ui.unit.TextUnitType.Sp))
                                        }
                                    }
                                    else -> {
                                        // digital_classic
                                        Row(verticalAlignment = Alignment.Bottom) {
                                            Text("12:30", color = accentColorParsed, fontWeight = FontWeight.Bold, fontSize = androidx.compose.ui.unit.TextUnit(14f, androidx.compose.ui.unit.TextUnitType.Sp))
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text(":45", color = Color(0xFF94A3B8), fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp))
                                        }
                                    }
                                }
                            }

                            // Meta Text
                            Text(
                                text = meta.name,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )

                            if (isLiveOnStation) {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF1B3825), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text(AppStrings.t("Đang chạy"), style = MaterialTheme.typography.labelSmall, color = Color(0xFF38B26C), fontSize = androidx.compose.ui.unit.TextUnit(9f, androidx.compose.ui.unit.TextUnitType.Sp))
                                }
                            }
                        }
                    }
                }
            }


            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Configuration Controls
            Text(
                text = AppStrings.t("Tùy biến hiển thị & Ngôn ngữ"),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )

            // Format & Language Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = editTimeFormat == "24h",
                    onClick = { editTimeFormat = "24h" },
                    label = { Text("24 Giờ") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = editTimeFormat == "12h",
                    onClick = { editTimeFormat = "12h" },
                    label = { Text("12 Giờ (AM/PM)") },
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = editLanguage == "vi",
                    onClick = { editLanguage = "vi" },
                    label = { Text("Tiếng Việt (VI)") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = editLanguage == "en",
                    onClick = { editLanguage = "en" },
                    label = { Text("English (EN)") },
                    modifier = Modifier.weight(1f)
                )
            }

            // Toggles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(AppStrings.t("Hiển thị giây"), style = MaterialTheme.typography.bodySmall)
                Switch(
                    checked = editShowSeconds,
                    onCheckedChange = { editShowSeconds = it }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(AppStrings.t("Hiển thị ngày tháng"), style = MaterialTheme.typography.bodySmall)
                Switch(
                    checked = editShowDate,
                    onCheckedChange = { editShowDate = it }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(AppStrings.t("Bật hoạt ảnh (Animations)"), style = MaterialTheme.typography.bodySmall)
                Switch(
                    checked = editAnimationEnabled,
                    onCheckedChange = { editAnimationEnabled = it }
                )
            }

            if (editAnimationEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(AppStrings.t("Tốc độ hoạt ảnh"), style = MaterialTheme.typography.bodySmall)
                        Text("${String.format("%.1f", editAnimationSpeed)}x", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(
                        value = editAnimationSpeed,
                        onValueChange = { editAnimationSpeed = (it * 10).roundToInt() / 10f },
                        valueRange = 0.5f..2.0f,
                        steps = 14
                    )
                }
            }


            // Accent Color Palette
            Text(AppStrings.t("Màu điểm nhấn (Accent Color)"), style = MaterialTheme.typography.bodySmall)
            val palette = listOf("#38BDF8", "#F59E0B", "#A855F7", "#10B981", "#EF4444", "#D4AF37")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                palette.forEach { hex ->
                    val colorObj = Color(android.graphics.Color.parseColor(hex))
                    val isColorSelected = editAccentColor.equals(hex, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(colorObj)
                            .clickable { editAccentColor = hex }
                            .then(
                                if (isColorSelected) Modifier.border(2.dp, Color.White, CircleShape) else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isColorSelected) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }


            // Save & Apply Button
            Button(
                onClick = {
                    val newCfg = clockConfig.copy(
                        style = previewStyle,
                        timeFormat = editTimeFormat,
                        showSeconds = editShowSeconds,
                        showDate = editShowDate,
                        showAmPm = editShowAmPm,
                        language = editLanguage,
                        animationEnabled = editAnimationEnabled,
                        animationSpeed = editAnimationSpeed,
                        accentColor = editAccentColor
                    )
                    onUpdateConfig(newCfg)
                },
                enabled = !isSaving && !isLoading,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(AppStrings.t("Đang đồng bộ lên trạm..."))
                } else {
                    Icon(imageVector = Icons.Default.Done, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(AppStrings.t("Áp dụng lên trạm DeskFlow"))
                }
            }

        }
    }
}

@Composable
fun BuzzerControlsCard(
    buzzerEnabled: Boolean,
    onBuzzerEnabledChange: (Boolean) -> Unit,
    buzzerDuration: Int,
    onBuzzerDurationChange: (Int) -> Unit,
    savedDuration: Int,
    onSaveSettings: () -> Unit,
    onTriggerBuzz: (Int) -> Unit,
    onEmergencyStop: () -> Unit,
    onOpenMorseConverter: (() -> Unit)? = null
) {
    var customDurText by rememberSaveable(buzzerDuration) { mutableStateOf(buzzerDuration.toString()) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }

    fun validateAndSet(input: String): Boolean {
        customDurText = input
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            errorMessage = AppStrings.t("Thời lượng còi không được để trống")
            return false
        }
        val num = trimmed.toIntOrNull()
        if (num == null) {
            errorMessage = AppStrings.t("Vui lòng nhập số nguyên hợp lệ")
            return false
        }
        if (num < 10 || num > 1000) {
            errorMessage = AppStrings.tpl("Thời lượng còi phải từ 10ms đến 1000ms (nhập: $num)")
            return false
        }
        errorMessage = null
        onBuzzerDurationChange(num)
        return true
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(imageVector = Icons.Default.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = stringResource(R.string.subcat_buzzer),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = AppStrings.t("Cấu hình âm thanh còi chip vật lý (GPIO 32) trên trạm ESP32. Âm báo thức khẩn cấp luôn kêu ngay cả khi tắt tiếng phản hồi."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Switch feedback
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tiếng bíp khi bấm nút (Feedback)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (buzzerEnabled) AppStrings.t("Đang bật âm phản hồi") else AppStrings.t("Đang tắt tiếng (Mute)"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = buzzerEnabled,
                    onCheckedChange = onBuzzerEnabledChange
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Custom Duration Input
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = AppStrings.t("Thời lượng tiếng bíp (mili-giây):"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = AppStrings.tpl("Đã lưu: ${savedDuration}ms"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }

                OutlinedTextField(
                    value = customDurText,
                    onValueChange = { validateAndSet(it) },
                    label = { Text(AppStrings.t("Độ dài bíp (10 - 1000 ms)")) },
                    placeholder = { Text("Ví dụ: 40, 100, 175, 300") },
                    singleLine = true,
                    isError = errorMessage != null,
                    supportingText = {
                        if (errorMessage != null) {
                            Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                        } else {
                            Text(AppStrings.t("Giới hạn an toàn phần cứng: 10ms đến 1000ms"))
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = AppStrings.t("Gợi ý nhanh:"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(20, 40, 80, 175, 300, 500).forEach { dur ->
                        FilterChip(
                            selected = buzzerDuration == dur,
                            onClick = { validateAndSet(dur.toString()) },
                            label = { Text("${dur}ms") }
                        )
                    }
                }
            }


            // Save Buzzer Settings Button
            Button(
                onClick = {
                    if (validateAndSet(customDurText)) {
                        onSaveSettings()
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
                enabled = errorMessage == null
            ) {
                Icon(imageVector = Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(AppStrings.t("Lưu cài đặt còi"))
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Action: Test Buzz
            FilledTonalButton(
                onClick = {
                    val d = customDurText.toIntOrNull() ?: buzzerDuration
                    onTriggerBuzz(d)
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Default.NotificationsActive, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(AppStrings.tpl("Phát thử tiếng bíp (${buzzerDuration}ms)"))
            }

            // Action: Emergency Stop
            OutlinedButton(
                onClick = onEmergencyStop,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Default.Warning, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.alarm_emergency_stop))
            }

            if (onOpenMorseConverter != null) {
                OutlinedButton(
                    onClick = onOpenMorseConverter,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.GraphicEq, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(AppStrings.t("Mở bộ mã Morse (Chuyển văn bản & Phát còi)"))
                }
            }

        }
    }
}

@Composable
fun MidiJukeboxCard(
    repository: FocusRepository,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentSongTitle by remember { mutableStateOf<String?>(null) }
    var selectedPreset by remember { mutableStateOf("mario") }

    val midiFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    var displayName = "song.mid"
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1 && cursor.moveToFirst()) {
                            displayName = cursor.getString(nameIndex) ?: displayName
                        }
                    }
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes == null || bytes.isEmpty()) {
                        onShowMessage(AppStrings.t("Không thể đọc tệp MIDI đã chọn"))
                        return@launch
                    }
                    val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                    onShowMessage(AppStrings.tpl("Đang gửi và phát '$displayName'..."))
                    val res = repository.playMidiFile(base64 = base64, filename = displayName)
                    if (res is ApiResult.Success) {
                        isPlaying = true
                        currentSongTitle = displayName
                        onShowMessage(AppStrings.tpl("✓ Đang phát: $displayName"))
                    } else if (res is ApiResult.Error) {
                        onShowMessage(AppStrings.tpl("Lỗi phát MIDI: ${res.message}"))
                    }
                } catch (e: Exception) {
                    onShowMessage(AppStrings.tpl("Lỗi xử lý file: ${e.message}"))
                }
            }
        }
    }

    val presets = listOf(
        "mario" to "Super Mario",
        "rickroll" to "Rickroll",
        "tetris" to "Tetris Theme",
        "coffin" to "Coffin Dance",
        "starwars" to "Imperial March",
        "nokia" to "Nokia Tune"
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Column {
                    Text(
                        text = AppStrings.t("Trình phát nhạc MIDI (Buzzer Jukebox)"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = AppStrings.t("Phát các giai điệu 8-bit hoặc file .mid qua còi ESP32"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (currentSongTitle != null && isPlaying) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Audiotrack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = AppStrings.tpl("Đang phát: $currentSongTitle"),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Text(
                text = AppStrings.t("Bài mẫu 8-bit có sẵn:"),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(presets) { (id, label) ->
                    FilterChip(
                        selected = (selectedPreset == id),
                        onClick = {
                            selectedPreset = id
                            coroutineScope.launch {
                                onShowMessage(AppStrings.tpl("Đang phát $label..."))
                                val res = repository.playMelodyPreset(id)
                                if (res is ApiResult.Success) {
                                    isPlaying = true
                                    currentSongTitle = label
                                    onShowMessage(AppStrings.tpl("✓ Đang phát: $label"))
                                } else if (res is ApiResult.Error) {
                                    onShowMessage(AppStrings.tpl("Lỗi phát bài: ${res.message}"))
                                }
                            }
                        },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = if (selectedPreset == id && isPlaying) {
                            { Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        midiFilePicker.launch("*/*")
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Chọn file .mid", maxLines = 1)
                }

                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            val res = repository.stopMelody()
                            if (res is ApiResult.Success) {
                                isPlaying = false
                                currentSongTitle = null
                                onShowMessage(AppStrings.t("Đã dừng phát nhạc trên ESP32."))
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(imageVector = Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Dừng")
                }
            }
        }
    }
}

@Composable
fun ActiveAlarmCard(
    alarmState: com.deskflow.app.data.model.AlarmActiveDto,
    answerText: String,
    onAnswerChange: (String) -> Unit,
    isSubmittingAlarm: Boolean,
    onSolveAlarm: () -> Unit,
    onEmergencyStop: () -> Unit,
    focusManager: androidx.compose.ui.focus.FocusManager
) {
    if (alarmState.active) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Alarm,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.alarm_active_warning),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = alarmState.question ?: AppStrings.t("Giải toán để tắt còi"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                OutlinedTextField(
                    value = answerText,
                    onValueChange = onAnswerChange,
                    label = { Text(stringResource(R.string.alarm_answer_placeholder)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onSolveAlarm,
                        enabled = !isSubmittingAlarm && answerText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isSubmittingAlarm) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.alarm_solve_btn))
                        }
                    }

                    FilledTonalButton(
                        onClick = onEmergencyStop,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(stringResource(R.string.alarm_emergency_stop))
                    }
                }
            }

        }
    } else {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.alarm_no_active),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = AppStrings.t("Không có báo thức đang kêu trên thiết bị. Bạn có thể hẹn giờ báo thức mới ở mục 'Hẹn giờ báo thức'."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ScheduleAlarmCard(
    scheduleTime: String,
    onScheduleTimeChange: (String) -> Unit,
    isScheduling: Boolean,
    onScheduleClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(imageVector = Icons.Default.AccessTime, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = stringResource(R.string.control_alarm_schedule_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = scheduleTime,
                    onValueChange = onScheduleTimeChange,
                    label = { Text(stringResource(R.string.control_alarm_time_label)) },
                    placeholder = { Text("06:30") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )

                Button(
                    onClick = onScheduleClick,
                    enabled = !isScheduling,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.control_alarm_schedule_btn))
                }
            }


            // Quick preset time chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("06:00", "06:30", "07:00", "22:00").forEach { preset ->
                    FilterChip(
                        selected = scheduleTime == preset,
                        onClick = { onScheduleTimeChange(preset) },
                        label = { Text(preset) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

        }
    }
}

@Composable
fun NotesAndCountdownsManagerCard(
    notes: List<NoteItemDto>,
    activeNoteId: String?,
    isLoadingNotes: Boolean,
    deletingNoteId: String? = null,
    onAddNote: (text: String, color: String) -> Unit,
    onDeleteNote: (id: String) -> Unit,
    onPinNote: (id: String) -> Unit,
    onUnpinNote: () -> Unit,
    countdowns: List<CountdownItemDto>,
    activeCountdownId: String?,
    isLoadingCountdowns: Boolean,
    deletingCountdownId: String? = null,
    onAddCountdown: (name: String, targetDate: String) -> Unit,
    onDeleteCountdown: (id: String) -> Unit,
    onPinCountdown: (id: String) -> Unit
) {
    var showAddNoteDialog by remember { mutableStateOf(false) }
    var newNoteText by remember { mutableStateOf("") }
    var newNoteColor by remember { mutableStateOf("#FDE047") }
    var noteToConfirmDelete by remember { mutableStateOf<NoteItemDto?>(null) }

    var showAddCountdownDialog by remember { mutableStateOf(false) }
    var newCdName by remember { mutableStateOf("") }
    var newCdDate by remember { mutableStateOf("") }
    var countdownToConfirmDelete by remember { mutableStateOf<CountdownItemDto?>(null) }

    val noteColors = listOf(
        "#FDE047" to Color(0xFFFDE047),
        "#86EFAC" to Color(0xFF86EFAC),
        "#93C5FD" to Color(0xFF93C5FD),
        "#FCA5A5" to Color(0xFFFCA5A5),
        "#C4B5FD" to Color(0xFFC4B5FD)
    )

    if (noteToConfirmDelete != null) {
        val targetNote = noteToConfirmDelete!!
        AlertDialog(
            onDismissRequest = { noteToConfirmDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.note_delete_confirm_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.note_delete_confirm_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "\"${targetNote.displayText}\"",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
,
            confirmButton = {
                Button(
                    onClick = {
                        val idToDelete = targetNote.id
                        noteToConfirmDelete = null
                        onDeleteNote(idToDelete)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.btn_delete))
                }
            }
,
            dismissButton = {
                TextButton(onClick = { noteToConfirmDelete = null }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }

        )
    }

    if (countdownToConfirmDelete != null) {
        val targetCd = countdownToConfirmDelete!!
        AlertDialog(
            onDismissRequest = { countdownToConfirmDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = AppStrings.t("Xóa đếm ngược này?"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = AppStrings.tpl("Sự kiện \"${targetCd.displayName}\" (${targetCd.targetDate}) sẽ bị xóa khỏi máy chủ và trạm DeskFlow."),
                    style = MaterialTheme.typography.bodySmall
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val idToDelete = targetCd.id
                        countdownToConfirmDelete = null
                        onDeleteCountdown(idToDelete)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.btn_delete))
                }
            }
,
            dismissButton = {
                TextButton(onClick = { countdownToConfirmDelete = null }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }

        )
    }

    if (showAddNoteDialog) {
        AlertDialog(
            onDismissRequest = { showAddNoteDialog = false },
            title = { Text(AppStrings.t("Thêm ghi chú mới"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newNoteText,
                        onValueChange = { newNoteText = it },
                        placeholder = { Text("Nhập nội dung ghi chú...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Text(AppStrings.t("Màu sắc thẻ ghi chú:"), style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        noteColors.forEach { (hex, col) ->
                            val isSelected = newNoteColor == hex
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(col)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f),
                                        shape = CircleShape
                                    )
                                    .clickable { newNoteColor = hex },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
,
            confirmButton = {
                Button(
                    onClick = {
                        if (newNoteText.isNotBlank()) {
                            onAddNote(newNoteText.trim(), newNoteColor)
                            newNoteText = ""
                            showAddNoteDialog = false
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(AppStrings.t("Lưu ghi chú"))
                }
            }
,
            dismissButton = {
                TextButton(onClick = { showAddNoteDialog = false }) {
                    Text("Hủy")
                }
            }

        )
    }

    if (showAddCountdownDialog) {
        AlertDialog(
            onDismissRequest = { showAddCountdownDialog = false },
            title = { Text(AppStrings.t("Thêm đếm ngược mới"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newCdName,
                        onValueChange = { newCdName = it },
                        label = { Text(AppStrings.t("Tên sự kiện / Kỳ thi")) },
                        placeholder = { Text(AppStrings.t("VD: Thi Giữa Kỳ, Bảo Vệ Đồ Án")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = newCdDate,
                        onValueChange = { newCdDate = it },
                        label = { Text(AppStrings.t("Ngày đích")) },
                        placeholder = { Text("VD: 2026-12-31 hoặc 31/12/2026 hoặc 15/01") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Text(
                        text = AppStrings.t("Hỗ trợ định dạng: YYYY-MM-DD, DD/MM/YYYY, hoặc DD/MM (múi giờ GMT+7)"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
,
            confirmButton = {
                Button(
                    onClick = {
                        if (newCdName.isNotBlank() && newCdDate.isNotBlank()) {
                            onAddCountdown(newCdName.trim(), newCdDate.trim())
                            newCdName = ""
                            newCdDate = ""
                            showAddCountdownDialog = false
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(AppStrings.t("Tạo đếm ngược"))
                }
            }
,
            dismissButton = {
                TextButton(onClick = { showAddCountdownDialog = false }) {
                    Text("Hủy")
                }
            }

        )
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ================= 1. GHI CHÚ (NOTES) =================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.NoteAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "Ghi chú & Giấy nhớ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                FilledTonalButton(
                    onClick = { showAddNoteDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(AppStrings.t("Thêm"), style = MaterialTheme.typography.labelMedium)
                }
            }


            if (isLoadingNotes) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else if (notes.isEmpty()) {
                Text(
                    text = AppStrings.t("Chưa có ghi chú nào. Bấm 'Thêm' để tạo ghi chú mới và ghim lên màn hình trạm DeskFlow!"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    notes.forEach { note ->
                        val isPinned = activeNoteId == note.id
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = note.displayText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    val isDeletingThisNote = deletingNoteId == note.id
                                    IconButton(
                                        onClick = { noteToConfirmDelete = note },
                                        enabled = !isDeletingThisNote,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        if (isDeletingThisNote) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        } else {
                                            Icon(Icons.Default.Delete, contentDescription = "Xóa", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isPinned) {
                                        SuggestionChip(
                                            onClick = onUnpinNote,
                                            label = { Text(AppStrings.t("Đang ghim trên trạm (Bỏ ghim)")) },
                                            icon = { Icon(Icons.Default.PushPin, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp)) },
                                            colors = SuggestionChipDefaults.suggestionChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                                        )
                                    } else {
                                        OutlinedButton(
                                            onClick = { onPinNote(note.id) },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(AppStrings.t("Ghim lên trạm"), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    if (note.createdAt != null) {
                                        Text(
                                            text = note.createdAt.substringBefore("T"),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }


            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // ================= 2. ĐẾM NGƯỢC (COUNTDOWNS) =================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.HourglassTop, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = AppStrings.t("Đếm ngược sự kiện"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                FilledTonalButton(
                    onClick = { showAddCountdownDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(AppStrings.t("Thêm"), style = MaterialTheme.typography.labelMedium)
                }
            }


            if (isLoadingCountdowns) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else if (countdowns.isEmpty()) {
                Text(
                    text = AppStrings.t("Chưa có đếm ngược nào. Bấm 'Thêm' để tạo kỳ thi hoặc sự kiện sắp tới!"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    countdowns.forEach { cd ->
                        val isPinned = activeCountdownId == cd.id
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = cd.displayName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = AppStrings.tpl("Ngày: ${cd.targetDate}"),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (cd.isExpired) {
                                        SuggestionChip(
                                            onClick = {},
                                            label = { Text(AppStrings.t("Đã hết hạn"), color = MaterialTheme.colorScheme.error) }
                                        )
                                    } else {
                                        SuggestionChip(
                                            onClick = {},
                                            label = { Text(AppStrings.tpl("Còn ${cd.daysLeft} ngày"), fontWeight = FontWeight.Bold) }
                                        )
                                    }
                                    val isDeletingThisCd = deletingCountdownId == cd.id
                                    IconButton(
                                        onClick = { countdownToConfirmDelete = cd },
                                        enabled = !isDeletingThisCd,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        if (isDeletingThisCd) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        } else {
                                            Icon(Icons.Default.Delete, contentDescription = "Xóa", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    if (isPinned) {
                                        SuggestionChip(
                                            onClick = {},
                                            label = { Text(AppStrings.t("Đang hiện trên trạm")) },
                                            icon = { Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp)) },
                                            colors = SuggestionChipDefaults.suggestionChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                                        )
                                    } else {
                                        OutlinedButton(
                                            onClick = { onPinCountdown(cd.id) },
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Default.Tv, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(AppStrings.t("Hiện trên trạm"), style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

        }
    }
}

@Composable
fun QuoteGeneratorCard(
    quoteTopicText: String,
    onQuoteTopicChange: (String) -> Unit,
    onGenerateQuote: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(imageVector = Icons.Default.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = stringResource(R.string.quote_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = quoteTopicText,
                    onValueChange = onQuoteTopicChange,
                    label = { Text(stringResource(R.string.quote_topic_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = onGenerateQuote, shape = RoundedCornerShape(12.dp)) {
                    Text(stringResource(R.string.btn_generate_quote))
                }
            }
        }
    }
}

@Composable
fun CountdownTimerCard(
    timerDurationText: String,
    onTimerDurationChange: (String) -> Unit,
    timerNoteText: String,
    onTimerNoteChange: (String) -> Unit,
    onStartTimer: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(imageVector = Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = stringResource(R.string.timer_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = timerDurationText,
                    onValueChange = onTimerDurationChange,
                    label = { Text(stringResource(R.string.timer_duration_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = timerNoteText,
                    onValueChange = onTimerNoteChange,
                    label = { Text(stringResource(R.string.timer_note_label)) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1.5f)
                )
            }
            Button(
                onClick = onStartTimer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.btn_start_timer))
            }
        }
    }
}

@Composable
fun DarkModeSettingsCard(
    editDarkMode: String,
    onDarkModeChange: (String) -> Unit,
    editDarkModeStart: String,
    onDarkModeStartChange: (String) -> Unit,
    editDarkModeEnd: String,
    onDarkModeEndChange: (String) -> Unit,
    savedMode: String,
    savedStart: String,
    savedEnd: String,
    onSaveSettings: () -> Unit
) {
    val context = LocalContext.current

    fun showNativeTimePicker(initialTime: String, onTimeSelected: (String) -> Unit) {
        val parts = initialTime.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 22
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        android.app.TimePickerDialog(
            context,
            { _, h, m ->
                val formatted = String.format("%02d:%02d", h, m)
                onTimeSelected(formatted)
            },
            hour,
            minute,
            true // 24h format
        ).show()
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(imageVector = Icons.Default.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = AppStrings.t("Lịch trình Dark Mode màn hình trạm"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = AppStrings.t("Điều khiển bảng màu hiển thị Sáng/Tối của màn hình ST7789 trên trạm ESP32. Lịch này chỉ tác động lên màn hình, không làm thay đổi độ sáng LED hay còi chip."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Current saved schedule banner
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    val savedModeLabel = when (savedMode) {
                        "always", "always_on" -> AppStrings.t("Luôn bật (Tối)")
                        "scheduled", "custom" -> "Theo lịch ($savedStart - $savedEnd)"
                        else -> AppStrings.t("Tắt (Sáng)")
                    }
                    Text(
                        text = AppStrings.tpl("Đang lưu trên trạm: $savedModeLabel"),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }


            Text(
                text = AppStrings.t("Chế độ hiển thị trạm:"),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isOff = (editDarkMode == "off")
                val isAlways = (editDarkMode == "always_on" || editDarkMode == "always")
                val isScheduled = (editDarkMode == "scheduled" || editDarkMode == "custom")

                FilterChip(
                    selected = isOff,
                    onClick = { onDarkModeChange("off") },
                    label = { Text(AppStrings.t("Tắt (Sáng)")) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = isAlways,
                    onClick = { onDarkModeChange("always_on") },
                    label = { Text(AppStrings.t("Luôn bật (Tối)")) },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = isScheduled,
                    onClick = { onDarkModeChange("scheduled") },
                    label = { Text("Theo lịch") },
                    modifier = Modifier.weight(1f)
                )
            }

            // Scheduled Mode Detail
            if (editDarkMode == "scheduled" || editDarkMode == "custom") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = AppStrings.t("Khung giờ tự động chuyển Dark Mode:"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Native Time Pickers for Start and End
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showNativeTimePicker(editDarkModeStart, onDarkModeStartChange) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(AppStrings.t("Bắt đầu tối"), style = MaterialTheme.typography.labelSmall)
                                Text(
                                    text = editDarkModeStart,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { showNativeTimePicker(editDarkModeEnd, onDarkModeEndChange) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(AppStrings.t("Chuyển sáng"), style = MaterialTheme.typography.labelSmall)
                                Text(
                                    text = editDarkModeEnd,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    // Notice if start == end
                    if (editDarkModeStart == editDarkModeEnd) {
                        Text(
                            text = AppStrings.t("⚠ Giờ bắt đầu trùng giờ kết thúc (khoảng 0 phút): Màn hình sẽ duy trì chế độ Sáng (Tắt)."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Text(
                        text = AppStrings.t("Gợi ý khung giờ nhanh:"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "22:00" to "06:00",
                            "23:00" to "07:00",
                            "20:00" to "23:00",
                            "06:30" to "18:30"
                        ).forEach { (s, e) ->
                            AssistChip(
                                onClick = {
                                    onDarkModeStartChange(s)
                                    onDarkModeEndChange(e)
                                },
                                label = { Text("$s-$e", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }


            Button(
                onClick = onSaveSettings,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(AppStrings.t("Lưu cấu hình Dark Mode"))
            }
        }
    }
}

@Composable
fun PowerSettingsCard(
    weatherAlertEnabled: Boolean,
    onWeatherAlertToggle: (Boolean) -> Unit,
    weatherAlertTime: String,
    onWeatherAlertTimeChange: (String) -> Unit,
    weatherCity: String,
    forecastIntervals: List<WeatherIntervalDto>,
    isLoadingForecast: Boolean,
    onRefreshForecast: () -> Unit,
    batteryLow: Int,
    onBatteryLowChange: (Int) -> Unit,
    batteryHigh: Int,
    onBatteryHighChange: (Int) -> Unit,
    onSaveSettings: () -> Unit
) {
    var showTimeDialog by remember { mutableStateOf(false) }
    var tempTimeText by remember { mutableStateOf(weatherAlertTime) }
    var showForecastExpanded by remember { mutableStateOf(false) }

    if (showTimeDialog) {
        AlertDialog(
            onDismissRequest = { showTimeDialog = false },
            title = { Text(AppStrings.t("Đặt giờ quét mưa dự báo"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(AppStrings.t("Nhập thời gian quét dự báo hàng ngày (HH:mm):"), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = tempTimeText,
                        onValueChange = { tempTimeText = it },
                        placeholder = { Text("06:15") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(AppStrings.t("Gợi ý các khung giờ quét phổ biến:"), style = MaterialTheme.typography.labelSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("06:00", "06:15", "06:30", "07:00").forEach { preset ->
                            FilterChip(
                                selected = tempTimeText == preset,
                                onClick = { tempTimeText = preset },
                                label = { Text(preset) }
                            )
                        }
                    }
                }
            }
,
            confirmButton = {
                Button(
                    onClick = {
                        if (tempTimeText.matches(Regex("""^([01]\d|2[0-3]):[0-5]\d$"""))) {
                            onWeatherAlertTimeChange(tempTimeText)
                            showTimeDialog = false
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(AppStrings.t("Xác nhận"))
                }
            }
,
            dismissButton = {
                TextButton(onClick = { showTimeDialog = false }) {
                    Text("Hủy")
                }
            }

        )
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(imageVector = Icons.Default.BatteryChargingFull, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = stringResource(R.string.subcat_settings_power),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Weather alert
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_weather_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = AppStrings.tpl("Thành phố: $weatherCity"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = weatherAlertEnabled,
                    onCheckedChange = onWeatherAlertToggle
                )
            }

            // Custom Rain Check Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = AppStrings.t("Giờ quét mưa dự báo"),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = AppStrings.tpl("Đang đặt: $weatherAlertTime (GMT+7)"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                OutlinedButton(
                    onClick = {
                        tempTimeText = weatherAlertTime
                        showTimeDialog = true
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(AppStrings.t("Đổi giờ"))
                }
            }


            // Live weather forecast intervals from provider
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = AppStrings.t("Dự báo thời tiết OpenWeatherMap"),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(
                    onClick = {
                        showForecastExpanded = !showForecastExpanded
                        if (showForecastExpanded && forecastIntervals.isEmpty()) {
                            onRefreshForecast()
                        }
                    }
                ) {
                    Text(if (showForecastExpanded) "Thu gọn" else "Xem khung giờ")
                }
            }


            if (showForecastExpanded) {
                if (isLoadingForecast) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else if (forecastIntervals.isEmpty()) {
                    Text(
                        text = AppStrings.t("Không có dữ liệu dự báo. Vui lòng kiểm tra kết nối mạng backend."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        forecastIntervals.take(6).forEach { interval ->
                            val timeDisplay = interval.displayTime
                            val descDisplay = interval.displayDescription
                            val popPct = (interval.effectivePop * 100).toInt()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = timeDisplay,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${interval.temp}°C - $descDisplay",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (interval.isRain || popPct >= 40) {
                                        Text(
                                            text = AppStrings.tpl("Mưa ($popPct%)"),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else {
                                        Text(
                                            text = AppStrings.tpl("$popPct% mưa"),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    val timeOnly = timeDisplay.substringAfter(" ").take(5).ifBlank { "06:15" }
                                    TextButton(
                                        onClick = {
                                            onWeatherAlertTimeChange(timeOnly)
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("Chọn", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }


            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Battery thresholds
            Text(
                text = stringResource(R.string.settings_battery_title),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(AppStrings.tpl("Báo cắm: < ${batteryLow}%"), style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = batteryLow.toFloat(),
                        onValueChange = { onBatteryLowChange(it.roundToInt()) },
                        valueRange = 10f..40f
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(AppStrings.tpl("Báo rút: > ${batteryHigh}%"), style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = batteryHigh.toFloat(),
                        onValueChange = { onBatteryHighChange(it.roundToInt()) },
                        valueRange = 60f..95f
                    )
                }
            }


            Button(
                onClick = onSaveSettings,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(imageVector = Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.settings_save_btn))
            }
        }
    }
}

@Composable
fun TelemetryDetailCard(
    systemStatus: com.deskflow.app.data.model.SystemStatusWrapper?,
    onRefresh: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.subcat_system_telemetry),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(onClick = onRefresh) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }


            if (systemStatus != null) {
                val sys = systemStatus
                // Section 1: ESP32 Station Metrics
                Text(
                    text = AppStrings.t("Thông số ESP32 Station"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        TelemetryPill(
                            label = "Pin ESP32",
                            value = "${sys.station.batteryPct}% (${sys.station.voltage}V)",
                            icon = Icons.Default.BatteryFull
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        TelemetryPill(
                            label = "Sóng Wi-Fi",
                            value = "${sys.station.wifiRssi} dBm",
                            icon = Icons.Default.Wifi
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Section 2: Server Metrics
                Text(
                    text = AppStrings.t("Thông số Server Companion"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        TelemetryPill(
                            label = "CPU Server",
                            value = "${sys.server.cpu}%",
                            icon = Icons.Default.Memory
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        TelemetryPill(
                            label = "RAM Server",
                            value = "${sys.server.ramPct}%",
                            icon = Icons.Default.Memory
                        )
                    }
                    Box(modifier = Modifier.weight(1.3f)) {
                        TelemetryPill(
                            label = AppStrings.t("Dung lượng (20GB)"),
                            value = sys.server.diskStr.ifBlank { "${sys.server.diskUsedGb} / ${sys.server.diskTotalGb}GB" },
                            icon = Icons.Default.Storage
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Section 3: Máy tính (đang đặt để show spec)
                Text(
                    text = AppStrings.tpl("Thông số Máy tính: ${sys.laptop.deviceName}") +
                        (if (sys.laptop.online) " ● Online" else " ○ Offline"),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (sys.laptop.online) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        TelemetryPill(
                            label = AppStrings.t("CPU Máy tính"),
                            value = "${sys.laptop.cpu}%",
                            icon = Icons.Default.Laptop
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        TelemetryPill(
                            label = AppStrings.t("RAM Máy tính"),
                            value = "${sys.laptop.ram}%",
                            icon = Icons.Default.Memory
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        TelemetryPill(
                            label = AppStrings.t("Pin Máy tính"),
                            value = "${sys.laptop.battery}%",
                            icon = Icons.Default.BatteryChargingFull
                        )
                    }
                }
            }
 else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = AppStrings.t("Chưa nhận được phản hồi telemetry"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = AppStrings.t("Endpoint /api/app/control/status chưa được kích hoạt trên máy chủ sản xuất hoặc trạm chưa gửi dữ liệu lên MQTT."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    FilledTonalButton(
                        onClick = onRefresh,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(AppStrings.t("Thử lại"))
                    }
                }
            }

        }
    }
}

@Composable
fun TelemetryPill(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
