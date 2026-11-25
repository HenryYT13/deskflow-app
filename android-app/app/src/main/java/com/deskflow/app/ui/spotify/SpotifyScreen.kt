package com.deskflow.app.ui.spotify

import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.deskflow.app.R
import com.deskflow.app.data.local.SecurePreferences
import com.deskflow.app.data.repository.ApiResult
import com.deskflow.app.data.repository.FocusRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import com.deskflow.app.ui.i18n.AppStrings

private val imageHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .build()

private val artworkMemoryCache = mutableMapOf<String, ImageBitmap>()

@Composable
fun AsyncTrackArtwork(
    coverUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    var loadedBitmap by remember(coverUrl) {
        mutableStateOf(coverUrl?.let { artworkMemoryCache[it] })
    }
    var isLoading by remember(coverUrl) { mutableStateOf(!coverUrl.isNullOrBlank() && loadedBitmap == null) }
    var hasError by remember(coverUrl) { mutableStateOf(false) }

    LaunchedEffect(coverUrl) {
        if (!coverUrl.isNullOrBlank() && loadedBitmap == null) {
            isLoading = true
            hasError = false
            withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder().url(coverUrl).build()
                    val response = imageHttpClient.newCall(request).execute()
                    if (response.isSuccessful && response.body != null) {
                        val bytes = response.body!!.bytes()
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bitmap != null) {
                            val imgBitmap = bitmap.asImageBitmap()
                            artworkMemoryCache[coverUrl] = imgBitmap
                            withContext(Dispatchers.Main) {
                                loadedBitmap = imgBitmap
                                isLoading = false
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                hasError = true
                                isLoading = false
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            hasError = true
                            isLoading = false
                        }
                    }
                } catch (_: Exception) {
                    withContext(Dispatchers.Main) {
                        hasError = true
                        isLoading = false
                    }
                }
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 8.dp,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when {
                loadedBitmap != null -> {
                    Image(
                        bitmap = loadedBitmap!!,
                        contentDescription = contentDescription,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp,
                        color = Color(0xFF1DB954)
                    )
                }
                else -> {
                    // Fallback artwork with Spotify branding
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1DB954)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = contentDescription,
                            tint = Color.White,
                            modifier = Modifier.size(54.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyScreen(
    repository: FocusRepository,
    securePreferences: SecurePreferences,
    onOpenScreenSwitcher: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val spotifyState by repository.spotifyState.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var sliderVolume by remember { mutableFloatStateOf(50f) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }

    // Authoritative state tracking and local interpolation
    var localProgressMs by remember { mutableLongStateOf(spotifyState.progressMs) }
    var lastSyncTimestamp by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var previousTrack by remember { mutableStateOf(spotifyState.track) }

    // 1. Authoritative real-time polling loop every 2.5 seconds
    LaunchedEffect(Unit) {
        while (isActive) {
            repository.refreshSpotify()
            delay(2500)
        }
    }

    // 2. Synchronize baseline when spotifyState arrives from backend
    LaunchedEffect(spotifyState.track, spotifyState.progressMs, spotifyState.isPlaying) {
        if (spotifyState.track != previousTrack) {
            previousTrack = spotifyState.track
            artworkMemoryCache.remove(spotifyState.coverUrl ?: "")
        }
        localProgressMs = spotifyState.progressMs
        lastSyncTimestamp = SystemClock.elapsedRealtime()
    }

    // 3. Smooth local progress interpolator (250ms ticker) while song is playing
    LaunchedEffect(spotifyState.isPlaying, spotifyState.durationMs) {
        if (spotifyState.isPlaying) {
            while (isActive) {
                val elapsed = SystemClock.elapsedRealtime() - lastSyncTimestamp
                val duration = if (spotifyState.durationMs > 0) spotifyState.durationMs else 1L
                localProgressMs = (spotifyState.progressMs + elapsed).coerceAtMost(duration)
                delay(250)
            }
        } else {
            localProgressMs = spotifyState.progressMs
        }
    }

    // Synchronize slider with real remote backend volume when user is not actively dragging
    LaunchedEffect(spotifyState.progressPct, spotifyState.connected) {
        if (!isDraggingSlider && spotifyState.connected) {
            sliderVolume = spotifyState.progressPct.toFloat().coerceIn(0f, 100f)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.nav_spotify),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (spotifyState.connected && spotifyState.isPlaying)
                                stringResource(R.string.spotify_now_playing)
                            else if (spotifyState.connected)
                                stringResource(R.string.spotify_not_playing)
                            else
                                stringResource(R.string.spotify_offline),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (spotifyState.isPlaying)
                                Color(0xFF1DB954)
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                isRefreshing = true
                                repository.refreshSpotify()
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
        if (!securePreferences.hasScope("spotify")) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
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
                            text = "Scope 'spotify' Required",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = stringResource(R.string.scope_spotify_required),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Spacer(modifier = Modifier.height(6.dp))

            // ================= REAL ALBUM ARTWORK =================
            AsyncTrackArtwork(
                coverUrl = spotifyState.coverUrl,
                contentDescription = spotifyState.track,
                modifier = Modifier.size(240.dp)
            )

            // Track details
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Text(
                    text = spotifyState.track ?: stringResource(R.string.spotify_offline),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = spotifyState.artist ?: "DeskFlow Station",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!spotifyState.album.isNullOrBlank()) {
                    Text(
                        text = spotifyState.album!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // ================= REAL-TIME PROGRESS BAR & TIMESTAMPS =================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val totalDuration = if (spotifyState.durationMs > 0) spotifyState.durationMs else 1L
                val progressFraction = (localProgressMs.toFloat() / totalDuration).coerceIn(0f, 1f)

                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Color(0xFF1DB954),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatSpotifyDuration(localProgressMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = formatSpotifyDuration(spotifyState.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ================= 5-LINE SYNCHRONIZED LYRICS CARD =================
            val windowLines: List<String> = remember(spotifyState.lyricsWindow, spotifyState.currentLyric, spotifyState.nextLyric) {
                when {
                    spotifyState.lyricsWindow.isNotEmpty() -> {
                        // Pad to ensure exactly 5 lines if fewer
                        val list = spotifyState.lyricsWindow.toMutableList()
                        while (list.size < 5) list.add("")
                        list.take(5)
                    }
                    !spotifyState.currentLyric.isNullOrBlank() -> {
                        listOf(
                            "",
                            "",
                            spotifyState.currentLyric ?: "",
                            spotifyState.nextLyric ?: "",
                            ""
                        )
                    }
                    else -> emptyList()
                }
            }

            val activeIndex = if (spotifyState.lyricsWindow.isNotEmpty()) {
                spotifyState.activeLyricIndex.coerceIn(0, 4)
            } else {
                2
            }

            if (windowLines.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FormatQuote,
                                contentDescription = null,
                                tint = Color(0xFF1DB954),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = AppStrings.t("LỜI BÀI HÁT ĐỒNG BỘ"),
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1DB954)
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        windowLines.forEachIndexed { idx, lineText ->
                            val isActive = idx == activeIndex
                            val isBlank = lineText.isBlank()

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (isActive && !isBlank) {
                                            Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color(0xFF1DB954).copy(alpha = 0.12f))
                                                .padding(vertical = 6.dp, horizontal = 12.dp)
                                        } else {
                                            Modifier.padding(vertical = 2.dp, horizontal = 12.dp)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isBlank) " " else lineText,
                                    style = when {
                                        isActive -> MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                        idx == activeIndex - 1 || idx == activeIndex + 1 -> MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = 13.5.sp
                                        )
                                        else -> MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 12.sp
                                        )
                                    },
                                    color = when {
                                        isActive -> Color(0xFF1DB954)
                                        idx == activeIndex - 1 || idx == activeIndex + 1 -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f)
                                    },
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            } else {
                // Informative fallback when lyrics are unavailable
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = AppStrings.t("Chưa có lời bài hát đồng bộ"),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (spotifyState.isPlaying) AppStrings.t("Bài hát này không có dữ liệu lời đồng bộ từ Spotify.") else AppStrings.t("Phát nhạc trên Spotify để bắt đầu đồng bộ lời bài hát."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ================= PLAYBACK CONTROLS =================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous Track
                FilledTonalIconButton(
                    onClick = {
                        coroutineScope.launch {
                            repository.controlSpotify("previous")
                            delay(350)
                            repository.refreshSpotify()
                        }
                    },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Previous",
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Play / Pause Toggle Button
                FilledIconButton(
                    onClick = {
                        coroutineScope.launch {
                            val cmd = if (spotifyState.isPlaying) "pause" else "play"
                            repository.controlSpotify(cmd)
                            delay(350)
                            repository.refreshSpotify()
                        }
                    },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color(0xFF1DB954),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        imageVector = if (spotifyState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (spotifyState.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(44.dp)
                    )
                }

                // Next Track
                FilledTonalIconButton(
                    onClick = {
                        coroutineScope.launch {
                            repository.controlSpotify("next")
                            delay(350)
                            repository.refreshSpotify()
                        }
                    },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Next",
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // ================= 1% ACCURATE VOLUME SLIDER =================
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = when {
                                    sliderVolume.roundToInt() > 50 -> Icons.Default.VolumeUp
                                    sliderVolume.roundToInt() > 0 -> Icons.Default.VolumeDown
                                    else -> Icons.Default.VolumeMute
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = AppStrings.t("Âm lượng Spotify"),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Exact 1% integer display (e.g. 42%)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = "${sliderVolume.roundToInt()}%",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Slider(
                        value = sliderVolume,
                        onValueChange = {
                            sliderVolume = it
                            isDraggingSlider = true
                        },
                        onValueChangeFinished = {
                            isDraggingSlider = false
                            val vol = sliderVolume.roundToInt().coerceIn(0, 100)
                            coroutineScope.launch {
                                repository.setSpotifyVolume(vol)
                            }
                        },
                        valueRange = 0f..100f,
                        steps = 0,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF1DB954),
                            activeTrackColor = Color(0xFF1DB954),
                            inactiveTrackColor = Color(0xFF1DB954).copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Quick percentage chips for convenience
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf(0, 25, 50, 75, 100).forEach { pct ->
                            FilterChip(
                                selected = sliderVolume.roundToInt() == pct,
                                onClick = {
                                    sliderVolume = pct.toFloat()
                                    coroutineScope.launch {
                                        repository.setSpotifyVolume(pct)
                                    }
                                },
                                label = { Text("$pct%", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }

            // #54 Dynamic BPM LED Pulse, #58/#138 Wrapped Mini, #132 Queue, #140 Devices, #137 Share Card
            val ctx = androidx.compose.ui.platform.LocalContext.current
            var statsState by remember { mutableStateOf<com.deskflow.app.data.model.SpotifyStatsResponse?>(null) }
            var queueItems by remember { mutableStateOf<List<com.deskflow.app.data.model.SpotifyQueueItemDto>>(emptyList()) }
            var spDevices by remember { mutableStateOf<List<com.deskflow.app.data.model.SpotifyDeviceDto>>(emptyList()) }
            var bpmSyncOn by remember { mutableStateOf(false) }
            LaunchedEffect(spotifyState.track) {
                val r = repository.getSpotifyStats()
                if (r is ApiResult.Success) statsState = r.data
                val q = repository.getSpotifyQueue()
                if (q is ApiResult.Success) queueItems = q.data.queue
                val d = repository.getSpotifyDevices()
                if (d is ApiResult.Success) spDevices = d.data.devices
            }

            Spacer(modifier = Modifier.height(14.dp))
            ElevatedCard(shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (spotifyState.bpm != null) "BPM: ${spotifyState.bpm?.roundToInt()}" else "BPM LED Sync (#54)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(
                                            android.content.Intent.EXTRA_TEXT,
                                            "🎵 Now Playing on DeskFlow: ${spotifyState.track ?: ""} — ${spotifyState.artist ?: ""}\n${spotifyState.currentLyric ?: ""}"
                                        )
                                    }
                                    ctx.startActivity(android.content.Intent.createChooser(send, "Share Now Playing"))
                                }
                            ) {
                                Text("Share (#137)", style = MaterialTheme.typography.labelSmall)
                            }
                            FilterChip(
                                selected = bpmSyncOn,
                                onClick = {
                                    val next = !bpmSyncOn
                                    bpmSyncOn = next
                                    coroutineScope.launch { repository.setBpmSync(next) }
                                },
                                label = { Text(if (bpmSyncOn) "BPM ON" else "BPM LED") }
                            )
                        }
                    }

                    if (spDevices.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Text("🔊 Thiết bị Spotify Connect (#140):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            spDevices.take(3).forEach { dev ->
                                FilterChip(
                                    selected = dev.isActive,
                                    onClick = {
                                        coroutineScope.launch {
                                            repository.transferSpotifyDevice(dev.id)
                                            val dr = repository.getSpotifyDevices()
                                            if (dr is ApiResult.Success) spDevices = dr.data.devices
                                        }
                                    },
                                    label = { Text(dev.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }

                    if (queueItems.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Text("📜 Tiếp theo trong Hàng chờ (#132):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        queueItems.take(4).forEachIndexed { idx, qi ->
                            Text("${idx + 1}. ${qi.name} — ${qi.artist}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    Text(
                        text = "📊 Wrapped Mini (#58/#138): ${statsState?.totalPlays ?: 0} lượt • ~${statsState?.estimatedMinutes ?: 0} phút",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    statsState?.topTracks?.take(5)?.forEach { item ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(item.name, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text("${item.count}x", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private fun formatSpotifyDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
