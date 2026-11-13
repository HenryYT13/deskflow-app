package com.deskflow.app.ui.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.deskflow.app.R
import androidx.activity.compose.rememberLauncherForActivityResult
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.deskflow.app.data.repository.ApiResult
import com.deskflow.app.data.repository.FocusRepository
import kotlinx.coroutines.launch
import com.deskflow.app.ui.i18n.AppStrings

@Composable
fun PairingScreen(
    repository: FocusRepository,
    onPairedSuccess: () -> Unit
) {
    var pairingCode by remember { mutableStateOf("") }
    var deviceName by remember { mutableStateOf(android.os.Build.MODEL ?: "Android Device") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val raw = result.contents ?: return@rememberLauncherForActivityResult
        if (raw.contains("session=", ignoreCase = true) || raw.contains("session_id=", ignoreCase = true)) {
            errorMessage = context.getString(R.string.pair_session_desktop_qr_hint)
            return@rememberLauncherForActivityResult
        }
        val code = Regex("(?i)code=([A-Za-z0-9]{8})").find(raw)?.groupValues?.get(1)
            ?: Regex("(?<![A-Za-z0-9])[A-Za-z0-9]{8}(?![A-Za-z0-9])").find(raw)?.value
        if (code != null) {
            pairingCode = code.uppercase()
            errorMessage = null
        } else {
            errorMessage = AppStrings.t("QR không hợp lệ / Invalid QR")
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.QrCode,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.pair_title),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.pair_desc),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedButton(
                onClick = {
                    scanLauncher.launch(
                        ScanOptions().apply {
                            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                            setPrompt(context.getString(R.string.pair_scan_qr_prompt))
                            setBeepEnabled(false)
                            setOrientationLocked(false)
                            setBarcodeImageEnabled(false)
                        }
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.pair_scan_qr))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Pairing Code Input Field
            OutlinedTextField(
                value = pairingCode,
                onValueChange = { input ->
                    if (input.length <= 8) {
                        pairingCode = input.uppercase().filter { it.isLetterOrDigit() }
                        errorMessage = null
                    }
                },
                label = { Text(stringResource(R.string.pairing_code_label)) },
                placeholder = { Text(stringResource(R.string.pairing_code_placeholder)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Next
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Device Name Input Field
            OutlinedTextField(
                value = deviceName,
                onValueChange = { deviceName = it },
                label = { Text(stringResource(R.string.device_name_label)) },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.Smartphone, contentDescription = null)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    if (pairingCode.length == 8) {
                        // Submit pairing
                    }
                })
            )

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (pairingCode.length != 8) {
                        errorMessage = AppStrings.t("Vui lòng nhập đủ 8 ký tự mã ghép đôi.")
                        return@Button
                    }
                    isLoading = true
                    errorMessage = null
                    coroutineScope.launch {
                        val result = repository.pairDevice(pairingCode, deviceName)
                        isLoading = false
                        when (result) {
                            is ApiResult.Success -> {
                                onPairedSuccess()
                            }
                            is ApiResult.Error -> {
                                errorMessage = when (result.code) {
                                    "pair_code_invalid" -> AppStrings.t("Mã ghép đôi không hợp lệ, đã hết hạn hoặc đã sử dụng.")
                                    "rate_limited" -> AppStrings.tpl("Đã thử quá số lần cho phép. Vui lòng chờ ${result.retryAfter ?: 60}s.")
                                    "network_error" -> AppStrings.tpl("Không thể kết nối đến máy chủ DeskFlow: ${result.message}")
                                    else -> AppStrings.tpl("Ghép đôi thất bại: ${result.code}")
                                }
                            }
                        }
                    }
                },
                enabled = !isLoading && pairingCode.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = stringResource(R.string.btn_pair),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}
