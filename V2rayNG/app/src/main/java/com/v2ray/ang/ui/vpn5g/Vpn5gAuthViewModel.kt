package com.v2ray.ang.ui.vpn5g

import android.app.Application
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.v2ray.ang.R
import com.v2ray.ang.handler.vpn5g.Vpn5gAuthConfig
import com.v2ray.ang.handler.vpn5g.Vpn5gAuthService
import com.v2ray.ang.handler.vpn5g.Vpn5gEmailPolicy
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.AppConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Vpn5gAuthMode { LOGIN, REGISTER, RECOVER }

enum class Vpn5gAuthPhase {
    IDLE, SENDING_CODE, CODE_SENT, VERIFYING, NEW_PASSWORD, DONE, ERROR
}

data class Vpn5gAuthUiState(
    val mode: Vpn5gAuthMode = Vpn5gAuthMode.LOGIN,
    val phase: Vpn5gAuthPhase = Vpn5gAuthPhase.IDLE,
    val email: String = "",
    val password: String = "",
    val newPassword: String = "",
    val code: String = "",
    val emailErrorRes: Int? = null,
    val passwordErrorRes: Int? = null,
    val codeErrorRes: Int? = null,
    val errorRes: Int? = null,
    val signedInEmail: String? = null,
    /** False until a Supabase project is configured (see Vpn5gAuthConfig). */
    val backendConfigured: Boolean = Vpn5gAuthConfig.isConfigured(),
)

/**
 * Auth screen ViewModel: owns the login / register / password-recovery
 * state machine and calls [Vpn5gAuthService] on IO. The Supabase project
 * does not exist yet, so every send/verify returns NotConfigured and the
 * screen says so honestly (no fake sends). Validation (email format,
 * 6-digit code, password length) is real and local.
 */
