package com.v2ray.ang.ui.vpn5g

import android.app.Application
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.v2ray.ang.R
import com.v2ray.ang.handler.vpn5g.Vpn5gOtpPolicy
import com.v2ray.ang.handler.vpn5g.Vpn5gRecoveryLink
import kotlinx.coroutines.delay

/**
 * Registration / login screens, wired to [Vpn5gAuthViewModel] /
 * [com.v2ray.ang.handler.vpn5g.Vpn5gAuthService] (Supabase Auth,
 * GoTrue REST) — four flows (v1.1.8):
 *
 *  - LOGIN: email + password → signed in directly.
 *  - REGISTER: email + password (+ confirmation) → signed in
 *    immediately while "Confirm email" is OFF; with it ON the user
 *    falls back to the OTP code screen.
 *  - OTP: email → real 6-digit code → code entry → signed in.
 *  - RECOVER: email → recovery LINK → tap the link, return to the
 *    app, enter a new password → signed in.
 *
 * Registration accepts Gmail only. When the Supabase project is not
 * configured the screen says so honestly instead of pretending to
 * send codes or links.
 *
 * Motion: the same language as the home screen — animated gradient
 * background (from the activity), pulsing logo, and staggered springy entry.
 * Purely presentational; nothing here gates sync/connect.
 *
 * @param recoveryToken a parsed recovery deep link
 * (`vpn5g://auth/callback`, type=recovery), delivered by
 * Vpn5gMainActivity when the user returns from the emailed link.
 * It is consumed exactly once via [onRecoveryTokenConsumed].
 */
