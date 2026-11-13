package com.deskflow.app.ui.morse

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deskflow.app.R
import com.deskflow.app.data.repository.ApiResult
import com.deskflow.app.data.repository.FocusRepository
import com.deskflow.app.util.MorseCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.deskflow.app.ui.i18n.AppStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MorseConverterScreen(
    repository: FocusRepository,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    isEmbedded: Boolean = false,
    onShowMessage: ((String) -> Unit)? = null
) {
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Mode: 0 = Live Bidirectional Sync, 1 = Text -> Morse, 2 = Morse -> Text
    var selectedMode by rememberSaveable { mutableIntStateOf(0) }

    var textInput by rememberSaveable { mutableStateOf("SOS DESKFLOW") }
    var morseInput by rememberSaveable { mutableStateOf("... --- ... / -.. . ... -.- ..-. .-.. --- .--") }
    var isSyncing by remember { mutableStateOf(false) }

    fun updateFromText(newText: String) {
        textInput = newText
        if (!isSyncing) {
            isSyncing = true
            morseInput = MorseCode.textToMorse(newText)
            isSyncing = false
        }
    }

    fun updateFromMorse(newMorse: String) {
        morseInput = newMorse
        if (!isSyncing) {
            isSyncing = true
            val dec = MorseCode.morseToText(newMorse)
            if (dec.isValid) {
                textInput = dec.text
            }
            isSyncing = false
        }
    }

    // Dot unit duration: 60 ms is the DeskFlow station default (20 WPM).
    // 1200 / WPM formula kept for backwards compatibility of the slider.
    var wpmSpeed by rememberSaveable { mutableIntStateOf(20) }
    val unitMs = remember(wpmSpeed) { 1200 / wpmSpeed }

    // Buzzer beep frequency: station default is 1000 Hz.
    var freqHz by rememberSaveable { mutableIntStateOf(1000) }

    var isPlaying by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var isStopping by remember { mutableStateOf(false) }
    var playbackTimerJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // Derived conversion results
    val morseOutput = remember(textInput) {
        MorseCode.textToMorse(textInput)
    }

    val morseDecodeResult = remember(morseInput) {
        MorseCode.morseToText(morseInput)
    }

    val activeMorsePattern = remember(selectedMode, morseOutput, morseInput) {
        when (selectedMode) {
            1 -> morseOutput
            2 -> MorseCode.normalizeForHardware(morseInput)
            else -> if (morseInput.isNotBlank()) MorseCode.normalizeForHardware(morseInput) else morseOutput
        }
    }

    fun showMessage(msg: String) {
        if (onShowMessage != null) {
            onShowMessage(msg)
        } else {
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
    }

    fun formatMorseError(action: String, error: ApiResult.Error): String {
        return if (error.statusCode == 405) {
            AppStrings.tpl("$action: Endpoint chưa kích hoạt trên máy chủ sản xuất (405 - Cần deploy backend)")
        } else if (error.code == "invalid_morse_pattern") {
            AppStrings.tpl("$action: Mã Morse hoặc văn bản không hợp lệ")
        } else if (error.statusCode == 401 || error.code == "token_invalid") {
            AppStrings.tpl("$action: Phiên đăng nhập hết hạn hoặc chưa xác thực (401)")
        } else if (error.statusCode == 403 || error.code == "insufficient_scope") {
            AppStrings.tpl("$action: Thiết bị thiếu quyền điều khiển 'control' (403)")
        } else if (error.statusCode == 500) {
            AppStrings.tpl("$action: Lỗi máy chủ khi gửi lệnh còi (500)")
        } else if (error.code == "timeout" || error.message.contains("timed out", ignoreCase = true)) {
            AppStrings.tpl("$action: Quá thời gian chờ máy chủ phản hồi (Timeout)")
        } else if (error.code == "network_error") {
            AppStrings.tpl("$action: Lỗi kết nối mạng (${error.message})")
        } else {
            "$action: ${error.message.ifBlank { error.code }}"
        }
    }

    fun handlePlay() {
        if (isSubmitting || isStopping) return

        val pattern = activeMorsePattern.trim()
        if (pattern.isEmpty()) {
            showMessage(AppStrings.t("Chưa có mã Morse để phát. Vui lòng nhập nội dung!"))
            return
        }

        if ((selectedMode == 0 || selectedMode == 2) && morseInput.isNotBlank() && !morseDecodeResult.isValid) {
            showMessage(morseDecodeResult.errorMessage ?: AppStrings.t("Mã Morse không hợp lệ, không thể phát!"))
            return
        }

        playbackTimerJob?.cancel()

        coroutineScope.launch {
            isSubmitting = true
            val res = repository.playMorse(pattern = pattern, unitMs = unitMs, freq = freqHz)
            isSubmitting = false
            if (res is ApiResult.Success) {
                isPlaying = true
                showMessage(AppStrings.t("Đã gửi mã Morse tới còi trạm DeskFlow!"))
                // Estimate playback duration: dots, dashes, spaces
                val estDurationMs = pattern.fold(0L) { acc, ch ->
                    acc + when (ch) {
                        '.' -> unitMs * 2L
                        '-' -> unitMs * 4L
                        ' ' -> unitMs * 2L
                        '/' -> unitMs * 6L
                        else -> 0L
                    }
                }
                // Auto reset status after estimated duration
                playbackTimerJob = launch {
                    delay(estDurationMs.coerceIn(1000L, 60000L))
                    isPlaying = false
                }
            } else if (res is ApiResult.Error) {
                isPlaying = false
                showMessage(formatMorseError(AppStrings.t("Lỗi phát còi"), res))
            }
        }
    }

    fun handleStop() {
        if (isStopping) return
        playbackTimerJob?.cancel()
        coroutineScope.launch {
            isStopping = true
            val res = repository.stopMorse()
            isStopping = false
            isPlaying = false
            if (res is ApiResult.Success) {
                showMessage(AppStrings.t("Đã gửi lệnh dừng còi tới trạm!"))
            } else if (res is ApiResult.Error) {
                showMessage(formatMorseError("Lỗi dừng còi", res))
            }
        }
    }

    @Composable
    fun MainContent(columnModifier: Modifier) {
        Column(
            modifier = columnModifier,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(2.dp))

            // ================= MODE SELECTOR TABS =================
            PrimaryTabRow(
                selectedTabIndex = selectedMode,
                modifier = Modifier.clip(RoundedCornerShape(14.dp))
            ) {
                Tab(
                    selected = selectedMode == 0,
                    onClick = { selectedMode = 0 },
                    text = {
                        Text(
                            text = stringResource(R.string.morse_tab_live),
                            fontWeight = if (selectedMode == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    icon = { Icon(Icons.Default.Sync, contentDescription = null) }
                )
                Tab(
                    selected = selectedMode == 1,
                    onClick = { selectedMode = 1 },
                    text = {
                        Text(
                            text = stringResource(R.string.morse_tab_text_to_morse),
                            fontWeight = if (selectedMode == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    icon = { Icon(Icons.Default.TextFields, contentDescription = null) }
                )
                Tab(
                    selected = selectedMode == 2,
                    onClick = { selectedMode = 2 },
                    text = {
                        Text(
                            text = stringResource(R.string.morse_tab_morse_to_text),
                            fontWeight = if (selectedMode == 2) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    icon = { Icon(Icons.Default.GraphicEq, contentDescription = null) }
                )
            }

            // ================= MODE 0: LIVE BIDIRECTIONAL SYNC =================
            if (selectedMode == 0) {
                // Card 1: Text input with live update
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = AppStrings.t("Văn bản (Plain Text - Live Sync)"),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (textInput.isNotBlank()) {
                                IconButton(onClick = {
                                    clipboardManager.setText(AnnotatedString(textInput))
                                    showMessage(AppStrings.t("Đã sao chép văn bản!"))
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Text", modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { updateFromText(it) },
                            placeholder = { Text(AppStrings.t("Nhập chữ cái tiếng Anh, số (VD: SOS, HELLO 2026)")) },
                            minLines = 2,
                            maxLines = 5,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                // Card 2: Morse input with live update & quick keypad
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = AppStrings.t("Mã Morse (Morse Code - Live Sync)"),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (morseInput.isNotBlank()) {
                                IconButton(onClick = {
                                    clipboardManager.setText(AnnotatedString(morseInput))
                                    showMessage(AppStrings.t("Đã sao chép mã Morse!"))
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy Morse", modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        OutlinedTextField(
                            value = morseInput,
                            onValueChange = { updateFromMorse(it) },
                            placeholder = { Text("VD: ... --- ... / .... . .-.. .-.. ---") },
                            minLines = 2,
                            maxLines = 5,
                            isError = !morseDecodeResult.isValid,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Error message if invalid morse tokens
                        AnimatedVisibility(visible = !morseDecodeResult.isValid && morseDecodeResult.errorMessage != null) {
                            Text(
                                text = morseDecodeResult.errorMessage ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        // Morse quick keypad
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = { updateFromMorse(morseInput + ".") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(".", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { updateFromMorse(morseInput + "-") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("-", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                            FilledTonalButton(
                                onClick = { updateFromMorse(morseInput + " ") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Space", fontSize = 11.sp)
                            }
                            FilledTonalButton(
                                onClick = { updateFromMorse(morseInput + " / ") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("/", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = {
                                    if (morseInput.isNotEmpty()) {
                                        updateFromMorse(morseInput.dropLast(1))
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Backspace, contentDescription = "Backspace")
                            }
                            IconButton(
                                onClick = {
                                    isSyncing = true
                                    textInput = ""
                                    morseInput = ""
                                    isSyncing = false
                                }
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear All")
                            }
                        }
                    }
                }
            }

            // ================= MODE 1: TEXT -> MORSE =================
            if (selectedMode == 1) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.morse_input_text_label),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { updateFromText(it) },
                            placeholder = { Text(AppStrings.t("Nhập chữ cái tiếng Anh, số (VD: SOS, HELLO 2026)")) },
                            minLines = 3,
                            maxLines = 6,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                // Output Card
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.morse_output_label),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            if (morseOutput.isNotBlank()) {
                                IconButton(onClick = {
                                    clipboardManager.setText(AnnotatedString(morseOutput))
                                    showMessage(AppStrings.t("Đã sao chép mã Morse!"))
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(20.dp))
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.background,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = morseOutput.ifBlank { AppStrings.t("Mã Morse dịch tự động sẽ hiển thị ở đây...") },
                                style = MaterialTheme.typography.bodyLarge,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (morseOutput.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
            }

            // ================= MODE 2: MORSE -> TEXT =================
            if (selectedMode == 2) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.morse_input_morse_label),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        OutlinedTextField(
                            value = morseInput,
                            onValueChange = { updateFromMorse(it) },
                            placeholder = { Text("VD: ... --- ... / .... . .-.. .-.. ---") },
                            minLines = 3,
                            maxLines = 6,
                            isError = !morseDecodeResult.isValid,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Error message
                        AnimatedVisibility(visible = !morseDecodeResult.isValid && morseDecodeResult.errorMessage != null) {
                            Text(
                                text = morseDecodeResult.errorMessage ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        // Morse quick-typing keyboard helper buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { updateFromMorse(morseInput + ".") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(".", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { updateFromMorse(morseInput + "-") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("-", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                            FilledTonalButton(
                                onClick = { updateFromMorse(morseInput + " ") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Space", fontSize = 12.sp)
                            }
                            FilledTonalButton(
                                onClick = { updateFromMorse(morseInput + " / ") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("/", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = {
                                    if (morseInput.isNotEmpty()) {
                                        updateFromMorse(morseInput.dropLast(1))
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Backspace, contentDescription = "Backspace")
                            }
                        }
                    }
                }

                // Decoded Output Card
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = AppStrings.t("Văn bản giải mã:"),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            if (morseDecodeResult.text.isNotBlank()) {
                                IconButton(onClick = {
                                    clipboardManager.setText(AnnotatedString(morseDecodeResult.text))
                                    showMessage(AppStrings.t("Đã sao chép văn bản!"))
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(20.dp))
                                }
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.background,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = morseDecodeResult.text.ifBlank { AppStrings.t("Văn bản giải mã từ Morse sẽ xuất hiện ở đây...") },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (morseDecodeResult.text.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }
                }
            }

            // ================= HARDWARE PLAYBACK CONTROLS =================
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (isPlaying) Color(0xFF10B981) else MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.VolumeUp else Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = if (isPlaying) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = AppStrings.t("Phát qua còi chip ESP32"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isPlaying) stringResource(R.string.morse_playing_status) else AppStrings.t("Âm thanh phát trực tiếp từ phần cứng trạm"),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isPlaying) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // WPM Speed Slider (5 to 30 WPM)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = AppStrings.tpl("Tốc độ phát: $wpmSpeed WPM ($unitMs ms/đơn vị)"),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Slider(
                            value = wpmSpeed.toFloat(),
                            onValueChange = { wpmSpeed = it.toInt() },
                            valueRange = 5f..30f,
                            steps = 24,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Buzzer Frequency Slider (400 Hz to 4000 Hz)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Tần số còi chip: $freqHz Hz",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (freqHz == 1000) AppStrings.t("Mặc định (1000 Hz)") else "${freqHz} Hz",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = freqHz.toFloat(),
                            onValueChange = { freqHz = (it / 100).toInt() * 100 },
                            valueRange = 400f..4000f,
                            steps = 35,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Action Buttons (Play / Stop)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { handlePlay() },
                            enabled = activeMorsePattern.isNotBlank() && !isSubmitting && !isStopping,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.morse_btn_play))
                            }
                        }

                        OutlinedButton(
                            onClick = { handleStop() },
                            enabled = !isStopping && !isSubmitting,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(0.7f)
                        ) {
                            if (isStopping) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            } else {
                                Icon(Icons.Default.Stop, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.morse_btn_stop))
                            }
                        }
                    }

                    Text(
                        text = AppStrings.t("Lưu ý: Lệnh được gửi tới trạm qua MQTT. Âm thanh phát từ còi vật lý chip ESP32 (GPIO32), không phát qua loa điện thoại."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (isEmbedded) {
        MainContent(columnModifier = modifier.fillMaxWidth())
    } else {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    },
                    title = {
                        Column {
                            Text(
                                text = stringResource(R.string.morse_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.morse_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            textInput = ""
                            morseInput = ""
                        }) {
                            Icon(imageVector = Icons.Default.ClearAll, contentDescription = "Clear")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            modifier = modifier
        ) { innerPadding ->
            MainContent(
                columnModifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
            )
        }
    }
}
