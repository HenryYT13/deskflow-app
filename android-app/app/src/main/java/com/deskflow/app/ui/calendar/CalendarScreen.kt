package com.deskflow.app.ui.calendar

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.deskflow.app.R
import com.deskflow.app.data.model.CalendarConfigRequest
import com.deskflow.app.data.model.CreateCalendarEventRequest
import com.deskflow.app.data.repository.ApiResult
import com.deskflow.app.data.repository.FocusRepository
import com.deskflow.app.ui.i18n.AppStrings
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    repository: FocusRepository,
    onOpenScreenSwitcher: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val calState by repository.calendarState.collectAsState()
    val scope = rememberCoroutineScope()

    var isBusy by remember { mutableStateOf(false) }
    var feedbackMsg by remember { mutableStateOf<String?>(null) }

    var icalUrlInput by remember(calState.icalUrl) { mutableStateOf(calState.icalUrl) }
    var clientIdInput by remember(calState.googleClientId) { mutableStateOf(calState.googleClientId) }
    var clientSecretInput by remember { mutableStateOf("") }
    var redirectUriInput by remember(calState.googleRedirectUri) { mutableStateOf(calState.googleRedirectUri) }
    var showOAuthCreds by remember { mutableStateOf(false) }

    val defaultDate = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    var newTitle by remember { mutableStateOf("") }
    var newDate by remember { mutableStateOf(defaultDate) }
    var newStart by remember { mutableStateOf("09:00") }
    var newEnd by remember { mutableStateOf("10:00") }
    var newLocation by remember { mutableStateOf("") }
    var newAllDay by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        repository.refreshCalendarStatus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.nav_calendar),
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(
                        enabled = !isBusy,
                        onClick = {
                            scope.launch {
                                isBusy = true
                                val res = repository.syncCalendar()
                                isBusy = false
                                feedbackMsg = when (res) {
                                    is ApiResult.Success -> AppStrings.t("Đã đồng bộ Google Calendar!")
                                    is ApiResult.Error -> res.message
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Sync")
                    }
                    IconButton(onClick = onOpenScreenSwitcher) {
                        Icon(Icons.Default.Apps, contentDescription = "Switch Screen")
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            feedbackMsg?.let { msg ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = msg,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Status & Push to ESP32
            ElevatedCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (calState.connected) {
                                    "${calState.todayCount} " + AppStrings.t("hôm nay") + " • ${calState.events.size} " + AppStrings.t("sự kiện")
                                } else {
                                    AppStrings.t("Chưa kết nối Google Calendar")
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            val modeLabel = buildList {
                                if (calState.oauthConnected) add("OAuth (${calState.connectedEmail})")
                                if (calState.icalConnected) add("Private iCal")
                            }.joinToString(" + ")
                            if (modeLabel.isNotBlank()) {
                                Text(
                                    text = modeLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            calState.lastError?.takeIf { it.isNotBlank() }?.let { err ->
                                Text(
                                    text = err,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    repository.syncCalendar()
                                    val r = repository.switchDisplayView("calendar")
                                    feedbackMsg = if (r is ApiResult.Success) {
                                        AppStrings.t("Đã chuyển ESP32 sang Lịch Google!")
                                    } else {
                                        AppStrings.t("Không thể chuyển màn hình ESP32")
                                    }
                                }
                            }
                        ) {
                            Text(AppStrings.t("Hiện lên ESP32"))
                        }
                    }
                }
            }

            // Google OAuth 2.0 Card
            ElevatedCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Google OAuth 2.0 (2 chiều)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (calState.oauthConnected) {
                            "${calState.connectedEmail} • ${calState.selectedCalendarName}"
                        } else {
                            AppStrings.t("Chưa đăng nhập OAuth")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (calState.oauthConnected && calState.calendars.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            calState.calendars.take(4).forEach { cal ->
                                FilterChip(
                                    selected = cal.id == calState.selectedCalendarId,
                                    onClick = {
                                        scope.launch {
                                            repository.updateCalendarConfig(
                                                CalendarConfigRequest(selectedCalendarId = cal.id)
                                            )
                                        }
                                    },
                                    label = { Text(cal.summary, maxLines = 1) }
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    when (val res = repository.startCalendarOAuth()) {
                                        is ApiResult.Success -> {
                                            val url = res.data.authUrl
                                            if (!url.isNullOrBlank()) {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                                feedbackMsg = AppStrings.t("Đã mở trình duyệt đăng nhập Google.")
                                            }
                                        }
                                        is ApiResult.Error -> {
                                            feedbackMsg = res.message
                                        }
                                    }
                                }
                            }
                        ) {
                            Text(AppStrings.t("Đăng nhập Google"))
                        }
                        if (calState.oauthConnected) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        repository.disconnectCalendarOAuth()
                                        feedbackMsg = AppStrings.t("Đã ngắt Google OAuth.")
                                    }
                                }
                            ) {
                                Text(AppStrings.t("Ngắt OAuth"))
                            }
                        }
                        TextButton(onClick = { showOAuthCreds = !showOAuthCreds }) {
                            Text(if (showOAuthCreds) AppStrings.t("Ẩn OAuth ID") else "Client ID/Secret")
                        }
                    }

                    if (showOAuthCreds) {
                        OutlinedTextField(
                            value = clientIdInput,
                            onValueChange = { clientIdInput = it },
                            label = { Text("Google Client ID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = clientSecretInput,
                            onValueChange = { clientSecretInput = it },
                            label = { Text("Google Client Secret") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = redirectUriInput,
                            onValueChange = { redirectUriInput = it },
                            label = { Text("Redirect URI") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    val res = repository.updateCalendarConfig(
                                        CalendarConfigRequest(
                                            googleClientId = clientIdInput.trim(),
                                            googleClientSecret = clientSecretInput.trim().ifBlank { null },
                                            googleRedirectUri = redirectUriInput.trim()
                                        )
                                    )
                                    if (res is ApiResult.Success) {
                                        clientSecretInput = ""
                                        feedbackMsg = AppStrings.t("Đã lưu OAuth Credentials!")
                                    } else if (res is ApiResult.Error) {
                                        feedbackMsg = res.message
                                    }
                                }
                            }
                        ) {
                            Text(AppStrings.t("Lưu Credentials"))
                        }
                    }
                }
            }

            // Private iCal (.ics) Card
            ElevatedCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = AppStrings.t("Link Private iCal (.ics)"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = icalUrlInput,
                        onValueChange = { icalUrlInput = it },
                        label = { Text("https://calendar.google.com/calendar/ical/.../basic.ics") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                val res = repository.updateCalendarConfig(
                                    CalendarConfigRequest(icalUrl = icalUrlInput.trim())
                                )
                                feedbackMsg = when (res) {
                                    is ApiResult.Success -> AppStrings.t("Đã lưu & đồng bộ iCal!")
                                    is ApiResult.Error -> res.message
                                }
                            }
                        }
                    ) {
                        Text(AppStrings.t("Lưu & Đồng bộ iCal"))
                    }
                }
            }

            // Create Event Card
            ElevatedCard(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = AppStrings.t("Thêm sự kiện (cần Google OAuth)"),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text(AppStrings.t("Tên sự kiện")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = newDate,
                            onValueChange = { newDate = it },
                            label = { Text("YYYY-MM-DD") },
                            singleLine = true,
                            modifier = Modifier.weight(1.3f)
                        )
                        if (!newAllDay) {
                            OutlinedTextField(
                                value = newStart,
                                onValueChange = { newStart = it },
                                label = { Text("Start") },
                                singleLine = true,
                                modifier = Modifier.weight(0.8f)
                            )
                            OutlinedTextField(
                                value = newEnd,
                                onValueChange = { newEnd = it },
                                label = { Text("End") },
                                singleLine = true,
                                modifier = Modifier.weight(0.8f)
                            )
                        }
                    }
                    OutlinedTextField(
                        value = newLocation,
                        onValueChange = { newLocation = it },
                        label = { Text(AppStrings.t("Địa điểm")) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = newAllDay, onCheckedChange = { newAllDay = it })
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(AppStrings.t("Cả ngày"))
                        }
                        Button(
                            enabled = newTitle.isNotBlank(),
                            onClick = {
                                scope.launch {
                                    val res = repository.createCalendarEvent(
                                        CreateCalendarEventRequest(
                                            title = newTitle.trim(),
                                            date = newDate.trim(),
                                            startTime = newStart.trim(),
                                            endTime = newEnd.trim(),
                                            allDay = newAllDay,
                                            location = newLocation.trim()
                                        )
                                    )
                                    if (res is ApiResult.Success) {
                                        newTitle = ""
                                        newLocation = ""
                                        feedbackMsg = AppStrings.t("Đã thêm sự kiện lên Google Calendar!")
                                    } else if (res is ApiResult.Error) {
                                        feedbackMsg = res.message
                                    }
                                }
                            }
                        ) {
                            Text(AppStrings.t("Thêm"))
                        }
                    }
                }
            }

            // Upcoming Events List
            Text(
                text = AppStrings.t("Sự kiện sắp tới (30 ngày)"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (calState.events.isEmpty()) {
                ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = AppStrings.t("Chưa có sự kiện sắp tới."),
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                calState.events.forEach { ev ->
                    ElevatedCard(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(ev.title, fontWeight = FontWeight.SemiBold)
                                val timeStr = if (ev.allDay) AppStrings.t("Cả ngày") else if (ev.endTime.isNotBlank()) "${ev.startTime}–${ev.endTime}" else ev.startTime
                                Text(
                                    text = buildString {
                                        append("${ev.date} • $timeStr")
                                        if (ev.location.isNotBlank()) append(" • @ ${ev.location}")
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (ev.canDelete) {
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            repository.deleteCalendarEvent(ev.id, ev.calendarId)
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete")
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
