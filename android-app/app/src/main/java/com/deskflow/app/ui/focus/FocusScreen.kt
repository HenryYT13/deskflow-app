package com.deskflow.app.ui.focus

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deskflow.app.R
import com.deskflow.app.data.model.FocusSessionDto
import com.deskflow.app.data.repository.ApiResult
import com.deskflow.app.data.repository.FocusRepository
import com.deskflow.app.domain.model.ChallengeInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(
    repository: FocusRepository,
    onOpenScreenSwitcher: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val focusState by repository.focusState.collectAsState()
    val focusHistory by repository.focusHistory.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var selectedDuration by remember { mutableIntStateOf(25) }
    var customDurationText by remember { mutableStateOf("") }
    val customDurationValue = customDurationText.toIntOrNull()
    val customDurationValid =
        customDurationText.isEmpty() || (customDurationValue != null && customDurationValue in 1..300)
    var reasonText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var isRefreshingHistory by remember { mutableStateOf(false) }

    var activeDialogChallenge by remember { mutableStateOf<ChallengeInfo?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Countdown State for Active Session
    var liveRemainingSeconds by remember(focusState.remainingSeconds, focusState.isActive) {
        mutableLongStateOf(
            focusState.remainingSeconds?.takeIf { it > 0 }
                ?: (focusState.durationMinutes.coerceAtLeast(1) * 60L)
        )
    }

    LaunchedEffect(focusState.isActive, liveRemainingSeconds) {
        if (focusState.isActive && liveRemainingSeconds > 0) {
            delay(1000L)
            liveRemainingSeconds--
        }
    }

    LaunchedEffect(Unit) {
        repository.refreshStatus()
        repository.refreshFocusHistory()
    }

    if (activeDialogChallenge != null) {
        ChallengeDialog(
            initialChallenge = activeDialogChallenge!!,
            repository = repository,
            onDismiss = { activeDialogChallenge = null },
            onSuccessUnlocked = {
                activeDialogChallenge = null
                coroutineScope.launch {
                    repository.refreshStatus()
                    repository.refreshFocusHistory()
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.card_focus_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (focusState.isActive) Color(0xFF10B981)
                                        else MaterialTheme.colorScheme.outline
                                    )
                            )
                            Text(
                                text = if (focusState.isActive)
                                    stringResource(R.string.status_active)
                                else
                                    stringResource(R.string.status_inactive),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (focusState.isActive)
                                    Color(0xFF10B981)
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (selectedTabIndex == 1) {
                        IconButton(
                            onClick = {
                                coroutineScope.launch {
                                    isRefreshingHistory = true
                                    repository.refreshFocusHistory()
                                    isRefreshingHistory = false
                                }
                            },
                            enabled = !isRefreshingHistory
                        ) {
                            if (isRefreshingHistory) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
                            }
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tab Header (Active Session vs History)
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) }
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text(stringResource(R.string.focus_tab_active), fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(imageVector = Icons.Default.Timer, contentDescription = null) }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = {
                        selectedTabIndex = 1
                        coroutineScope.launch { repository.refreshFocusHistory() }
                    },
                    text = { Text(stringResource(R.string.focus_tab_history), fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(imageVector = Icons.Default.History, contentDescription = null) }
                )
            }

            // Tab Content
            when (selectedTabIndex) {
                0 -> {
                    // TAB 0: CURRENT SESSION
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (focusState.isActive) {
                            // ==================== ACTIVE FOCUS MODE ====================
                            val totalSec = (focusState.durationMinutes.coerceAtLeast(1) * 60L).toFloat()
                            val progress = if (totalSec > 0) {
                                (liveRemainingSeconds.toFloat() / totalSec).coerceIn(0f, 1f)
                            } else 1f
                            val animatedProgress by animateFloatAsState(
                                targetValue = progress,
                                animationSpec = tween(durationMillis = 500),
                                label = "gauge_progress"
                            )

                            val elapsedSec = (totalSec.toLong() - liveRemainingSeconds).coerceAtLeast(0L)
                            val elapsedMin = elapsedSec / 60
                            val elapsedRestSec = elapsedSec % 60

                            val remMin = liveRemainingSeconds / 60
                            val remSec = liveRemainingSeconds % 60
                            val timeDisplay = String.format(Locale.getDefault(), "%02d:%02d", remMin, remSec)

                            // Glowing circular countdown gauge Card
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(28.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(20.dp)
                                ) {
                                    // Circular Countdown Gauge
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.size(230.dp)
                                    ) {
                                        // Background Track Ring
                                        CircularProgressIndicator(
                                            progress = { 1f },
                                            modifier = Modifier.fillMaxSize(),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            strokeWidth = 14.dp,
                                            trackColor = Color.Transparent
                                        )

                                        // Glowing Active Progress Arc
                                        CircularProgressIndicator(
                                            progress = { animatedProgress },
                                            modifier = Modifier.fillMaxSize(),
                                            color = MaterialTheme.colorScheme.primary,
                                            strokeWidth = 14.dp,
                                            trackColor = Color.Transparent
                                        )

                                        // Gauge Center Info
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(28.dp)
                                            )
                                            Text(
                                                text = timeDisplay,
                                                style = MaterialTheme.typography.displaySmall,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = stringResource(R.string.time_remaining).uppercase(),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                letterSpacing = 1.2.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    // Target Goal Badge
                                    val goalText = focusState.reason.ifBlank {
                                        stringResource(R.string.card_focus_title)
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Flag,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = goalText,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }

                                    // Stats Row: Total Duration vs Elapsed
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Card(
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(12.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = "Mục tiêu ban đầu",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    text = "${focusState.durationMinutes} phút",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }

                                        Card(
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(12.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = "Đã trôi qua",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    text = String.format(Locale.getDefault(), "%02d:%02d", elapsedMin, elapsedRestSec),
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }

                                    if (actionError != null) {
                                        Text(
                                            text = actionError!!,
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall,
                                            textAlign = TextAlign.Center
                                        )
                                    }

                                    // Sleek Unlock Math Challenge Button
                                    Button(
                                        onClick = {
                                            isLoading = true
                                            actionError = null
                                            coroutineScope.launch {
                                                val challengeRes = repository.requestChallenge()
                                                isLoading = false
                                                when (challengeRes) {
                                                    is ApiResult.Success -> {
                                                        activeDialogChallenge = challengeRes.data
                                                    }
                                                    is ApiResult.Error -> {
                                                        actionError = "Không thể tạo thử thách: ${challengeRes.code}"
                                                    }
                                                }
                                            }
                                        },
                                        enabled = !isLoading,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.error
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(54.dp),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        if (isLoading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.onError
                                            )
                                        } else {
                                            Icon(imageVector = Icons.Default.Calculate, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = stringResource(R.string.btn_request_challenge),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // ==================== IDLE STATE: START FOCUS ====================
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(28.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    verticalArrangement = Arrangement.spacedBy(18.dp)
                                ) {
                                    // Header with Gradient Accent Icon
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(Color(0xFF0284C7), Color(0xFF6366F1))
                                                    )
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.SelfImprovement,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                        Column {
                                            Text(
                                                text = stringResource(R.string.start_focus_title),
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Khóa xao nhãng & đồng bộ mọi thiết bị",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                                    // Duration Selector: 2m, 15m, 25m, 45m, 60m, 90m
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = stringResource(R.string.duration_label),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            listOf(2, 15, 25, 45, 60, 90).forEach { mins ->
                                                val isSelected =
                                                    selectedDuration == mins && customDurationText.isEmpty()
                                                FilterChip(
                                                    selected = isSelected,
                                                    onClick = {
                                                        selectedDuration = mins
                                                        customDurationText = ""
                                                    },
                                                    label = {
                                                        Text(
                                                            text = "${mins}m",
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                    },
                                                    shape = RoundedCornerShape(12.dp),
                                                    modifier = Modifier.weight(1f)
                                                )
                                            }
                                        }

                                        // Custom duration: any value from 1 to 300 minutes
                                        OutlinedTextField(
                                            value = customDurationText,
                                            onValueChange = { raw ->
                                                val digits = raw.filter { it.isDigit() }.take(3)
                                                customDurationText = digits
                                                digits.toIntOrNull()
                                                    ?.takeIf { it in 1..300 }
                                                    ?.let { selectedDuration = it }
                                            },
                                            label = { Text("Tùy chỉnh số phút (1–300)") },
                                            placeholder = { Text("VD: 2") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            isError = !customDurationValid,
                                            shape = RoundedCornerShape(14.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        if (!customDurationValid) {
                                            Text(
                                                text = "Số phút phải từ 1 đến 300",
                                                color = MaterialTheme.colorScheme.error,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }

                                    // Study Goal Input
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = stringResource(R.string.reason_label),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        OutlinedTextField(
                                            value = reasonText,
                                            onValueChange = { reasonText = it },
                                            placeholder = { Text(stringResource(R.string.reason_placeholder)) },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp),
                                            singleLine = true
                                        )

                                        // Quick suggestion chips
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            listOf("Ôn thi Toán", "Lập trình", "Đọc sách", "Tiếng Anh").forEach { suggestion ->
                                                SuggestionChip(
                                                    onClick = { reasonText = suggestion },
                                                    label = { Text(suggestion, style = MaterialTheme.typography.labelSmall) },
                                                    shape = RoundedCornerShape(10.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (actionError != null) {
                                        Text(
                                            text = actionError!!,
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }

                                    // Animated Start Focus Button
                                    Button(
                                        onClick = {
                                            isLoading = true
                                            actionError = null
                                            coroutineScope.launch {
                                                val res = repository.enableFocus(selectedDuration, reasonText)
                                                isLoading = false
                                                if (res is ApiResult.Error) {
                                                    actionError = "Kích hoạt thất bại: ${res.code}"
                                                }
                                            }
                                        },
                                        enabled = !isLoading && customDurationValid,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(54.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        if (isLoading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        } else {
                                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = stringResource(R.string.btn_start_focus),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // TAB 1: HISTORY (PERSISTED HISTORY)
                    if (focusHistory.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HistoryToggleOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(56.dp)
                                )
                                Text(
                                    text = stringResource(R.string.history_empty_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.history_empty_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(focusHistory, key = { it.id }) { session ->
                                FocusHistoryCard(session = session)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FocusHistoryCard(
    session: FocusSessionDto,
    modifier: Modifier = Modifier
) {
    val isCompleted = session.status.equals("completed", ignoreCase = true)
    val isActive = session.status.equals("active", ignoreCase = true)

    val durationMinutes = if (session.durationSeconds > 0) {
        (session.durationSeconds / 60).coerceAtLeast(1)
    } else 0

    val startTimeFormatted = formatIsoDate(session.startTime)
    val endTimeFormatted = session.endTime?.let { formatIsoDate(it) }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status Chip
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = when {
                        isCompleted -> MaterialTheme.colorScheme.primaryContainer
                        isActive -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = when {
                                isCompleted -> Icons.Default.CheckCircle
                                isActive -> Icons.Default.PlayCircle
                                else -> Icons.Default.Warning
                            },
                            contentDescription = null,
                            tint = when {
                                isCompleted -> MaterialTheme.colorScheme.primary
                                isActive -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = when {
                                isCompleted -> stringResource(R.string.session_status_completed)
                                isActive -> "Đang hoạt động"
                                else -> stringResource(R.string.session_status_interrupted)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isCompleted -> MaterialTheme.colorScheme.primary
                                isActive -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                // Duration badge
                if (durationMinutes > 0) {
                    Text(
                        text = stringResource(R.string.session_duration_format, durationMinutes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Timestamps
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Bắt đầu",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = startTimeFormatted,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (endTimeFormatted != null) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Kết thúc",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = endTimeFormatted,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            if (!session.reason.isNullOrBlank()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Text(
                    text = session.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

fun formatIsoDate(isoStr: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        parser.timeZone = TimeZone.getTimeZone("UTC")
        val clean = if (isoStr.contains(".")) isoStr.substringBefore(".") else isoStr.replace("Z", "")
        val date = parser.parse(clean)
        val formatter = SimpleDateFormat("HH:mm - dd/MM", Locale.getDefault())
        if (date != null) formatter.format(date) else isoStr
    } catch (_: Exception) {
        isoStr
    }
}