@Composable
fun Vpn5gAuthScreen(
    onBack: () -> Unit,
    recoveryToken: Vpn5gRecoveryLink.Parsed? = null,
    onRecoveryTokenConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val authViewModel: Vpn5gAuthViewModel = viewModel(
        factory = Vpn5gAuthViewModel.Factory(context.applicationContext as Application),
    )
    val state by authViewModel.uiState.collectAsStateWithLifecycle()

    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    // Recovery deep link: open the new-password screen exactly once.
    LaunchedEffect(recoveryToken) {
        if (recoveryToken != null) {
            authViewModel.startRecovery(recoveryToken)
            onRecoveryTokenConsumed()
        }
    }

    // Resend countdown (v1.1.5 §7): transient presentation state only.
    // The ViewModel owns the last-send timestamp; the screen only counts
    // down from it, one second at a time, until a resend is allowed.
    var resendWaitSeconds by remember { mutableStateOf(0L) }
    LaunchedEffect(state.lastCodeSentMs, state.phase) {
        while (true) {
            val wait = Vpn5gOtpPolicy.resendWaitSeconds(
                state.lastCodeSentMs, System.currentTimeMillis()
            )
            resendWaitSeconds = wait
            if (wait <= 0L) break
            delay(1_000L)
        }
    }

    // Password visibility is transient presentation state only.
    var passwordVisible by remember { mutableStateOf(false) }

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
                    when {
                        state.phase == Vpn5gAuthPhase.NEW_PASSWORD ||
                            state.phase == Vpn5gAuthPhase.UPDATING_PASSWORD ->
                            R.string.vpn5g_auth_new_password_title
                        state.mode == Vpn5gAuthMode.LOGIN -> R.string.vpn5g_auth_title
                        state.mode == Vpn5gAuthMode.REGISTER -> R.string.vpn5g_auth_register_title
                        state.mode == Vpn5gAuthMode.OTP -> R.string.vpn5g_auth_otp_title
                        else -> R.string.vpn5g_auth_recover_title
                    }
                ),
                color = Vpn5gOnDark,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        if (!state.backendConfigured) {
            Vpn5gStaggeredItem(index = 2, visible = entered) {
                Text(
                    text = stringResource(R.string.vpn5g_auth_coming_soon),
                    color = Vpn5gAccentLight,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (state.phase == Vpn5gAuthPhase.DONE) {
            Vpn5gStaggeredItem(index = 3, visible = entered) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.vpn5g_auth_done),
                        color = Vpn5gOnDark,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    state.signedInEmail?.let { email ->
                        Spacer(Modifier.height(4.dp))
                        Text(text = email, color = Vpn5gOnDarkMuted, fontSize = 14.sp)
                    }
                }
            }
            Vpn5gStaggeredItem(index = 4, visible = entered) {
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Vpn5gAccent,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.vpn5g_auth_back_to_profile), fontSize = 16.sp)
                }
            }
            return@Column
        }

        val busy = state.phase == Vpn5gAuthPhase.SENDING_CODE ||
            state.phase == Vpn5gAuthPhase.VERIFYING ||
            state.phase == Vpn5gAuthPhase.SIGNING_IN ||
            state.phase == Vpn5gAuthPhase.UPDATING_PASSWORD
        val isNewPassword = state.phase == Vpn5gAuthPhase.NEW_PASSWORD ||
            state.phase == Vpn5gAuthPhase.UPDATING_PASSWORD
        val needsPassword = state.mode == Vpn5gAuthMode.LOGIN ||
            state.mode == Vpn5gAuthMode.REGISTER || isNewPassword
        val needsPasswordConfirm = state.mode == Vpn5gAuthMode.REGISTER || isNewPassword

        // Email field (all modes, Gmail only). Shown on the
        // new-password screen too: the recovery link may arrive
        // without an email typed, and the session needs the address.
        Vpn5gStaggeredItem(index = 3, visible = entered) {
            OutlinedTextField(
                value = state.email,
                onValueChange = { authViewModel.setEmail(it) },
                label = { Text(stringResource(R.string.vpn5g_auth_email)) },
                placeholder = { Text(stringResource(R.string.vpn5g_auth_email_hint)) },
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                isError = state.emailErrorRes != null,
                supportingText = state.emailErrorRes?.let { { Text(stringResource(it)) } },
                colors = vpn5gTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Password field (password login, signup, new password).
        if (needsPassword) {
            Vpn5gStaggeredItem(index = 4, visible = entered) {
                OutlinedTextField(
                    value = state.password,
                    onValueChange = { authViewModel.setPassword(it) },
                    label = { Text(stringResource(R.string.vpn5g_auth_password)) },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        TextButton(onClick = { passwordVisible = !passwordVisible }) {
                            Text(
                                text = stringResource(
                                    if (passwordVisible) {
                                        R.string.vpn5g_auth_hide_password
                                    } else {
                                        R.string.vpn5g_auth_show_password
                                    }
                                ),
                                color = Vpn5gAccentLight,
                                fontSize = 13.sp,
                            )
                        }
                    },
                    isError = state.passwordErrorRes != null,
                    supportingText = state.passwordErrorRes?.let { { Text(stringResource(it)) } },
                    colors = vpn5gTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Password confirmation (signup, new password).
        if (needsPasswordConfirm) {
            Vpn5gStaggeredItem(index = 5, visible = entered) {
                OutlinedTextField(
                    value = state.passwordConfirm,
                    onValueChange = { authViewModel.setPasswordConfirm(it) },
                    label = { Text(stringResource(R.string.vpn5g_auth_password_confirm)) },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    isError = state.passwordErrorRes != null,
                    colors = vpn5gTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Primary action: one guarded entry point per mode. The button
        // is disabled while its request is in flight, so a double tap
        // can never fire two requests (v1.1.8 "Too many requests" fix).
        val primaryLabelRes = when {
            isNewPassword && state.phase == Vpn5gAuthPhase.UPDATING_PASSWORD ->
                R.string.vpn5g_auth_updating_password
            isNewPassword -> R.string.vpn5g_auth_new_password_button
            state.mode == Vpn5gAuthMode.LOGIN && state.phase == Vpn5gAuthPhase.SIGNING_IN ->
                R.string.vpn5g_auth_signing_in
            state.mode == Vpn5gAuthMode.LOGIN -> R.string.vpn5g_auth_login_button
            state.mode == Vpn5gAuthMode.REGISTER && state.phase == Vpn5gAuthPhase.SIGNING_IN ->
                R.string.vpn5g_auth_signing_in
            state.mode == Vpn5gAuthMode.REGISTER -> R.string.vpn5g_auth_signup_button
            state.mode == Vpn5gAuthMode.RECOVER && state.phase == Vpn5gAuthPhase.SIGNING_IN ->
                R.string.vpn5g_auth_sending_link
            state.mode == Vpn5gAuthMode.RECOVER -> R.string.vpn5g_auth_send_link
            state.phase == Vpn5gAuthPhase.CODE_SENT -> R.string.vpn5g_auth_confirm
            state.phase == Vpn5gAuthPhase.SENDING_CODE -> R.string.vpn5g_auth_sending
            state.phase == Vpn5gAuthPhase.VERIFYING -> R.string.vpn5g_auth_verifying
            else -> R.string.vpn5g_auth_send_code
        }
        val primaryEnabled = !busy && state.phase != Vpn5gAuthPhase.LINK_SENT
        Vpn5gStaggeredItem(index = 6, visible = entered) {
            Button(
                onClick = {
                    when {
                        isNewPassword -> authViewModel.updatePassword()
                        state.mode == Vpn5gAuthMode.LOGIN -> authViewModel.signInWithPassword()
                        state.mode == Vpn5gAuthMode.REGISTER -> authViewModel.signUp()
                        state.mode == Vpn5gAuthMode.RECOVER -> authViewModel.sendRecoveryLink()
                        state.phase == Vpn5gAuthPhase.CODE_SENT -> authViewModel.verifyCode()
                        else -> authViewModel.sendCode()
                    }
                },
                enabled = primaryEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Vpn5gAccent,
                    contentColor = Color.White,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(primaryLabelRes), fontSize = 16.sp)
            }
        }

        // Code field, shown after a code was sent (OTP mode).
        if (state.mode == Vpn5gAuthMode.OTP &&
            (state.phase == Vpn5gAuthPhase.CODE_SENT || state.phase == Vpn5gAuthPhase.VERIFYING)
        ) {
            Vpn5gStaggeredItem(index = 7, visible = entered) {
                OutlinedTextField(
                    value = state.code,
                    onValueChange = { authViewModel.setCode(it) },
                    label = { Text(stringResource(R.string.vpn5g_auth_code)) },
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = state.codeErrorRes != null,
                    supportingText = state.codeErrorRes?.let { { Text(stringResource(it)) } },
                    colors = vpn5gTextFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        // Code-sent note + resend (v1.1.5 §7): shown after a code was
        // sent. The note is honest — the backend accepted the request;
        // delivery depends on the email service, so it says where to
        // look when the code does not arrive instead of promising
        // instant delivery. Resend is limited to once per 60 seconds.
        if (state.mode == Vpn5gAuthMode.OTP &&
            (state.phase == Vpn5gAuthPhase.CODE_SENT || state.phase == Vpn5gAuthPhase.VERIFYING)
        ) {
            Vpn5gStaggeredItem(index = 8, visible = entered) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.vpn5g_auth_code_sent),
                        color = Vpn5gOnDarkMuted,
                        fontSize = 14.sp,
                    )
                    TextButton(
                        onClick = { authViewModel.resendCode() },
                        enabled = !busy && resendWaitSeconds <= 0L,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (resendWaitSeconds > 0L) {
                                stringResource(
                                    R.string.vpn5g_auth_resend_in, resendWaitSeconds
                                )
                            } else {
                                stringResource(R.string.vpn5g_auth_resend)
                            },
                            color = Vpn5gAccentLight,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }

        // Recovery link sent: the user must tap the link in the email;
        // the link returns to the app and opens the new-password
        // screen. A resend link is offered (guarded by the ViewModel).
        if (state.mode == Vpn5gAuthMode.RECOVER && state.phase == Vpn5gAuthPhase.LINK_SENT) {
            Vpn5gStaggeredItem(index = 9, visible = entered) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.vpn5g_auth_recovery_link_sent),
                        color = Vpn5gOnDarkMuted,
                        fontSize = 14.sp,
                    )
                    TextButton(
                        onClick = { authViewModel.sendRecoveryLink() },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.vpn5g_auth_send_link_again),
                            color = Vpn5gAccentLight,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }

        if (state.mode == Vpn5gAuthMode.RECOVER && state.phase == Vpn5gAuthPhase.IDLE) {
            Vpn5gStaggeredItem(index = 10, visible = entered) {
                Text(
                    text = stringResource(R.string.vpn5g_auth_recover_body),
                    color = Vpn5gOnDarkMuted,
                    fontSize = 14.sp,
                )
            }
        }

        state.errorRes?.let { errorRes ->
            Vpn5gStaggeredItem(index = 11, visible = entered) {
                Text(
                    text = stringResource(errorRes),
                    color = Color(0xFFF87171),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Mode switch links.
        Vpn5gStaggeredItem(index = 12, visible = entered) {
            Column(modifier = Modifier.fillMaxWidth()) {
                when (state.mode) {
                    Vpn5gAuthMode.LOGIN -> {
                        TextButton(
                            onClick = { authViewModel.setMode(Vpn5gAuthMode.REGISTER) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = stringResource(R.string.vpn5g_auth_go_register), color = Vpn5gAccentLight, fontSize = 14.sp)
                        }
                        TextButton(
                            onClick = { authViewModel.setMode(Vpn5gAuthMode.OTP) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = stringResource(R.string.vpn5g_auth_go_otp), color = Vpn5gAccentLight, fontSize = 14.sp)
                        }
                        TextButton(
                            onClick = { authViewModel.setMode(Vpn5gAuthMode.RECOVER) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = stringResource(R.string.vpn5g_auth_forgot), color = Vpn5gAccentLight, fontSize = 14.sp)
                        }
                    }
                    Vpn5gAuthMode.REGISTER -> {
                        TextButton(
                            onClick = { authViewModel.setMode(Vpn5gAuthMode.LOGIN) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = stringResource(R.string.vpn5g_auth_go_login), color = Vpn5gAccentLight, fontSize = 14.sp)
                        }
                        TextButton(
                            onClick = { authViewModel.setMode(Vpn5gAuthMode.OTP) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = stringResource(R.string.vpn5g_auth_go_otp), color = Vpn5gAccentLight, fontSize = 14.sp)
                        }
                    }
                    Vpn5gAuthMode.OTP -> {
                        TextButton(
                            onClick = { authViewModel.setMode(Vpn5gAuthMode.LOGIN) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = stringResource(R.string.vpn5g_auth_go_password_login), color = Vpn5gAccentLight, fontSize = 14.sp)
                        }
                    }
                    Vpn5gAuthMode.RECOVER -> {
                        TextButton(
                            onClick = { authViewModel.setMode(Vpn5gAuthMode.LOGIN) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = stringResource(R.string.vpn5g_auth_go_password_login), color = Vpn5gAccentLight, fontSize = 14.sp)
                        }
                    }
                }
                TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(R.string.vpn5g_nav_me), color = Vpn5gAccentLight, fontSize = 14.sp)
                }
            }
        }
    }
}

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
