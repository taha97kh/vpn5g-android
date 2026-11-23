package com.v2ray.ang.ui.vpn5g

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.R
import com.v2ray.ang.handler.vpn5g.Vpn5gAuthConfig
import com.v2ray.ang.handler.vpn5g.Vpn5gAuthService
import com.v2ray.ang.handler.vpn5g.Vpn5gEmailPolicy
import com.v2ray.ang.handler.vpn5g.Vpn5gOtpPolicy
import com.v2ray.ang.handler.vpn5g.Vpn5gRecoveryLink
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.AppConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One auth mode per screen (v1.1.8). LOGIN is the password login
 * (scenario 4), REGISTER the direct signup (scenario 1), OTP the
 * 6-digit code login/signup (scenario 2), RECOVER the forgot-password
 * link flow (scenario 3).
 */
enum class Vpn5gAuthMode { LOGIN, REGISTER, OTP, RECOVER }

enum class Vpn5gAuthPhase {
    IDLE, SENDING_CODE, CODE_SENT, VERIFYING, SIGNING_IN, LINK_SENT,
    NEW_PASSWORD, UPDATING_PASSWORD, DONE, ERROR
}

data class Vpn5gAuthUiState(
    val mode: Vpn5gAuthMode = Vpn5gAuthMode.LOGIN,
    val phase: Vpn5gAuthPhase = Vpn5gAuthPhase.IDLE,
    val email: String = "",
    val code: String = "",
    val password: String = "",
    val passwordConfirm: String = "",
    val emailErrorRes: Int? = null,
    val codeErrorRes: Int? = null,
    val passwordErrorRes: Int? = null,
    val errorRes: Int? = null,
    val signedInEmail: String? = null,
    /** False until a Supabase project is configured (see Vpn5gAuthConfig). */
    val backendConfigured: Boolean = Vpn5gAuthConfig.isConfigured(),
    /** Epoch millis of the last accepted code send (resend cooldown). */
    val lastCodeSentMs: Long = 0L,
    /** Wrong-code tries in this session (lockout after the limit). */
    val wrongCodeAttempts: Int = 0,
    /** Recovery-link tokens (scenario 3); set only by startRecovery. */
    val recoveryLink: Vpn5gRecoveryLink.Parsed? = null,
)

/**
 * Auth screen ViewModel: owns the four Supabase Auth flows (direct
 * signup, OTP code, recovery link + new password, password login) and
 * calls [Vpn5gAuthService] on IO.
 *
 * "Too many requests" root cause (v1.1.8 fix): the old screen could
 * fire an OTP send with no cooldown guard and no in-flight lock, so a
 * double tap — or a mode switch followed by another send within 60
 * seconds — hit Supabase's one-send-per-60-seconds limit. Every send
 * now goes through ONE guarded entry point per mode: a request already
 * in flight is ignored, and a send inside the 60-second cooldown is
 * refused with the rate-limit message instead of being fired. The
 * screen also disables every send button while its request is in
 * flight, so a double tap can never reach the backend. Nothing is
 * auto-retried: a 429 shows how long to wait.
 *
 * SignUp and OTP are never fired for the same attempt: the OTP
 * fallback after a no-session signup is sequential (signup first,
 * then — only if the backend returned a user without a session — one
 * OTP send), never parallel.
 *
 * When the Supabase project is not configured, every call returns
 * NotConfigured and the screen says so honestly (no fake sends).
 * Validation (Gmail-only email, 6-digit code, password length and
 * confirmation) is real and local. No error path leaves the UI stuck
 * in a loading state: every result sets a terminal phase.
 */
