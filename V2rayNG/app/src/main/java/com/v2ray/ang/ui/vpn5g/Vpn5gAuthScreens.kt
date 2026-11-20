package com.v2ray.ang.ui.vpn5g

import android.util.Patterns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R

/**
 * Registration / login and password-recovery screens.
 *
 * UI-ONLY in v1: there is no backend yet (Supabase Auth lands in v2), so the
 * send/confirm buttons show an honest "coming soon" notice instead of
 * pretending to work. Validation is real: email format and a 6-digit code.
 *
 * Motion: the same language as the home screen — animated gradient
 * background (from the activity), pulsing logo, and staggered springy entry
 * of logo, fields and buttons (AnimatedVisibility, spring dampingRatio 0.7,
 * ~100ms stagger). Purely presentational; nothing here gates sync/connect.
 */
@Composable
fun Vpn5gAuthScreen(onBack: () -> Unit) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    var mode by remember { mutableStateOf(Vpn5gAuthMode.REGISTER) }
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }
    var codeError by remember { mutableStateOf<String?>(null) }
    var showComingSoon by remember { mutableStateOf(false) }

    val invalidEmailText = stringResource(R.string.vpn5g_auth_invalid_email)
    val invalidCodeText = stringResource(R.string.vpn5g_auth_invalid_code)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Vpn5gStaggeredItem(index = 0, visible = entered) {
            Vpn5gPulsingLogo(size = 96.dp)
        }
        Vpn5gStaggeredItem(index = 1, visible = entered) {
            Text(
                text = stringResource(
                    if (mode == Vpn5gAuthMode.REGISTER) R.string.vpn5g_auth_title
                    else R.string.vpn5g_auth_recover_title
                ),
                color = Vpn5gOnDark,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        if (mode == Vpn5gAuthMode.RECOVER) {
            Vpn5gStaggeredItem(index = 2, visible = entered) {
                Text(
                    text = stringResource(R.string.vpn5g_auth_recover_body),
                    color = Vpn5gOnDarkMuted,
                    fontSize = 14.sp,
                )
            }
        }
        Vpn5gStaggeredItem(index = 3, visible = entered) {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it; emailError = null; showComingSoon = false },
                label = { Text(stringResource(R.string.vpn5g_auth_email)) },
                placeholder = { Text(stringResource(R.string.vpn5g_auth_email_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                isError = emailError != null,
                supportingText = emailError?.let { { Text(it) } },
                colors = vpn5gTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Vpn5gStaggeredItem(index = 4, visible = entered) {
            Button(
                onClick = {
                    if (!Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
                        emailError = invalidEmailText
                    } else {
                        emailError = null
                        // v1: no backend — honest notice, no fake send.
                        showComingSoon = true
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Vpn5gAccent,
                    contentColor = Color.White,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.vpn5g_auth_send_code), fontSize = 16.sp)
            }
        }
        Vpn5gStaggeredItem(index = 5, visible = entered) {
            OutlinedTextField(
                value = code,
                onValueChange = {
                    code = it.filter { ch -> ch.isDigit() }.take(6)
                    codeError = null
                    showComingSoon = false
                },
                label = { Text(stringResource(R.string.vpn5g_auth_code)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = codeError != null,
                supportingText = codeError?.let { { Text(it) } },
                colors = vpn5gTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Vpn5gStaggeredItem(index = 6, visible = entered) {
            Button(
                onClick = {
                    if (!code.matches(Regex("\\d{6}"))) {
                        codeError = invalidCodeText
                    } else {
                        codeError = null
                        // v1: no backend — honest notice, no fake confirm.
                        showComingSoon = true
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Vpn5gAccent,
                    contentColor = Color.White,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.vpn5g_auth_confirm), fontSize = 16.sp)
            }
        }
        if (showComingSoon) {
            Vpn5gStaggeredItem(index = 7, visible = entered) {
                Text(
                    text = stringResource(R.string.vpn5g_auth_coming_soon),
                    color = Vpn5gAccentLight,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Vpn5gStaggeredItem(index = 8, visible = entered) {
            TextButton(
                onClick = {
                    mode = if (mode == Vpn5gAuthMode.REGISTER) Vpn5gAuthMode.RECOVER
                    else Vpn5gAuthMode.REGISTER
                    showComingSoon = false
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(
                        if (mode == Vpn5gAuthMode.REGISTER) R.string.vpn5g_auth_forgot
                        else R.string.vpn5g_auth_title
                    ),
                    color = Vpn5gAccentLight,
                    fontSize = 14.sp,
                )
            }
        }
        Vpn5gStaggeredItem(index = 9, visible = entered) {
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.vpn5g_nav_subscription),
                    color = Vpn5gAccentLight,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

private enum class Vpn5gAuthMode { REGISTER, RECOVER }

@Composable
private fun vpn5gTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Vpn5gOnDark,
    unfocusedTextColor = Vpn5gOnDark,
    focusedLabelColor = Vpn5gAccentLight,
    unfocusedLabelColor = Vpn5gOnDarkMuted,
    focusedBorderColor = Vpn5gAccentLight,
    unfocusedBorderColor = Vpn5gOnDarkMuted,
    cursorColor = Vpn5gAccentLight,
    focusedPlaceholderColor = Vpn5gOnDarkMuted,
    unfocusedPlaceholderColor = Vpn5gOnDarkMuted,
)
