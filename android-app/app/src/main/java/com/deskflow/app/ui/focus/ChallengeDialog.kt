package com.deskflow.app.ui.focus

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.deskflow.app.R
import com.deskflow.app.data.repository.ApiResult
import com.deskflow.app.data.repository.FocusRepository
import com.deskflow.app.domain.model.ChallengeInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ChallengeDialog(
    initialChallenge: ChallengeInfo,
    repository: FocusRepository,
    onDismiss: () -> Unit,
    onSuccessUnlocked: () -> Unit
) {
    var currentChallenge by remember { mutableStateOf(initialChallenge) }
    var answerText by remember { mutableStateOf("") }
    var remainingSeconds by remember { mutableStateOf(initialChallenge.expiresInSeconds) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var cooldownSeconds by remember { mutableStateOf<Int?>(null) }

    val coroutineScope = rememberCoroutineScope()

    // Countdown Timer Effect
    LaunchedEffect(currentChallenge.challengeId) {
        remainingSeconds = currentChallenge.expiresInSeconds
        while (remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds--
        }
        errorMessage = "Thử thách đã hết hạn. Vui lòng lấy thử thách mới."
    }

    // Cooldown Countdown Effect
    LaunchedEffect(cooldownSeconds) {
        val cd = cooldownSeconds ?: return@LaunchedEffect
        var left = cd
        while (left > 0) {
            delay(1000L)
            left--
            cooldownSeconds = left
        }
        cooldownSeconds = null
        errorMessage = null
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Calculate,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(R.string.challenge_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = stringResource(R.string.challenge_desc),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Math Question Box
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = currentChallenge.question.ifBlank { "Đang tải bài toán..." },
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 24.sp
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Expiry Countdown Badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (remainingSeconds <= 15) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.challenge_expires_in, remainingSeconds),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (remainingSeconds <= 15) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Integer Input
                OutlinedTextField(
                    value = answerText,
                    onValueChange = { input ->
                        // Only allow integer characters (+, -, digits)
                        if (input.matches(Regex("^-?\\d*$"))) {
                            answerText = input
                            errorMessage = null
                        }
                    },
                    label = { Text(stringResource(R.string.challenge_question_label)) },
                    placeholder = { Text(stringResource(R.string.challenge_answer_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Submit Button
                Button(
                    onClick = {
                        val ans = answerText.toIntOrNull()
                        if (ans == null) {
                            errorMessage = "Vui lòng nhập số nguyên hợp lệ."
                            return@Button
                        }

                        isSubmitting = true
                        errorMessage = null

                        coroutineScope.launch {
                            val res = repository.verifyChallenge(currentChallenge.challengeId, ans)
                            isSubmitting = false
                            when (res) {
                                is ApiResult.Success -> {
                                    if (res.data.correct) {
                                        onSuccessUnlocked()
                                    } else {
                                        errorMessage = res.data.message ?: "Đáp án chưa đúng. Đã đổi bài toán mới."
                                        answerText = ""
                                        res.data.newChallenge?.let { nc ->
                                            currentChallenge = ChallengeInfo(
                                                challengeId = nc.challengeId,
                                                question = nc.question,
                                                language = nc.language,
                                                expiresAt = nc.expiresAt,
                                                expiresInSeconds = nc.expiresIn
                                            )
                                        }
                                    }
                                }
                                is ApiResult.Error -> {
                                    if (res.code == "cooldown") {
                                        cooldownSeconds = res.retryAfter ?: 60
                                        errorMessage = "Đã nhập sai quá 5 lần. Chờ ${cooldownSeconds}s."
                                    } else {
                                        errorMessage = "Lỗi xác thực: ${res.code}"
                                    }
                                }
                            }
                        }
                    },
                    enabled = !isSubmitting && answerText.isNotBlank() && remainingSeconds > 0 && cooldownSeconds == null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.btn_submit_answer))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Request New Challenge Button
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            val newCh = repository.requestChallenge(forceNew = true)
                            if (newCh is ApiResult.Success) {
                                currentChallenge = newCh.data
                                answerText = ""
                                errorMessage = null
                            }
                        }
                    },
                    enabled = !isSubmitting && cooldownSeconds == null
                ) {
                    Text(stringResource(R.string.btn_new_challenge))
                }
            }
        }
    }
}