class Vpn5gAuthViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(
        Vpn5gAuthUiState(signedInEmail = Vpn5gAuthService.currentSession()?.email)
    )
    val uiState: StateFlow<Vpn5gAuthUiState> = _uiState.asStateFlow()

    fun setMode(mode: Vpn5gAuthMode) {
        _uiState.update {
            it.copy(
                mode = mode,
                phase = Vpn5gAuthPhase.IDLE,
                code = "",
                newPassword = "",
                emailErrorRes = null,
                passwordErrorRes = null,
                codeErrorRes = null,
                errorRes = null,
            )
        }
    }

    fun setEmail(value: String) {
        _uiState.update { it.copy(email = value, emailErrorRes = null, errorRes = null) }
    }

    fun setPassword(value: String) {
        _uiState.update { it.copy(password = value, passwordErrorRes = null, errorRes = null) }
    }

    fun setNewPassword(value: String) {
        _uiState.update { it.copy(newPassword = value, passwordErrorRes = null, errorRes = null) }
    }

    fun setCode(value: String) {
        _uiState.update {
            it.copy(code = value.filter { ch -> ch.isDigit() }.take(6), codeErrorRes = null, errorRes = null)
        }
    }

    /**
     * Email validation. Signup and password recovery accept ONLY
     * @gmail.com addresses (see [Vpn5gEmailPolicy]); login keeps the
     * generic format check because registered accounts are gmail by
     * construction.
     */
    private fun validEmail(gmailOnly: Boolean = false): Boolean {
        val email = _uiState.value.email.trim()
        if (gmailOnly) {
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
        return if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.update { it.copy(emailErrorRes = R.string.vpn5g_auth_invalid_email) }
            false
        } else {
            true
        }
    }

    private fun validPassword(password: String): Boolean {
        return if (password.length < 6) {
            _uiState.update { it.copy(passwordErrorRes = R.string.vpn5g_auth_password_short) }
            false
        } else {
            true
        }
    }

    /** Login with email + password. */
    fun signInWithPassword() {
        if (!validEmail()) return
        if (!validPassword(_uiState.value.password)) return
        val state = _uiState.value
        _uiState.update { it.copy(phase = Vpn5gAuthPhase.VERIFYING, errorRes = null) }
        viewModelScope.launch {
            when (val result = Vpn5gAuthService.signInWithPassword(state.email.trim(), state.password)) {
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

    /** Step 1 of the code flows: send the OTP code for the current mode. */
    fun sendCode() {
        val gmailOnly = _uiState.value.mode != Vpn5gAuthMode.LOGIN
        if (!validEmail(gmailOnly = gmailOnly)) return
        if (_uiState.value.mode == Vpn5gAuthMode.REGISTER &&
            !validPassword(_uiState.value.password)
        ) return
        val state = _uiState.value
        _uiState.update { it.copy(phase = Vpn5gAuthPhase.SENDING_CODE, errorRes = null) }
        viewModelScope.launch {
            val result = when (state.mode) {
                Vpn5gAuthMode.LOGIN -> Vpn5gAuthService.sendLoginOtp(state.email.trim())
                Vpn5gAuthMode.REGISTER -> Vpn5gAuthService.signUpWithEmail(state.email.trim(), state.password)
                Vpn5gAuthMode.RECOVER -> Vpn5gAuthService.sendRecoveryOtp(state.email.trim())
            }
            when (result) {
                Vpn5gAuthService.AuthResult.CodeSent ->
                    _uiState.update { it.copy(phase = Vpn5gAuthPhase.CODE_SENT) }
                Vpn5gAuthService.AuthResult.NotConfigured ->
                    _uiState.update { it.copy(phase = Vpn5gAuthPhase.ERROR, errorRes = R.string.vpn5g_auth_coming_soon) }
                is Vpn5gAuthService.AuthResult.Error ->
                    _uiState.update { it.copy(phase = Vpn5gAuthPhase.ERROR, errorRes = mapError(result.message)) }
                is Vpn5gAuthService.AuthResult.SignedIn ->
                    _uiState.update {
                        it.copy(phase = Vpn5gAuthPhase.DONE, signedInEmail = result.session.email)
                    }
            }
        }
    }

    /** Step 2 of the code flows: verify the 6-digit code. */
    fun verifyCode() {
        val state = _uiState.value
        if (!state.code.matches(Regex("\\d{6}"))) {
            _uiState.update { it.copy(codeErrorRes = R.string.vpn5g_auth_invalid_code) }
            return
        }
        val type = when (state.mode) {
            Vpn5gAuthMode.LOGIN -> "email"
            Vpn5gAuthMode.REGISTER -> "signup"
            Vpn5gAuthMode.RECOVER -> "recovery"
        }
        _uiState.update { it.copy(phase = Vpn5gAuthPhase.VERIFYING, errorRes = null) }
        viewModelScope.launch {
            when (val result = Vpn5gAuthService.verifyOtp(state.email.trim(), state.code, type)) {
                is Vpn5gAuthService.AuthResult.SignedIn -> {
                    if (state.mode == Vpn5gAuthMode.RECOVER) {
                        // Recovery verified: now ask for the new password.
                        _uiState.update {
                            it.copy(phase = Vpn5gAuthPhase.NEW_PASSWORD, signedInEmail = result.session.email)
                        }
                    } else {
                        _uiState.update {
                            it.copy(phase = Vpn5gAuthPhase.DONE, signedInEmail = result.session.email)
                        }
                    }
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

    /** Recovery step 3: set the new password with the verified session. */
    fun updatePassword() {
        if (!validPassword(_uiState.value.newPassword)) return
        val newPassword = _uiState.value.newPassword
        _uiState.update { it.copy(phase = Vpn5gAuthPhase.VERIFYING, errorRes = null) }
        viewModelScope.launch {
            when (val result = Vpn5gAuthService.updatePassword(newPassword)) {
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

    fun signOut() {
        Vpn5gAuthService.signOut()
        _uiState.update {
            it.copy(phase = Vpn5gAuthPhase.IDLE, signedInEmail = null, code = "", password = "")
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorRes = null, phase = Vpn5gAuthPhase.IDLE) }
    }

    private fun mapError(message: String): Int {
        LogUtil.e(AppConfig.TAG, "Vpn5gAuthViewModel: auth error $message")
        return when {
            message == "password_too_short" -> R.string.vpn5g_auth_password_short
            message == "network_error" -> R.string.vpn5g_auth_network_error
            message.startsWith("verify_failed") || message.startsWith("otp_failed") ->
                R.string.vpn5g_auth_code_wrong
            message.startsWith("signin_failed") || message.startsWith("signup_failed") ->
                R.string.vpn5g_auth_signin_failed
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
