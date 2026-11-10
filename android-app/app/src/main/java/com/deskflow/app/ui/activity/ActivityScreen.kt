package com.deskflow.app.ui.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.deskflow.app.R
import com.deskflow.app.data.local.ActivityStore
import com.deskflow.app.domain.model.ActivityType
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ActivityScreen(
    activityStore: ActivityStore,
    onOpenScreenSwitcher: () -> Unit = {}
) {
    val events by activityStore.events.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.activity_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        if (events.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.activity_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(events, key = { it.id }) { event ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val icon = when (event.type) {
                                ActivityType.FOCUS_STARTED -> Icons.Default.PlayArrow
                                ActivityType.FOCUS_STOPPED -> Icons.Default.CheckCircle
                                ActivityType.WEBSITE_BLOCKED -> Icons.Default.Block
                                ActivityType.DEVICE_PAIRED -> Icons.Default.PhonelinkRing
                                ActivityType.CHALLENGE_FAILED -> Icons.Default.Warning
                            }
                            val iconTint = when (event.type) {
                                ActivityType.FOCUS_STARTED -> MaterialTheme.colorScheme.primary
                                ActivityType.FOCUS_STOPPED -> MaterialTheme.colorScheme.tertiary
                                ActivityType.WEBSITE_BLOCKED -> MaterialTheme.colorScheme.error
                                ActivityType.DEVICE_PAIRED -> MaterialTheme.colorScheme.secondary
                                ActivityType.CHALLENGE_FAILED -> MaterialTheme.colorScheme.error
                            }

                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(iconTint.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = iconTint
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = event.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (!event.details.isNullOrBlank()) {
                                    Text(
                                        text = event.details,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(event.timestamp))
                            Text(
                                text = timeStr,
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
