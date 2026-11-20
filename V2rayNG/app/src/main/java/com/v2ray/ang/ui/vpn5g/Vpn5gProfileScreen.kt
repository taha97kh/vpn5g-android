package com.v2ray.ang.ui.vpn5g

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.BuildConfig
import com.v2ray.ang.R
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.BOT_URL
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.CHANNEL_URL
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants.SUPPORT_URL

/**
 * "Me" screen: account (email auth), subscription remaining, advanced
 * settings, and about (channel, bot, support, version).
 */
@Composable
fun Vpn5gProfileScreen(
    state: Vpn5gHomeUiState,
    onBack: () -> Unit,
    onOpenAuth: () -> Unit,
    onOpenProtocol: () -> Unit,
    onSignOut: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Vpn5gStaggeredItem(index = 0, visible = entered) {
            Vpn5gPulsingLogo(size = 96.dp)
        }
        Vpn5gStaggeredItem(index = 1, visible = entered) {
            Text(
                text = stringResource(R.string.vpn5g_nav_me),
                color = Vpn5gOnDark,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        // Account.
        Vpn5gStaggeredItem(index = 2, visible = entered) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.vpn5g_profile_account),
                    color = Vpn5gOnDark,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                if (state.signedInEmail.isNullOrBlank()) {
                    Text(
                        text = stringResource(R.string.vpn5g_profile_not_signed_in),
                        color = Vpn5gOnDarkMuted,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onOpenAuth,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Vpn5gAccent,
                            contentColor = Color.White,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.vpn5g_auth_title), fontSize = 16.sp)
                    }
                } else {
                    Text(
                        text = state.signedInEmail,
                        color = Vpn5gOnDark,
                        fontSize = 15.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.vpn5g_auth_signout),
                            color = Vpn5gAccentLight,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }

        // Subscription remaining (remote values only; never invented).
        Vpn5gStaggeredItem(index = 3, visible = entered) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.vpn5g_subscription_title),
                    color = Vpn5gOnDark,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                val status = state.subscriptionStatus
                if (status == null) {
                    Text(
                        text = stringResource(R.string.vpn5g_subscription_none),
                        color = Vpn5gOnDarkMuted,
                        fontSize = 14.sp,
                    )
                } else {
                    status.plan_name?.let { plan ->
                        Text(
                            text = stringResource(R.string.vpn5g_subscription_plan, plan),
                            color = Vpn5gOnDark,
                            fontSize = 15.sp,
                        )
                    }
                    status.remaining_days?.let { days ->
                        Text(
                            text = stringResource(R.string.vpn5g_subscription_days, days),
                            color = Vpn5gOnDark,
                            fontSize = 15.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { (days / 30f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = Vpn5gAccentLight,
                            trackColor = Vpn5gOnDarkMuted.copy(alpha = 0.3f),
                        )
                    }
                    status.remaining_volume_gb?.let { volume ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.vpn5g_subscription_volume, volume),
                            color = Vpn5gOnDark,
                            fontSize = 15.sp,
                        )
                    }
                }
            }
        }

        // VPN settings (protocol) + advanced settings + about.
        Vpn5gStaggeredItem(index = 4, visible = entered) {
            Column(modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onOpenProtocol, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.vpn5g_profile_vpn_settings),
                        color = Vpn5gAccentLight,
                        fontSize = 14.sp,
                    )
                }
                TextButton(onClick = onOpenAdvanced, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.vpn5g_advanced_settings),
                        color = Vpn5gAccentLight,
                        fontSize = 14.sp,
                    )
                }
                Spacer(Modifier.height(8.dp))
                ProfileLinkRow(
                    label = stringResource(R.string.vpn5g_about_channel),
                    onClick = { onOpenUrl(CHANNEL_URL) },
                )
                ProfileLinkRow(
                    label = stringResource(R.string.vpn5g_about_bot),
                    onClick = { onOpenUrl(BOT_URL) },
                )
                ProfileLinkRow(
                    label = stringResource(R.string.vpn5g_about_support),
                    onClick = { onOpenUrl(SUPPORT_URL) },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.vpn5g_about_version,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE,
                    ),
                    color = Vpn5gOnDarkMuted,
                    fontSize = 14.sp,
                )
                Text(
                    text = stringResource(
                        R.string.vpn5g_about_build_date,
                        BuildConfig.BUILD_TIME,
                    ),
                    color = Vpn5gOnDarkMuted,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.vpn5g_about_core),
                    color = Vpn5gOnDarkMuted,
                    fontSize = 14.sp,
                )
            }
        }
        Vpn5gStaggeredItem(index = 5, visible = entered) {
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.vpn5g_nav_home),
                    color = Vpn5gAccentLight,
                    fontSize = 14.sp,
                )
            }
        }
        Vpn5gStaggeredItem(index = 6, visible = entered) {
            Vpn5gFooter()
        }
    }
}

@Composable
private fun ProfileLinkRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = label) { onClick() }
            .padding(vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = Vpn5gAccentLight,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