class Vpn5gAuthViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(
        Vpn5gAuthUiState(signedInEmail = Vpn5gAuthService.currentSession()?.email)
    )
    val uiState: StateFlow<Vpn5gAuthUiState> = _uiState.asStateFlow()

    /** Phases in which a request is already running (double-tap guard). */
    private fun isBusy(phase: Vpn5gAuthPhase): Boolean = phase == Vpn5gAuthPhase.SENDING_CODE ||
        phase == Vpn5gAuthPhase.VERIFYING ||
        phase == Vpn5gAuthPhase.SIGNING_IN ||
        phase == Vpn5gAuthPhase.UPDATING_PASSWORD

    fun setMode(mode: Vpn5gAuthMode) {
        _uiState.update {
            it.copy(
                mode = mode,
                phase = Vpn5gAuthPhase.IDLE,
                code = "",
                password = "",
                passwordConfirm = "",
                emailErrorRes = null,
                codeErrorRes = null,
                passwordErrorRes = null,
                errorRes = null,
                wrongCodeAttempts = 0,
                recoveryLink = null,
            )
        }
    }

    fun setEmail(value: String) {
        _uiState.update { it.copy(email = value, emailErrorRes = null, errorRes = null) }
    }

    fun setCode(value: String) {
        _uiState.update {
            it.copy(code = value.filter { ch -> ch.isDigit() }.take(6), codeErrorRes = null, errorRes = null)
        }
    }

    fun setPassword(value: String) {
        _uiState.update { it.copy(password = value, passwordErrorRes = null, errorRes = null) }
    }

    fun setPasswordConfirm(value: String) {
        _uiState.update { it.copy(passwordConfirm = value, passwordErrorRes = null, errorRes = null) }
    }

    /**
     * Email validation. Every mode accepts ONLY @gmail.com addresses (see
     * [Vpn5gEmailPolicy]): a non-Gmail address can never be a registered
     * account, so it is rejected upfront with a clear message instead of
     * failing at the backend.
     */
    private fun validEmail(): Boolean {
        val email = _uiState.value.email.trim()
        val errorRes = when (Vpn5gEmailPolicy.check(email)) {
            Vpn5gEmailPolicy.Check.OK -> null
            Vpn5gEmailPolicy.Check.DISPOSABLE -> R.string.vpn5g_auth_disposable
            Vpn5gEmailPolicy.Check.NOT_GMAIL -> R.string.vpn5g_auth_gmail_only
            Vpn5gEmailPolicy.Check.INVALID_FORMAT -> R.string.vpn5g_auth_invalid_email
        }
        if (errorRes != null) {
            _uiState.update { it.copy(emailErrorRes = errorRes) }
            return false
        }
        return true
    }

    /** Password validation for signup / password login / new password. */
    private fun validPassword(requireConfirm: Boolean): Boolean {
        val state = _uiState.value
        val errorRes = when {
            state.password.isEmpty() -> R.string.vpn5g_auth_password_required
            state.password.length < 6 -> R.string.vpn5g_auth_weak_password
            requireConfirm && state.password != state.passwordConfirm ->
                R.string.vpn5g_auth_password_mismatch
            else -> null
        }
        if (errorRes != null) {
            _uiState.update { it.copy(passwordErrorRes = errorRes) }
            return false
        }
        return true
    }

    /**
     * Scenario 1: direct signup (email + password, no code). With
     * "Confirm email" OFF the backend returns a session and the user
     * is signed in immediately. With it ON the backend returns a user
     * without a session: one OTP send follows (sequential fallback)
     * and the code screen opens. An already-registered email offers
     * the password-login screen instead of a dead end.
     */
    fun signUp() {
        val state = _uiState.value
        if (isBusy(state.phase)) return
        if (!validEmail() || !validPassword(requireConfirm = true)) return
        val email = state.email.trim()
        val password = state.password
        _uiState.update { it.copy(phase = Vpn5gAuthPhase.SIGNING_IN, errorRes = null) }
        viewModelScope.launch {
            when (val result = Vpn5gAuthService.signUp(email, password)) {
                is Vpn5gAuthService.AuthResult.SignedIn ->
                    _uiState.update {
                        it.copy(phase = Vpn5gAuthPhase.DONE, signedInEmail = result.session.email)
                    }
                Vpn5gAuthService.AuthResult.NeedsCode -> {
                    // "Confirm email" is ON: fall back to OTP, once.
                    when (val otp = Vpn5gAuthService.sendLoginOtp(email)) {
                        Vpn5gAuthService.AuthResult.CodeSent ->
                            _uiState.update {
                                it.copy(
                                    phase = Vpn5gAuthPhase.CODE_SENT,
                                    mode = Vpn5gAuthMode.OTP,
                                    errorRes = R.string.vpn5g_auth_signup_code_needed,
                                    lastCodeSentMs = System.currentTimeMillis(),
                                )
                            }
                        Vpn5gAuthService.AuthResult.NotConfigured ->
                            _uiState.update { it.copy(phase = Vpn5gAuthPhase.ERROR, errorRes = R.string.vpn5g_auth_coming_soon) }
                        is Vpn5gAuthService.AuthResult.Error ->
                            _uiState.update { it.copy(phase = Vpn5gAuthPhase.ERROR, errorRes = mapError(otp.message)) }
                        else ->
                            _uiState.update { it.copy(phase = Vpn5gAuthPhase.ERROR, errorRes = R.string.vpn5g_auth_generic_error) }
                    }
                }
                Vpn5gAuthService.AuthResult.NotConfigured ->
                    _uiState.update { it.copy(phase = Vpn5gAuthPhase.ERROR, errorRes = R.string.vpn5g_auth_coming_soon) }
                is Vpn5gAuthService.AuthResult.Error -> {
                    if (result.message == "email_already_registered") {
                        // Not a dead end: offer the password login.
                        _uiState.update {
                            it.copy(
                                mode = Vpn5gAuthMode.LOGIN,
                                phase = Vpn5gAuthPhase.IDLE,
                                password = "",
                                passwordConfirm = "",
                                errorRes = R.string.vpn5g_auth_email_already_registered,
                            )
                        }
                    } else {
                        _uiState.update { it.copy(phase = Vpn5gAuthPhase.ERROR, errorRes = mapError(result.message)) }
                    }
                }
                else ->
                    _uiState.update { it.copy(phase = Vpn5gAuthPhase.ERROR, errorRes = R.string.vpn5g_auth_generic_error) }
            }
        }
    }

    /**
     * Scenario 4: password login for already-registered users.
     * A wrong email/password is a friendly message, never a crash.
     */
    fun signInWithPassword() {
        val state = _uiState.value
        if (isBusy(state.phase)) return
        if (!validEmail() || !validPassword(requireConfirm = false)) return
        val email = state.email.trim()
        val password = state.password
        _uiState.update { it.copy(phase = Vpn5gAuthPhase.SIGNING_IN, errorRes = null) }
        viewModelScope.launch {
            when (val result = Vpn5gAuthService.signInWithPassword(email, password)) {
                is Vpn5gAuthService.AuthResult.SignedIn ->
                    _uiState.update {
                        it.copy(phase = Vpn5gAuthPhase.DONE, signedInEmail = result.session.email)
                    }
                Vpn5gAuthService.AuthResult.NotConfigured ->
                    _uiState.update { it.copy(phase = Vpn5gAuthPhase.ERROR, errorRes = R.string.vpn5g_auth_coming_soon) }
                is Vpn5gAuthService.AuthResult.Error ->
                    _uiState.update { it.copy(phase = Vpn5gAuthPhase.ERROR, errorRes = mapError(result.message)) }
                else ->
                    _uiState.update { it.copy(phase = Vpn5gAuthPhase.ERROR, errorRes = R.string.vpn5g_auth_generic_error) }
            }
        }
    }

    /**
     * Scenario 2, step 1: send the OTP code. Single guarded entry
     * point: a request already in flight is ignored, and a send
     * inside the 60-second cooldown is refused with the rate-limit
     * message instead of being fired at the backend.
     */
    fun sendCode() {
        val state = _uiState.value
        if (isBusy(state.phase)) return
        if (!validEmail()) return
        if (!Vpn5gOtpPolicy.canResend(state.lastCodeSentMs, System.currentTimeMillis())) {
            _uiState.update { it.copy(errorRes = R.string.vpn5g_auth_rate_limited) }
            return
        }
        _uiState.update { it.copy(phase = Vpn5gAuthPhase.SENDING_CODE, errorRes = null) }
        viewModelScope.launch {
            when (val result = Vpn5gAuthService.sendLoginOtp(state.email.trim())) {
                Vpn5gAuthService.AuthResult.CodeSent ->
                    _uiState.update {
                        it.copy(
                            phase = Vpn5gAuthPhase.CODE_SENT,
                            lastCodeSentMs = System.currentTimeMillis(),
                        )
                    }
                Vpn5gAuthService.AuthResult.NotConfigured ->
                    _uiState.update { it.copy(phase = Vpn5gAuthPhase.ERROR, errorRes = R.string.vpn5g_auth_coming_soon) }
                is Vpn5gAuthService.AuthResult.Error ->
                    _uiState.update { it.copy(phase = Vpn5gAuthPhase.ERROR, errorRes = mapError(result.message)) }
                is Vpn5gAuthService.AuthResult.SignedIn ->
                    _uiState.update {
                        it.copy(phase = Vpn5gAuthPhase.DONE, signedInEmail = result.session.email)
                    }
                else ->
                    _uiState.update { it.copy(phase = Vpn5gAuthPhase.ERROR, errorRes = R.string.vpn5g_auth_generic_error) }
            }
        }
    }

    /**
     * Resend the code (v1.1.5 §7): allowed only after the 60-second
     * cooldown (Supabase rate-limits faster resends). The screen disables
     * the button while cooling down; this is the second guard.
     */
    fun resendCode() {
        if (!Vpn5gOtpPolicy.canResend(
                _uiState.value.lastCodeSentMs, System.currentTimeMillis()
            )
        ) {
            return
        }
        sendCode()
    }

    /** Scenario 2, step 2: verify the 6-digit code. */
    fun verifyCode() {
        val state = _uiState.value
        if (isBusy(state.phase)) return
        if (!state.code.matches(Regex("\\d{6}"))) {
            _uiState.update { it.copy(codeErrorRes = R.string.vpn5g_auth_invalid_code) }
            return
        }
        _uiState.update { it.copy(phase = Vpn5gAuthPhase.VERIFYING, errorRes = null) }
        viewModelScope.launch {
            when (val result = Vpn5gAuthService.verifyOtp(state.email.trim(), state.code)) {
                is Vpn5gAuthService.AuthResult.SignedIn ->
                    _uiState.update {
                        it.copy(
                            phase = Vpn5gAuthPhase.DONE,
                            signedInEmail = result.session.email,
                            wrongCodeAttempts = 0,
                        )
                    }
                Vpn5gAuthService.AuthResult.NotConfigured ->
                    _uiState.update { it.copy(phase = Vpn5gAuthPhase.ERROR, errorRes = R.string.vpn5g_auth_coming_soon) }
                is Vpn5gAuthService.AuthResult.Error -> {
                    // A wrong or expired code counts against the session
                    // limit (v1.1.5 §7): after the limit the user must
                    // take a fresh code instead of guessing forever.
                    val errorRes = mapError(result.message)
                    _uiState.update {
                        if (result.message == "code_wrong_or_expired") {
                            val attempts = it.wrongCodeAttempts + 1
                            if (Vpn5gOtpPolicy.isLockedOut(attempts)) {
                                it.copy(
                                    phase = Vpn5gAuthPhase.ERROR,
                                    errorRes = R.string.vpn5g_auth_code_locked,
                                    wrongCodeAttempts = 0,
                                    code = "",
                                )
                            } else {
                                it.copy(
                                    phase = Vpn5gAuthPhase.ERROR,
                                    errorRes = errorRes,
                                    wrongCodeAttempts = attempts,
                                )
                            }
                        } else {
                            it.copy(
                                phase = Vpn5gAuthPhase.ERROR,
                                errorRes = errorRes,
                            )
                        }
                    }
                }
                else ->
                    _uiState.update { it.copy(phase = Vpn5gAuthPhase.ERROR, errorRes = R.string.vpn5g_auth_generic_error) }
            }
        }
    }

    /**
     * Scenario 3, step 1: send the recovery LINK (not a code).
     * A request already in flight is ignored.
     */
    fun sendRecoveryLink() {
        val state = _uiState.value
        if (isBusy(state.phase)) return
        if (!validEmail()) return
        _uiState.update { it.copy(phase = Vpn5gAuthPhase.SIGNING_IN, errorRes = null) }
        viewModelScope.launch {
            when (val result = Vpn5gAuthService.sendRecoveryLink(state.email.trim())) {
                Vpn5gAuthService.AuthResult.RecoveryLinkSent ->
                    _uiState.update { it.copy(phase = Vpn5gAuthPhase.LINK_SENT) }
                Vpn5gAuthService.AuthResult.NotConfigured ->
                    _uiState.update { it.copy(phase = Vpn5gAuthPhase.ERROR, errorRes = R.string.vpn5g_auth_coming_soon) }
                is Vpn5gAuthService.AuthResult.Error ->
                    _uiState.update { it.copy(phase = Vpn5gAuthPhase.ERROR, errorRes = mapError(result.message)) }
                else ->
                    _uiState.update { it.copy(phase = Vpn5gAuthPhase.ERROR, errorRes = R.string.vpn5g_auth_generic_error) }
            }
        }
    }

    /**
     * Scenario 3, step 2: the recovery deep link arrived
     * (`vpn5g://auth/callback`, type=recovery). Opens the
     * new-password screen with the link's tokens; the tokens are
     * stored in ViewModel state so they survive rotation.
     */
    fun startRecovery(link: Vpn5gRecoveryLink.Parsed) {
        _uiState.update {
            it.copy(
                mode = Vpn5gAuthMode.RECOVER,
                phase = Vpn5gAuthPhase.NEW_PASSWORD,
                password = "",
                passwordConfirm = "",
                passwordErrorRes = null,
                errorRes = null,
                recoveryLink = link,
            )
        }
    }

    /** Scenario 3, step 3: set the new password, then sign in. */
    fun updatePassword() {
        val state = _uiState.value
        if (isBusy(state.phase)) return
        val link = state.recoveryLink
        if (link == null) {
            _uiState.update { it.copy(errorRes = R.string.vpn5g_auth_recovery_link_invalid) }
            return
        }
        if (!validPassword(requireConfirm = true)) return
        val email = state.email.trim()
        val newPassword = state.password
        _uiState.update { it.copy(phase = Vpn5gAuthPhase.UPDATING_PASSWORD, errorRes = null) }
        viewModelScope.launch {
            when (val result = Vpn5gAuthService.updatePassword(link, email, newPassword)) {
                is Vpn5gAuthService.AuthResult.SignedIn ->
                    _uiState.update {
                        it.copy(
                            phase = Vpn5gAuthPhase.DONE,
                            signedInEmail = result.session.email,
                            recoveryLink = null,
                        )
                    }
                Vpn5gAuthService.AuthResult.NotConfigured ->
                    _uiState.update { it.copy(phase = Vpn5gAuthPhase.ERROR, errorRes = R.string.vpn5g_auth_coming_soon) }
                is Vpn5gAuthService.AuthResult.Error ->
                    _uiState.update { it.copy(phase = Vpn5gAuthPhase.ERROR, errorRes = mapError(result.message)) }
                else ->
                    _uiState.update { it.copy(phase = Vpn5gAuthPhase.ERROR, errorRes = R.string.vpn5g_auth_generic_error) }
            }
        }
    }

    fun signOut() {
        Vpn5gAuthService.signOut()
        _uiState.update {
            it.copy(phase = Vpn5gAuthPhase.IDLE, signedInEmail = null, code = "", recoveryLink = null)
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorRes = null, phase = Vpn5gAuthPhase.IDLE) }
    }

    private fun mapError(message: String): Int {
        LogUtil.e(AppConfig.TAG, "Vpn5gAuthViewModel: auth error $message")
        return when (message) {
            "network_error" -> R.string.vpn5g_auth_network_error
            "rate_limited" -> R.string.vpn5g_auth_rate_limited
            "email_not_registered" -> R.string.vpn5g_auth_email_not_registered
            "email_already_registered" -> R.string.vpn5g_auth_email_already_registered
            "invalid_credentials" -> R.string.vpn5g_auth_invalid_credentials
            "weak_password" -> R.string.vpn5g_auth_weak_password
            "recovery_link_invalid" -> R.string.vpn5g_auth_recovery_link_invalid
            "code_wrong_or_expired" -> R.string.vpn5g_auth_code_wrong
            else -> R.string.vpn5g_auth_generic_error
        }
    }

    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(Vpn5gAuthViewModel::class.java)) {
                return Vpn5gAuthViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
