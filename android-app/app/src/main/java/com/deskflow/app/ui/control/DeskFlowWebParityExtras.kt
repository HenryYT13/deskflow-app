package com.deskflow.app.ui.control

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.deskflow.app.data.model.*
import com.deskflow.app.data.repository.ApiResult
import com.deskflow.app.data.repository.FocusRepository
import com.deskflow.app.ui.i18n.AppStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

private val PIANO_NOTES = listOf(
    "C4" to 262, "C#4" to 277, "D4" to 294, "D#4" to 311,
    "E4" to 330, "F4" to 349, "F#4" to 370, "G4" to 392,
    "G#4" to 415, "A4" to 440, "A#4" to 466, "B4" to 494,
    "C5" to 523
)

private fun playAndroidTone(freqHz: Int, durationMs: Int = 180) {
    Thread {
        runCatching {
            val sampleRate = 22050
            val count = (sampleRate * (durationMs / 1000.0)).toInt().coerceAtLeast(1)
            val samples = ShortArray(count)
            for (i in 0 until count) {
                val env = (1.0 - i.toDouble() / count).coerceAtLeast(0.05)
                samples[i] = (sin(2.0 * PI * i * freqHz / sampleRate) * Short.MAX_VALUE * 0.35 * env).toInt().toShort()
            }
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(samples.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(samples, 0, samples.size)
            track.play()
            Thread.sleep(durationMs.toLong() + 50L)
            track.release()
        }
    }.start()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShopeeRgbEffectsCard(
    repository: FocusRepository,
    settingsState: StationSettingsDto?,
    coroutineScope: CoroutineScope,
    onFeedback: (String) -> Unit
) {
    var ledEffect by remember(settingsState) {
        mutableStateOf(
            if (settingsState?.ledEffect == "rainbow") "solid"
            else (settingsState?.ledEffect ?: "solid")
        )
    }
    var rgbMode by remember(settingsState) {
        mutableStateOf(settingsState?.ledRgb == true || settingsState?.ledEffect == "rainbow")
    }
    var gradientMode by remember(settingsState) { mutableStateOf(settingsState?.ledGradient == true) }
    var lampHex by remember(settingsState) { mutableStateOf(settingsState?.lampColor ?: "#38BDF8") }
    var lampHex2 by remember(settingsState) { mutableStateOf(settingsState?.lampColor2 ?: "#8B5CF6") }
    var speed by remember(settingsState) { mutableFloatStateOf((settingsState?.ledSpeed ?: 50).toFloat()) }
    var ambientOn by remember(settingsState) { mutableStateOf(settingsState?.ambientSyncEnabled == true) }
    var bpmSyncOn by remember(settingsState) { mutableStateOf(settingsState?.bpmSyncEnabled == true) }

    val effects = listOf(
        "solid" to if (rgbMode) AppStrings.t("Cầu vồng xoay") else AppStrings.t("Màu tĩnh"),
        "breathing" to AppStrings.t("Nhịp thở"),
        "chase" to AppStrings.t("Sao băng đuổi"),
        "police" to AppStrings.t("Cảnh sát"),
        "sparkle" to AppStrings.t("Lấp lánh"),
        "fire" to AppStrings.t("Lửa trại"),
        "strobe" to AppStrings.t("Nháy Sàn"),
        "clock" to AppStrings.t("Đồng hồ 24 LED (#76)")
    )

    ElevatedCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = AppStrings.t("Hiệu ứng 24 LED, Gradient & Ambient"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Mode Toggles: RGB | Gradient (#31) | Spotify Cover Ambient | BPM Pulse (#54)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = rgbMode && !ambientOn,
                    onClick = {
                        rgbMode = true
                        gradientMode = false
                        ambientOn = false
                        coroutineScope.launch {
                            val r = repository.setLampEffect(ledEffect, speed.roundToInt(), null, true, lampHex2, false)
                            onFeedback(if (r is ApiResult.Success) "LED → RGB + $ledEffect" else AppStrings.t("Lỗi chỉnh RGB"))
                        }
                    },
                    label = { Text("RGB Flow") }
                )
                FilterChip(
                    selected = gradientMode && !rgbMode,
                    onClick = {
                        val nextGrad = !gradientMode
                        gradientMode = nextGrad
                        if (nextGrad) rgbMode = false
                        ambientOn = false
                        coroutineScope.launch {
                            repository.setLampEffect(ledEffect, speed.roundToInt(), lampHex, false, lampHex2, nextGrad)
                            onFeedback(if (nextGrad) "Gradient 24 LED: $lampHex → $lampHex2" else "Gradient OFF")
                        }
                    },
                    label = { Text("Gradient (#31)") }
                )
                FilterChip(
                    selected = ambientOn,
                    onClick = {
                        val next = !ambientOn
                        ambientOn = next
                        coroutineScope.launch {
                            repository.setAmbientSync(next)
                            onFeedback(if (next) AppStrings.t("Đã bật Ambient theo bìa Spotify!") else AppStrings.t("Đã tắt Ambient"))
                        }
                    },
                    label = { Text("Ambient Spotify") }
                )
                FilterChip(
                    selected = bpmSyncOn,
                    onClick = {
                        val next = !bpmSyncOn
                        bpmSyncOn = next
                        coroutineScope.launch {
                            repository.setBpmSync(next)
                            onFeedback(if (next) AppStrings.t("Đồng bộ LED theo nhịp BPM!") else AppStrings.t("Đã tắt BPM Sync"))
                        }
                    },
                    label = { Text("BPM Pulse (#54)") }
                )
            }

            if (gradientMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = lampHex,
                        onValueChange = { lampHex = it },
                        label = { Text("HEX A") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = lampHex2,
                        onValueChange = { lampHex2 = it },
                        label = { Text("HEX B") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = {
                        coroutineScope.launch {
                            repository.setLampEffect(ledEffect, speed.roundToInt(), lampHex, false, lampHex2, true)
                            onFeedback("Gradient → $lampHex → $lampHex2")
                        }
                    }) {
                        Text(AppStrings.t("Áp dụng"))
                    }
                }
            }

            // 7 LED Effects
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                effects.forEach { (effId, label) ->
                    FilterChip(
                        selected = ledEffect == effId && !ambientOn,
                        onClick = {
                            ledEffect = effId
                            ambientOn = false
                            coroutineScope.launch {
                                repository.setLampEffect(
                                    effId,
                                    speed.roundToInt(),
                                    if (rgbMode) null else lampHex,
                                    rgbMode,
                                    lampHex2,
                                    if (rgbMode) false else gradientMode
                                )
                                onFeedback("LED → $label")
                            }
                        },
                        label = { Text(label) }
                    )
                }
            }

            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(AppStrings.t("Tốc độ hiệu ứng"), style = MaterialTheme.typography.labelMedium)
                    Text("${speed.roundToInt()}%", fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
                Slider(
                    value = speed,
                    onValueChange = { speed = it },
                    onValueChangeFinished = {
                        coroutineScope.launch {
                            repository.setLampEffect(ledEffect, speed.roundToInt(), null, rgbMode, lampHex2, gradientMode)
                        }
                    },
                    valueRange = 10f..100f
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MiniGamesAndBootLogoCard(
    repository: FocusRepository,
    coroutineScope: CoroutineScope,
    onFeedback: (String) -> Unit
) {
    var selectedGame by remember { mutableIntStateOf(0) }
    var bootTitle by remember { mutableStateOf("DESKFLOW") }
    var bootSub by remember { mutableStateOf("24 LED STATION") }
    var ghUser by remember { mutableStateOf("HenryYT13") }

    ElevatedCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.SportsEsports, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(AppStrings.t("Arcade Hub 9 Games (#23,#58-#63)"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    0 to "🦖 Dino", 1 to "🐦 Flappy", 2 to "🐍 Snake",
                    3 to "🧩 2048", 4 to "🧱 Tetris", 5 to "👾 Invaders",
                    6 to "🏓 PingPong", 7 to "⚡ Reaction", 8 to "🎲 Lucky"
                ).forEach { (mode, title) ->
                    FilterChip(
                        selected = selectedGame == mode,
                        onClick = {
                            selectedGame = mode
                            coroutineScope.launch {
                                repository.setGameMode(mode)
                                repository.switchDisplayView("game")
                                onFeedback("ESP32 Game → $title")
                            }
                        },
                        label = { Text(title, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            val r = repository.captureStationScreenshot()
                            onFeedback(if (r is ApiResult.Success) AppStrings.t("Đã gửi lệnh chụp màn hình ESP32!") else AppStrings.t("Lỗi chụp màn hình"))
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(AppStrings.t("Chụp MH (#75)"))
                }
                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            repository.syncGithubStats(ghUser.trim())
                            repository.switchDisplayView("github")
                            onFeedback("GitHub @$ghUser -> ESP32")
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("GitHub (#57)")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            Text(AppStrings.t("Tùy chỉnh màn hình khởi động ESP32"), style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = bootTitle,
                    onValueChange = { if (it.length <= 16) bootTitle = it },
                    label = { Text("Boot Title") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = bootSub,
                    onValueChange = { if (it.length <= 20) bootSub = it },
                    label = { Text("Boot Subtitle") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            Button(
                onClick = {
                    coroutineScope.launch {
                        val r = repository.setBootLogo(bootTitle.trim(), bootSub.trim(), "")
                        onFeedback(if (r is ApiResult.Success) AppStrings.t("Đã nạp Boot Logo vào ESP32!") else AppStrings.t("Lỗi cập nhật Boot Logo"))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(AppStrings.t("Lưu Boot Logo vào ESP32 NVS"))
            }
        }
    }
}

@Composable
fun OtaFirmwareCard(
    repository: FocusRepository,
    coroutineScope: CoroutineScope,
    onFeedback: (String) -> Unit
) {
    var otaStatus by remember { mutableStateOf<OtaStatusDto?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val r = repository.getOtaStatus()
        if (r is ApiResult.Success) otaStatus = r.data.data
    }

    ElevatedCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(AppStrings.t("Cập nhật Firmware OTA không dây"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = "ESP32: v${otaStatus?.stationFwVersion ?: "--"}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace
                )
            }

            Text(
                text = if (otaStatus?.hasFirmware == true) {
                    "Server cache: v${otaStatus?.version} (${(otaStatus?.sizeBytes ?: 0) / 1024} KB)"
                } else {
                    AppStrings.t("Chưa có firmware cache trên server")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        coroutineScope.launch {
                            val r = repository.checkAndPullOta(autoPull = true, autoFlash = true, force = false)
                            busy = false
                            when (r) {
                                is ApiResult.Success -> {
                                    val d = r.data
                                    val st = repository.getOtaStatus()
                                    if (st is ApiResult.Success) otaStatus = st.data.data
                                    onFeedback(
                                        when {
                                            d.flashed -> AppStrings.tpl("Đã pull ${d.tagName} & đang nạp OTA ESP32!")
                                            d.pulled -> AppStrings.tpl("Đã tải ${d.tagName} về Server!")
                                            else -> AppStrings.tpl("ESP32 đang mới nhất (${d.tagName})")
                                        }
                                    )
                                }
                                is ApiResult.Error -> onFeedback(r.message)
                            }
                        }
                    },
                    modifier = Modifier.weight(1.4f)
                ) {
                    Text(if (busy) AppStrings.t("Đang Pull...") else AppStrings.t("Check & Pull + Flash"))
                }
                OutlinedButton(
                    enabled = !busy && otaStatus?.hasFirmware == true,
                    onClick = {
                        coroutineScope.launch {
                            val r = repository.flashOta()
                            onFeedback(if (r is ApiResult.Success) AppStrings.t("Đã gửi lệnh Flash OTA!") else AppStrings.t("Lỗi Flash OTA"))
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(AppStrings.t("Flash .bin"))
                }
            }
        }
    }
}

@Composable
fun PersistentAlarmsAndBriefingCard(
    repository: FocusRepository,
    settingsState: StationSettingsDto?,
    coroutineScope: CoroutineScope,
    onFeedback: (String) -> Unit
) {
    val context = LocalContext.current
    var alarms by remember { mutableStateOf<List<PersistentAlarmDto>>(emptyList()) }
    var timeInput by remember { mutableStateOf("06:30") }
    var labelInput by remember { mutableStateOf("Dậy học bài") }
    var antiOversleep by remember(settingsState) { mutableStateOf(settingsState?.antiOversleepEnabled ?: true) }
    var briefingText by remember { mutableStateOf("") }

    val tts = remember {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale("vi", "VN")
            }
        }
        engine
    }
    DisposableEffect(Unit) {
        onDispose { tts.shutdown() }
    }

    fun reloadAlarms() {
        coroutineScope.launch {
            val r = repository.getPersistentAlarms()
            if (r is ApiResult.Success) alarms = r.data.alarms
        }
    }

    LaunchedEffect(Unit) { reloadAlarms() }

    ElevatedCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(AppStrings.t("Báo thức cố định & Bản tin sáng (#45)"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                FilledTonalButton(onClick = {
                    coroutineScope.launch {
                        val r = repository.getMorningBriefing()
                        if (r is ApiResult.Success && r.data.ttsText.isNotBlank()) {
                            briefingText = r.data.ttsText
                            tts.speak(r.data.ttsText, TextToSpeech.QUEUE_FLUSH, null, "df_briefing")
                            onFeedback(AppStrings.t("Đang đọc Bản tin sáng!"))
                        } else {
                            onFeedback(AppStrings.t("Không lấy được bản tin sáng"))
                        }
                    }
                }) {
                    Icon(Icons.Default.RecordVoiceOver, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(AppStrings.t("Đọc TTS"))
                }
            }

            if (briefingText.isNotBlank()) {
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text(briefingText, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(10.dp))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(AppStrings.t("Chống ngủ lại (Wake-up Guard)"), style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = antiOversleep,
                    onCheckedChange = { next ->
                        antiOversleep = next
                        coroutineScope.launch {
                            val base = settingsState ?: StationSettingsDto()
                            repository.updateSettings(base.copy(antiOversleepEnabled = next))
                            onFeedback(if (next) AppStrings.t("Đã bật chống ngủ lại") else AppStrings.t("Đã tắt chống ngủ lại"))
                        }
                    }
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = timeInput,
                    onValueChange = { timeInput = it },
                    label = { Text("HH:mm") },
                    singleLine = true,
                    modifier = Modifier.weight(0.9f)
                )
                OutlinedTextField(
                    value = labelInput,
                    onValueChange = { labelInput = it },
                    label = { Text(AppStrings.t("Nhãn")) },
                    singleLine = true,
                    modifier = Modifier.weight(1.6f)
                )
                Button(onClick = {
                    coroutineScope.launch {
                        val r = repository.createPersistentAlarm(timeInput.trim(), labelInput.trim())
                        if (r is ApiResult.Success) {
                            alarms = r.data.alarms
                            onFeedback(AppStrings.t("Đã thêm báo thức $timeInput"))
                        }
                    }
                }) { Text(AppStrings.t("Thêm")) }
            }

            alarms.take(5).forEach { alarm ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${alarm.time} — ${alarm.label}", fontWeight = FontWeight.SemiBold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = alarm.enabled,
                            onCheckedChange = { next ->
                                coroutineScope.launch {
                                    val r = repository.togglePersistentAlarm(alarm.id, next)
                                    if (r is ApiResult.Success) alarms = r.data.alarms
                                }
                            }
                        )
                        IconButton(onClick = {
                            coroutineScope.launch {
                                repository.deletePersistentAlarm(alarm.id)
                                reloadAlarms()
                            }
                        }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SavedMelodyAndMiniPianoCard(
    repository: FocusRepository,
    coroutineScope: CoroutineScope,
    onFeedback: (String) -> Unit
) {
    val context = LocalContext.current
    var saved by remember { mutableStateOf<List<SavedMelodyItemDto>>(emptyList()) }
    var recording by remember { mutableStateOf(false) }
    var recordedNotes by remember { mutableStateOf<List<List<Int>>>(emptyList()) }
    var tuneName by remember { mutableStateOf("Android Tune") }
    var midiInspect by remember { mutableStateOf<Triple<String, String, List<MidiTrackDto>>?>(null) }
    var selectedTracks by remember { mutableStateOf<List<Int>>(emptyList()) }

    val midiPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            coroutineScope.launch(Dispatchers.IO) {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null && bytes.isNotEmpty()) {
                    val fname = uri.lastPathSegment?.substringAfterLast('/') ?: "upload.mid"
                    val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    val res = repository.inspectMidiBytes(fname, bytes)
                    if (res is ApiResult.Success && res.data.tracks.size > 1) {
                        midiInspect = Triple(fname, b64, res.data.tracks)
                        selectedTracks = listOfNotNull(res.data.suggestedLead ?: res.data.tracks.firstOrNull()?.index)
                        onFeedback(AppStrings.t("Đã đọc ${res.data.tracks.size} tracks MIDI!"))
                    } else {
                        val playRes = repository.playCustomMidiTracks(b64, fname, fname, null)
                        onFeedback(if (playRes is ApiResult.Success) "Playing $fname" else AppStrings.t("Lỗi đọc file .mid"))
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        val r = repository.getMelodyLibrary()
        if (r is ApiResult.Success) saved = r.data.items
    }

    ElevatedCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(AppStrings.t("Mini-Piano, .MID Picker (#112) & Thư viện"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { midiPicker.launch(arrayOf("audio/midi", "audio/x-midi", "*/*")) }) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Chọn .mid")
                }
                Spacer(Modifier.width(6.dp))
                FilterChip(
                    selected = recording,
                    onClick = { recording = !recording },
                    label = { Text(if (recording) "● Thu" else "Thu âm") }
                )
            }

            midiInspect?.let { (fname, b64, tracks) ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("🎹 Multi-Track ($fname):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tracks.forEach { tr ->
                            val picked = selectedTracks.contains(tr.index)
                            FilterChip(
                                selected = picked,
                                onClick = {
                                    selectedTracks = if (picked) selectedTracks - tr.index else selectedTracks + tr.index
                                },
                                label = { Text("T${tr.index}: ${tr.name.ifBlank { "${tr.noteCount}n" }}", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = selectedTracks.isNotEmpty(),
                            onClick = {
                                coroutineScope.launch {
                                    repository.playCustomMidiTracks(b64, fname, fname, selectedTracks)
                                    onFeedback("Playing $fname!")
                                }
                            }
                        ) { Text("Phát trên ESP32") }
                        TextButton(onClick = { midiInspect = null }) { Text("Đóng") }
                    }
                }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PIANO_NOTES.forEach { (note, freq) ->
                    val isSharp = note.contains("#")
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSharp) Color(0xFF18181B) else Color(0xFFF4F4F5),
                        border = BorderStroke(1.dp, Color(0xFF3F3F46)),
                        modifier = Modifier.clickable {
                            playAndroidTone(freq, 180)
                            if (recording) {
                                recordedNotes = recordedNotes + listOf(listOf(freq, 220))
                            } else {
                                coroutineScope.launch { repository.playMelodyNotes(note, listOf(listOf(freq, 180))) }
                            }
                        }
                    ) {
                        Text(
                            text = note,
                            color = if (isSharp) Color.White else Color.Black,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp)
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = tuneName,
                    onValueChange = { tuneName = it },
                    label = { Text(AppStrings.t("Tên (${recordedNotes.size} nốt)")) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    enabled = recordedNotes.isNotEmpty(),
                    onClick = {
                        coroutineScope.launch {
                            repository.playMelodyNotes(tuneName, recordedNotes)
                            onFeedback(AppStrings.t("Đang phát trên còi ESP32!"))
                        }
                    }
                ) { Text("ESP32") }
                Button(
                    enabled = recordedNotes.isNotEmpty(),
                    onClick = {
                        coroutineScope.launch {
                            val r = repository.saveMelodyToLibrary(tuneName.ifBlank { "Tune" }, recordedNotes, "android_piano")
                            if (r is ApiResult.Success) {
                                saved = r.data.items
                                onFeedback(AppStrings.t("Đã lưu vào thư viện!"))
                            }
                        }
                    }
                ) { Text(AppStrings.t("Lưu")) }
            }

            if (saved.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Text(AppStrings.t("Thư viện đã lưu"), style = MaterialTheme.typography.labelMedium)
                saved.take(6).forEach { m ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${m.name} (${m.noteCount} notes)", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Row {
                            IconButton(onClick = {
                                coroutineScope.launch {
                                    repository.playSavedMelody(m.id)
                                    onFeedback("Playing ${m.name}")
                                }
                            }) { Icon(Icons.Default.PlayArrow, contentDescription = null) }
                            IconButton(onClick = {
                                coroutineScope.launch {
                                    val r = repository.deleteSavedMelody(m.id)
                                    if (r is ApiResult.Success) saved = r.data.items
                                }
                            }) { Icon(Icons.Default.DeleteOutline, contentDescription = null) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AiCopilotDialog(
    repository: FocusRepository,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var messages by remember {
        mutableStateOf(
            listOf(
                Triple("ai", AppStrings.t("Xin chào! Bảo mình bật LED RGB, đổi game Snake, tạo ghi chú, bật Focus 25p hoặc thêm lịch nhé!"), "")
            )
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.SmartToy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("DeskFlow AI Copilot", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = null) }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { (role, text, act) ->
                        val isUser = role == "user"
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = text,
                                    color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                            if (act.isNotBlank()) {
                                Text("⚡ $act", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        placeholder = { Text(AppStrings.t("VD: Bật LED xanh nháy...")) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        enabled = !busy && input.isNotBlank(),
                        onClick = {
                            val msg = input.trim()
                            input = ""
                            messages = messages + Triple("user", msg, "")
                            busy = true
                            scope.launch {
                                val r = repository.sendAiCopilotChat(msg)
                                busy = false
                                messages = when (r) {
                                    is ApiResult.Success -> messages + Triple(
                                        "ai",
                                        r.data.reply,
                                        if (r.data.action != "none") r.data.action else ""
                                    )
                                    is ApiResult.Error -> messages + Triple("ai", r.message, "")
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null)
                    }
                }
            }
        }
    }
}

/**
 * (#103) Android Accelerometer Shake Phone 20x Challenge to silence active alarm.
 */
@Composable
fun ShakeAlarmDismissCard(
    alarmActive: Boolean,
    repository: FocusRepository,
    coroutineScope: CoroutineScope,
    onDismissed: (String) -> Unit
) {
    val context = LocalContext.current
    var shakeCount by remember(alarmActive) { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }

    DisposableEffect(alarmActive) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accel = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var lastShakeMs = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(ev: SensorEvent) {
                if (!alarmActive || busy) return
                val x = ev.values[0]
                val y = ev.values[1]
                val z = ev.values[2]
                val g = kotlin.math.sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
                val now = System.currentTimeMillis()
                if (g > 1.85f && now - lastShakeMs > 180L) {
                    lastShakeMs = now
                    val next = shakeCount + 1
                    shakeCount = next
                    if (next >= 20 && !busy) {
                        busy = true
                        coroutineScope.launch {
                            val r = repository.shakeDismissAlarm(next)
                            busy = false
                            if (r is ApiResult.Success) {
                                shakeCount = 0
                                onDismissed(AppStrings.t("📳 Đã lắc đủ 20 lần — Tắt báo thức thành công!"))
                                repository.refreshAlarm()
                            } else {
                                onDismissed(AppStrings.t("Lỗi gửi lệnh tắt báo thức!"))
                            }
                        }
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (alarmActive && accel != null) {
            sm.registerListener(listener, accel, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose { sm?.unregisterListener(listener) }
    }

    if (alarmActive) {
        ElevatedCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = AppStrings.t("📳 Lắc điện thoại 20 lần để tắt chuông (#103)"),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                LinearProgressIndicator(
                    progress = { (shakeCount / 20f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp)
                )
                Text(
                    text = "${shakeCount.coerceAtMost(20)} / 20 lần lắc mạnh",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}
