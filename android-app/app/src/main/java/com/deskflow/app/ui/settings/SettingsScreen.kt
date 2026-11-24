package com.deskflow.app.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.deskflow.app.R
import com.deskflow.app.data.local.SecurePreferences
import com.deskflow.app.data.repository.ApiHealthTestResult
import com.deskflow.app.data.repository.ApiResult
import com.deskflow.app.data.repository.FocusRepository
import com.deskflow.app.domain.model.DeviceInfo
import com.deskflow.app.ui.theme.ScheduledDarkModeEvaluator
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.deskflow.app.ui.i18n.AppStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repository: FocusRepository,
    securePreferences: SecurePreferences,
    onBack: () -> Unit,
    onUnpaired: () -> Unit,
    onOpenScreenSwitcher: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showUnpairConfirm by remember { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Preferences state
    val currentNavPosition by securePreferences.navBarPositionFlow.collectAsState()
    val currentTheme by securePreferences.appThemeFlow.collectAsState()
    val darkModeStart by securePreferences.darkModeStartFlow.collectAsState()
    val darkModeEnd by securePreferences.darkModeEndFlow.collectAsState()
    val dynamicColor by securePreferences.dynamicColorFlow.collectAsState()
    val currentLanguage by securePreferences.languageFlow.collectAsState()

    var editStartInput by remember(darkModeStart) { mutableStateOf(darkModeStart) }
    var editEndInput by remember(darkModeEnd) { mutableStateOf(darkModeEnd) }
    var scheduleInputError by remember { mutableStateOf<String?>(null) }

    // API Health Test state
    var healthTestResult by remember { mutableStateOf<ApiHealthTestResult?>(null) }
    var isTestingHealth by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Other Device QR Login state
    var pendingPairSessionId by remember { mutableStateOf<String?>(null) }
    var pendingPairServerUrl by remember { mutableStateOf<String?>(null) }
    var pendingPairDeviceName by remember { mutableStateOf("DeskFlow Desktop") }
    var pendingPairPlatform by remember { mutableStateOf("desktop") }
    var showPairConfirmDialog by remember { mutableStateOf(false) }
    var isApprovingPair by remember { mutableStateOf(false) }

    // Paired Devices List state
    var devicesList by remember { mutableStateOf<List<DeviceInfo>>(emptyList()) }
    var isLoadingDevices by remember { mutableStateOf(false) }
    var deviceToUnpair by remember { mutableStateOf<DeviceInfo?>(null) }
    var deviceToRename by remember { mutableStateOf<DeviceInfo?>(null) }
    var deviceRenameInput by remember { mutableStateOf("") }
    var deviceRenameBusy by remember { mutableStateOf(false) }
    var displayDeviceId by remember { mutableStateOf<String?>(null) }

    fun refreshDevices() {
        coroutineScope.launch {
            isLoadingDevices = true
            val res = repository.getDevices()
            if (res is ApiResult.Success) {
                devicesList = res.data
            }
            val selected = repository.getSelectedDisplayDevice()
            if (selected is ApiResult.Success) {
                displayDeviceId = selected.data
            }
            isLoadingDevices = false
        }
    }

    LaunchedEffect(Unit) {
        refreshDevices()
    }

    val scanOtherDeviceLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val raw = result.contents ?: return@rememberLauncherForActivityResult
        var parsedSession: String? = null
        var parsedServer: String? = null
        var parsedName: String? = null
        var parsedPlatform: String? = null

        try {
            val uri = Uri.parse(raw)
            if (uri != null && uri.isHierarchical) {
                parsedSession = uri.getQueryParameter("session") ?: uri.getQueryParameter("session_id")
                parsedServer = uri.getQueryParameter("server") ?: uri.getQueryParameter("server_url")
                parsedName = uri.getQueryParameter("name") ?: uri.getQueryParameter("device_name")
                parsedPlatform = uri.getQueryParameter("platform")
            }
        } catch (_: Exception) {}

        if (parsedSession == null) {
            val sessionRegex = Regex("(?i)(?:session|session_id)=([A-Za-z0-9_-]+)").find(raw)
            if (sessionRegex != null) {
                parsedSession = sessionRegex.groupValues[1]
            }
        }
        if (parsedServer == null) {
            val serverRegex = Regex("(?i)(?:server|server_url)=([^&\\s]+)").find(raw)
            if (serverRegex != null) {
                parsedServer = serverRegex.groupValues[1]
            }
        }
        if (parsedName == null) {
            val nameRegex = Regex("(?i)(?:name|device_name)=([^&\\s]+)").find(raw)
            if (nameRegex != null) {
                parsedName = try { java.net.URLDecoder.decode(nameRegex.groupValues[1], "UTF-8") } catch (_: Exception) { nameRegex.groupValues[1] }
            }
        }
        if (parsedPlatform == null) {
            val platformRegex = Regex("(?i)(?:platform)=([^&\\s]+)").find(raw)
            if (platformRegex != null) {
                parsedPlatform = platformRegex.groupValues[1]
            }
        }

        if (!parsedSession.isNullOrBlank()) {
            pendingPairSessionId = parsedSession
            pendingPairServerUrl = parsedServer?.takeIf { it.isNotBlank() } ?: securePreferences.apiUrl
            pendingPairDeviceName = parsedName?.takeIf { it.isNotBlank() } ?: "DeskFlow Desktop"
            pendingPairPlatform = parsedPlatform?.takeIf { it.isNotBlank() } ?: "desktop"
            showPairConfirmDialog = true
        } else {
            Toast.makeText(context, context.getString(R.string.pair_session_invalid_qr), Toast.LENGTH_SHORT).show()
        }
    }

    if (showPairConfirmDialog && pendingPairSessionId != null) {
        val sessionId = pendingPairSessionId!!
        val serverUrl = pendingPairServerUrl
        AlertDialog(
            onDismissRequest = {
                if (!isApprovingPair) {
                    showPairConfirmDialog = false
                    coroutineScope.launch {
                        repository.rejectPairSession(serverUrl, sessionId)
                    }
                    Toast.makeText(context, context.getString(R.string.pair_session_cancelled_msg), Toast.LENGTH_SHORT).show()
                }
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.dialog_pair_session_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.dialog_pair_session_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = pendingPairDeviceName,
                        onValueChange = { pendingPairDeviceName = it },
                        label = { Text(stringResource(R.string.dialog_pair_session_device_name_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            isApprovingPair = true
                            val res = repository.approvePairSession(serverUrl, sessionId, pendingPairDeviceName, pendingPairPlatform)
                            isApprovingPair = false
                            showPairConfirmDialog = false
                            when (res) {
                                is ApiResult.Success -> {
                                    val finalName = res.data.deviceName ?: pendingPairDeviceName
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.pair_session_success_msg, finalName),
                                        Toast.LENGTH_LONG
                                    ).show()
                                    refreshDevices()
                                }
                                is ApiResult.Error -> {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.pair_session_failed_msg, res.message),
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                        }
                    },
                    enabled = !isApprovingPair && pendingPairDeviceName.isNotBlank(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isApprovingPair) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(stringResource(R.string.dialog_pair_session_confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showPairConfirmDialog = false
                        coroutineScope.launch {
                            repository.rejectPairSession(serverUrl, sessionId)
                        }
                        Toast.makeText(context, context.getString(R.string.pair_session_cancelled_msg), Toast.LENGTH_SHORT).show()
                    },
                    enabled = !isApprovingPair
                ) {
                    Text(stringResource(R.string.dialog_pair_session_cancel))
                }
            }
        )
    }

    if (showUnpairConfirm) {
        AlertDialog(
            onDismissRequest = { showUnpairConfirm = false },
            title = { Text(stringResource(R.string.unpair_confirm_title)) },
            text = { Text(stringResource(R.string.unpair_confirm_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnpairConfirm = false
                        coroutineScope.launch {
                            repository.unpairDevice()
                            onUnpaired()
                        }
                    }
                ) {
                    Text(
                        stringResource(R.string.btn_confirm_unpair),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnpairConfirm = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    if (deviceToUnpair != null) {
        val dev = deviceToUnpair!!
        AlertDialog(
            onDismissRequest = { deviceToUnpair = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.LinkOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(stringResource(R.string.unlink_device_confirm_title)) },
            text = { Text(stringResource(R.string.unlink_device_confirm_desc, dev.deviceName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val targetId = dev.deviceId
                        deviceToUnpair = null
                        devicesList = devicesList.filter { it.deviceId != targetId }
                        coroutineScope.launch {
                            val res = repository.unpairOtherDevice(targetId)
                            if (res is ApiResult.Success) {
                                Toast.makeText(context, AppStrings.t("Đã xoá và hủy liên kết thiết bị"), Toast.LENGTH_SHORT).show()
                                refreshDevices()
                            } else {
                                Toast.makeText(context, AppStrings.tpl("Lỗi hủy liên kết: ${(res as ApiResult.Error).message}"), Toast.LENGTH_LONG).show()
                                refreshDevices()
                            }
                        }
                    }
                ) {
                    Text(
                        stringResource(R.string.btn_unlink_device),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deviceToUnpair = null }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    if (showScheduleDialog) {
        AlertDialog(
            onDismissRequest = { showScheduleDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.DarkMode,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.settings_darkmode_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editStartInput,
                        onValueChange = {
                            editStartInput = it
                            scheduleInputError = null
                        },
                        label = { Text("${stringResource(R.string.theme_schedule_start)} (HH:MM)") },
                        placeholder = { Text("20:00") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = editEndInput,
                        onValueChange = {
                            editEndInput = it
                            scheduleInputError = null
                        },
                        label = { Text("${stringResource(R.string.theme_schedule_end)} (HH:MM)") },
                        placeholder = { Text("06:00") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    if (scheduleInputError != null) {
                        Text(
                            text = scheduleInputError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val validRegex = Regex("^([01]\\d|2[0-3]):([0-5]\\d)$")
                        val sTrim = editStartInput.trim()
                        val eTrim = editEndInput.trim()
                        if (!validRegex.matches(sTrim) || !validRegex.matches(eTrim)) {
                            scheduleInputError = AppStrings.t("Vui lòng nhập định dạng giờ hợp lệ HH:MM (00:00 - 23:59).")
                        } else {
                            securePreferences.darkModeStart = sTrim
                            securePreferences.darkModeEnd = eTrim
                            showScheduleDialog = false
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.settings_save_btn))
                }
            },
            dismissButton = {
                TextButton(onClick = { showScheduleDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // ================= 1. NAVIGATION BAR POSITION =================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerticalAlignBottom,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.settings_nav_position_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilterChip(
                            selected = currentNavPosition == "bottom",
                            onClick = { securePreferences.navBarPosition = "bottom" },
                            label = { Text(stringResource(R.string.nav_pos_bottom)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.VerticalAlignBottom,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = currentNavPosition == "top",
                            onClick = { securePreferences.navBarPosition = "top" },
                            label = { Text(stringResource(R.string.nav_pos_top)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.VerticalAlignTop,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ================= 2. THEME & SCHEDULED DARK MODE SETTINGS =================
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.theme_label),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // Row 1: System & Light
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = currentTheme == "system",
                            onClick = { securePreferences.appTheme = "system" },
                            label = { Text(stringResource(R.string.theme_system)) },
                            leadingIcon = {
                                Icon(Icons.Default.SettingsBrightness, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = currentTheme == "light",
                            onClick = { securePreferences.appTheme = "light" },
                            label = { Text(stringResource(R.string.theme_light)) },
                            leadingIcon = {
                                Icon(Icons.Default.LightMode, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Row 2: Dark & Scheduled
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = currentTheme == "dark",
                            onClick = { securePreferences.appTheme = "dark" },
                            label = { Text(stringResource(R.string.theme_dark)) },
                            leadingIcon = {
                                Icon(Icons.Default.DarkMode, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = currentTheme == "scheduled",
                            onClick = { securePreferences.appTheme = "scheduled" },
                            label = { Text(stringResource(R.string.theme_scheduled)) },
                            leadingIcon = {
                                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Scheduled Dark Mode Configuration Panel
                    AnimatedVisibility(visible = currentTheme == "scheduled") {
                        val isNowDark = ScheduledDarkModeEvaluator.isScheduledDarkActiveNow(
                            startStr = darkModeStart,
                            endStr = darkModeEnd
                        )
                        val isEqualTimes = darkModeStart == darkModeEnd

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${stringResource(R.string.theme_schedule_start)}: $darkModeStart  •  ${stringResource(R.string.theme_schedule_end)}: $darkModeEnd",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = when {
                                                isEqualTimes -> AppStrings.t("Giờ bắt đầu trùng giờ kết thúc (Đang giữ chế độ Sáng)")
                                                isNowDark -> AppStrings.tpl("Hiện tại đang trong khung giờ Tối ($darkModeStart – $darkModeEnd)")
                                                else -> AppStrings.tpl("Hiện tại ngoài khung giờ Tối (Chuyển Tối lúc $darkModeStart)")
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    FilledTonalButton(
                                        onClick = {
                                            editStartInput = darkModeStart
                                            editEndInput = darkModeEnd
                                            scheduleInputError = null
                                            showScheduleDialog = true
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(AppStrings.t("Đổi giờ"), style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }

                            // Quick Schedule Presets
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    Triple("20:00", "06:00", "20:00–06:00"),
                                    Triple("22:00", "06:00", "22:00–06:00"),
                                    Triple("21:00", "07:00", "21:00–07:00")
                                ).forEach { (s, e, label) ->
                                    val selected = darkModeStart == s && darkModeEnd == e
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            securePreferences.darkModeStart = s
                                            securePreferences.darkModeEnd = e
                                        },
                                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    // Dynamic Color Switch on Android 12+
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.theme_dynamic_color),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Switch(
                                checked = dynamicColor,
                                onCheckedChange = { securePreferences.dynamicColor = it }
                            )
                        }
                    }
                }
            }

            // ================= 3. API HEALTH TEST =================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NetworkCheck,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.api_test_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.api_test_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.api_url_label),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            securePreferences.apiUrl,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    FilledTonalButton(
                        onClick = {
                            coroutineScope.launch {
                                isTestingHealth = true
                                healthTestResult = repository.testApiHealth()
                                isTestingHealth = false
                            }
                        },
                        enabled = !isTestingHealth,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isTestingHealth) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.api_test_testing))
                        } else {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.api_test_run_btn))
                        }
                    }

                    healthTestResult?.let { res ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (res.isSuccess) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (res.isSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                        contentDescription = null,
                                        tint = if (res.isSuccess) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = if (res.isSuccess) {
                                            stringResource(R.string.api_test_status_healthy)
                                        } else {
                                            stringResource(R.string.api_test_status_failed)
                                        },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (res.isSuccess) Color(0xFF10B981) else MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                res.statusCode?.let { code ->
                                    Text(
                                        text = stringResource(R.string.api_test_http_status, code),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Text(
                                    text = stringResource(R.string.api_test_latency, res.latencyMs),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                res.service?.let { svc ->
                                    Text(
                                        text = stringResource(R.string.api_test_service, svc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                res.version?.let { ver ->
                                    Text(
                                        text = stringResource(R.string.api_test_version, ver),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                val timeFormat = SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale.getDefault())
                                Text(
                                    text = stringResource(R.string.api_test_last_run, timeFormat.format(Date(res.timestamp))),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                res.errorMessage?.let { msg ->
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Details: $msg",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ================= 4. LANGUAGE SETTINGS =================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.language_label),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilterChip(
                            selected = currentLanguage == "vi",
                            onClick = {
                                securePreferences.language = "vi"
                                coroutineScope.launch {
                                    val cfg = (repository.getClockConfig() as? ApiResult.Success)?.data ?: com.deskflow.app.data.model.ClockConfigDto()
                                    repository.updateClockConfig(cfg.copy(language = "vi"))
                                }
                            },
                            label = { Text("Tiếng Việt") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = currentLanguage == "en",
                            onClick = {
                                securePreferences.language = "en"
                                coroutineScope.launch {
                                    val cfg = (repository.getClockConfig() as? ApiResult.Success)?.data ?: com.deskflow.app.data.model.ClockConfigDto()
                                    repository.updateClockConfig(cfg.copy(language = "en"))
                                }
                            },
                            label = { Text("English") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ================= 5. DEVICE INFORMATION =================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.settings_device_group),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.device_name_label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(securePreferences.deviceName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(stringResource(R.string.device_id_label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val idDisplay = securePreferences.deviceId?.let {
                            if (it.length > 12) "${it.take(8)}...${it.takeLast(4)}" else it
                        } ?: "N/A"
                        Text(idDisplay, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            // ================= DEVICE RENAME + OVERLAY =================
            val ctx = androidx.compose.ui.platform.LocalContext.current
            var renameText by remember { mutableStateOf(securePreferences.deviceName) }
            var renameStatus by remember { mutableStateOf<String?>(null) }
            var overlayGranted by remember { mutableStateOf(android.provider.Settings.canDrawOverlays(ctx)) }
            val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
                    if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        overlayGranted = android.provider.Settings.canDrawOverlays(ctx)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(obs)
                onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
            }
            val renameOk = stringResource(R.string.device_rename_success)
            val renameFail = stringResource(R.string.device_rename_failed)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it; renameStatus = null },
                        label = { Text(stringResource(R.string.device_rename_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                renameStatus = if (repository.renameDevice(renameText) is com.deskflow.app.data.repository.ApiResult.Success) renameOk else renameFail
                            }
                        },
                        enabled = renameText.isNotBlank() && renameText.trim() != securePreferences.deviceName
                    ) { Text(stringResource(R.string.device_rename_button)) }
                    renameStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.overlay_title), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.overlay_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(8.dp))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = AppStrings.t("Chạy nền khi bật Focus Mode"),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = AppStrings.t("Tắt tối ưu pin cho DeskFlow để dịch vụ Focus Mode không bị hệ thống dừng khi chạy nền."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val powerManager = remember { ctx.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager }
                    var batteryExempt by remember { mutableStateOf(powerManager.isIgnoringBatteryOptimizations(ctx.packageName)) }
                    if (batteryExempt) {
                        Text(AppStrings.t("Đã tắt tối ưu pin."), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Button(onClick = {
                            try {
                                ctx.startActivity(
                                    android.content.Intent(
                                        android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                        android.net.Uri.parse("package:" + ctx.packageName)
                                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            } catch (_: Exception) {
                                ctx.startActivity(
                                    android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                            batteryExempt = powerManager.isIgnoringBatteryOptimizations(ctx.packageName)
                        }) { Text(AppStrings.t("Tắt tối ưu pin")) }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    if (overlayGranted) {
                        Text(stringResource(R.string.overlay_granted), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Button(onClick = {
                            ctx.startActivity(
                                android.content.Intent(
                                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    android.net.Uri.parse("package:" + ctx.packageName)
                                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }) { Text(stringResource(R.string.overlay_grant)) }
                    }
                }
            }

            // ================= LIÊN KẾT THIẾT BỊ KHÁC =================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.settings_other_devices_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.settings_other_devices_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            val options = ScanOptions().apply {
                                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                setPrompt(context.getString(R.string.settings_other_devices_prompt))
                                setBeepEnabled(false)
                                setOrientationLocked(false)
                                setBarcodeImageEnabled(false)
                            }
                            scanOtherDeviceLauncher.launch(options)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.btn_scan_qr_other_device))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ================= 5b. LINKED DEVICES =================
            if (devicesList.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${stringResource(R.string.linked_devices_title)} (${devicesList.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(onClick = { refreshDevices() }) {
                                if (isLoadingDevices) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        Text(
                            text = stringResource(R.string.linked_devices_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        devicesList.forEachIndexed { idx, dev ->
                            if (idx > 0) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val icon = when (dev.platform.lowercase()) {
                                        "android" -> Icons.Default.PhoneAndroid
                                        "browser_extension", "extension", "chrome", "firefox" -> Icons.Default.Extension
                                        else -> Icons.Default.Laptop
                                    }
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (dev.isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = dev.deviceName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (dev.isCurrent) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = stringResource(R.string.this_device_badge),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = AppStrings.tpl("Nền tảng: ${dev.platform}"),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        deviceRenameInput = dev.deviceName
                                        deviceToRename = dev
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = stringResource(R.string.device_rename_button),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        if (dev.isCurrent) {
                                            showUnpairConfirm = true
                                        } else {
                                            deviceToUnpair = dev
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DeleteOutline,
                                        contentDescription = stringResource(R.string.btn_unlink_device),
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ================= 5c. CHỌN THIẾT BỊ HIỂN THỊ SPEC =================
            if (devicesList.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.display_device_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.display_device_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        devicesList.forEach { dev ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = displayDeviceId == dev.deviceId,
                                    onClick = {
                                        displayDeviceId = dev.deviceId
                                        coroutineScope.launch {
                                            repository.setSelectedDisplayDevice(dev.deviceId)
                                            repository.refreshSystemStatus()
                                        }
                                    }
                                )
                                Text(
                                    text = dev.deviceName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Rename dialog for any linked device
            deviceToRename?.let { target ->
                AlertDialog(
                    onDismissRequest = { if (!deviceRenameBusy) deviceToRename = null },
                    title = { Text(stringResource(R.string.device_rename_label)) },
                    text = {
                        Column {
                            OutlinedTextField(
                                value = deviceRenameInput,
                                onValueChange = { deviceRenameInput = it },
                                singleLine = true,
                                label = { Text(stringResource(R.string.device_rename_label)) }
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(
                            enabled = deviceRenameInput.isNotBlank() && !deviceRenameBusy,
                            onClick = {
                                coroutineScope.launch {
                                    deviceRenameBusy = true
                                    val res = repository.renameLinkedDevice(target.deviceId, deviceRenameInput)
                                    deviceRenameBusy = false
                                    if (res is ApiResult.Success) {
                                        deviceToRename = null
                                        refreshDevices()
                                    }
                                }
                            }
                        ) { Text(stringResource(R.string.device_rename_button)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { deviceToRename = null }) { Text("Cancel") }
                    }
                )
            }

            // ================= 5B. PUSH NOTIFICATIONS, PUBLIC BADGE (#78) & BACKUP/RESTORE (#80) =================
            val clipboard = LocalClipboardManager.current
            var ntfyInput by remember { mutableStateOf("") }
            var showRestoreDialog by remember { mutableStateOf(false) }
            var restoreJsonInput by remember { mutableStateOf("") }

            LaunchedEffect(Unit) {
                val stRes = repository.refreshSettings()
                if (stRes is ApiResult.Success) {
                    ntfyInput = stRes.data.data?.ntfyTopic ?: ""
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = AppStrings.t("Thông báo ntfy.sh, Public Badge & Backup JSON"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = ntfyInput,
                            onValueChange = { ntfyInput = it },
                            label = { Text("ntfy.sh topic") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = {
                            coroutineScope.launch {
                                val curr = (repository.refreshSettings() as? ApiResult.Success)?.data?.data
                                    ?: com.deskflow.app.data.model.StationSettingsDto()
                                val r = repository.updateSettings(curr.copy(ntfyTopic = ntfyInput.trim()))
                                Toast.makeText(
                                    context,
                                    if (r is ApiResult.Success) "Đã lưu ntfy topic!" else "Lỗi lưu topic",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }) { Text(AppStrings.t("Lưu")) }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                coroutineScope.launch {
                                    val r = repository.sendTestNotification()
                                    Toast.makeText(
                                        context,
                                        if (r is ApiResult.Success) "Đã bắn thông báo test!" else "Lỗi test push",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text(AppStrings.t("Test Push ĐT")) }

                        OutlinedButton(
                            onClick = {
                                val md = "[![DeskFlow Live](https://df.api.thetechdude.io.vn/api/public/badge.svg)](https://deskflow.thetechdude.io.vn)"
                                clipboard.setText(AnnotatedString(md))
                                Toast.makeText(context, "Đã copy GitHub Badge Markdown!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Copy Badge (#78)") }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    val r = repository.exportBackup()
                                    if (r is ApiResult.Success && r.data.backup != null) {
                                        val txt = r.data.backup.toString()
                                        clipboard.setText(AnnotatedString(txt))
                                        Toast.makeText(context, "Đã copy Backup JSON vào Clipboard!", Toast.LENGTH_SHORT).show()
                                        val send = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/json"
                                            putExtra(Intent.EXTRA_TEXT, txt)
                                        }
                                        context.startActivity(Intent.createChooser(send, "Export DeskFlow Backup JSON"))
                                    } else {
                                        Toast.makeText(context, "Không thể xuất backup!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text(AppStrings.t("Xuất Backup (#80)")) }

                        OutlinedButton(
                            onClick = { showRestoreDialog = true },
                            modifier = Modifier.weight(1f)
                        ) { Text(AppStrings.t("Khôi phục JSON")) }
                    }
                }
            }

            if (showRestoreDialog) {
                AlertDialog(
                    onDismissRequest = { showRestoreDialog = false },
                    title = { Text(AppStrings.t("Khôi phục cấu hình JSON (#80)")) },
                    text = {
                        OutlinedTextField(
                            value = restoreJsonInput,
                            onValueChange = { restoreJsonInput = it },
                            label = { Text("Dán nội dung Backup JSON...") },
                            minLines = 4,
                            maxLines = 8,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            coroutineScope.launch {
                                runCatching {
                                    val obj = Json.parseToJsonElement(restoreJsonInput.trim()).jsonObject
                                    repository.restoreBackup(obj)
                                }.onSuccess { res ->
                                    showRestoreDialog = false
                                    Toast.makeText(
                                        context,
                                        if (res is ApiResult.Success) "Đã khôi phục toàn bộ cài đặt!" else "Lỗi khôi phục!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }.onFailure {
                                    Toast.makeText(context, "JSON không hợp lệ!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }) { Text(AppStrings.t("Khôi phục")) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showRestoreDialog = false }) { Text("Cancel") }
                    }
                )
            }

            // ================= 6. ABOUT =================
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.about_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.version_label, com.deskflow.app.BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.about_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ================= 7. UNPAIR BUTTON =================
            OutlinedButton(
                onClick = { showUnpairConfirm = true },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.LinkOff, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.btn_unpair))
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
