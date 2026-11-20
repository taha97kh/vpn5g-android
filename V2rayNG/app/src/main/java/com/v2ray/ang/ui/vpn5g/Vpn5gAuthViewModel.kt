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
import com.v2ray.ang.util.LogUtil
import com.v2ray.ang.AppConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Vpn5gAuthMode { LOGIN, REGISTER, RECOVER }

enum class Vpn5gAuthPhase {
    IDLE, SENDING_CODE, CODE_SENT, VERIFYING, DONE, ERROR
}

data class Vpn5gAuthUiState(
    val mode: Vpn5gAuthMode = Vpn5gAuthMode.LOGIN,
    val phase: Vpn5gAuthPhase = Vpn5gAuthPhase.IDLE,
    val email: String = "",
    val code: String = "",
    val emailErrorRes: Int? = null,
    val codeErrorRes: Int? = null,
    val errorRes: Int? = null,
    val signedInEmail: String? = null,
    /** False until a Supabase project is configured (see Vpn5gAuthConfig). */
    val backendConfigured: Boolean = Vpn5gAuthConfig.isConfigured(),
)

/**
 * Auth screen ViewModel: owns the passwordless OTP state machine
 * (email → 6-digit code → verified → signed in) and calls
 * [Vpn5gAuthService] on IO. Registration, login, and recovery are the
 * same OTP flow: registration creates the account on first use, recovery
 * signs an existing account back in. There is no password anywhere.
 *
 * When the Supabase project is not configured, every send/verify returns
 * NotConfigured and the screen says so honestly (no fake sends).
 * Validation (Gmail-only email, 6-digit code) is real and local.
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
                emailErrorRes = null,
                codeErrorRes = null,
                errorRes = null,
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

    /** Step 1 of the code flows: send the OTP code for the current mode. */
    fun sendCode() {
        if (!validEmail()) return
        val state = _uiState.value
        _uiState.update { it.copy(phase = Vpn5gAuthPhase.SENDING_CODE, errorRes = null) }
        viewModelScope.launch {
            val result = when (state.mode) {
                Vpn5gAuthMode.LOGIN -> Vpn5gAuthService.sendLoginOtp(state.email.trim())
                Vpn5gAuthMode.REGISTER -> Vpn5gAuthService.sendLoginOtp(state.email.trim())
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
        _uiState.update { it.copy(phase = Vpn5gAuthPhase.VERIFYING, errorRes = null) }
        viewModelScope.launch {
            when (val result = Vpn5gAuthService.verifyOtp(state.email.trim(), state.code)) {
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
            it.copy(phase = Vpn5gAuthPhase.IDLE, signedInEmail = null, code = "")
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
