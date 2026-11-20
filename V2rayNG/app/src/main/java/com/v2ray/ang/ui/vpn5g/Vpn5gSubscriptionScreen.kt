package com.v2ray.ang.ui.vpn5g

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R

/**
 * Subscription status screen.
 *
 * Shows ONLY what the remote app_config.json provides — numbers are never
 * invented. The days bar is measured against a 30-day cycle and labelled as
 * such; volume is shown as text (no total is published by the server).
 */
@Composable
fun Vpn5gSubscriptionScreen(
    state: Vpn5gHomeUiState,
    onBack: () -> Unit,
    onOpenBot: () -> Unit,
    onOpenAuth: () -> Unit,
) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Vpn5gStaggeredItem(index = 0, visible = entered) {
            Text(
                text = stringResource(R.string.vpn5g_subscription_title),
                color = Vpn5gOnDark,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        val status = state.subscriptionStatus
        if (status == null) {
            Vpn5gStaggeredItem(index = 1, visible = entered) {
                Column {
                    Text(
                        text = stringResource(R.string.vpn5g_subscription_none),
                        color = Vpn5gOnDark,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.vpn5g_subscription_none_body),
                        color = Vpn5gOnDarkMuted,
                        fontSize = 14.sp,
                    )
                }
            }
        } else {
            Vpn5gStaggeredItem(index = 1, visible = entered) {
                Column {
                    status.plan_name?.let { plan ->
                        Text(
                            text = stringResource(R.string.vpn5g_subscription_plan, plan),
                            color = Vpn5gOnDark,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    status.remaining_days?.let { days ->
                        Text(
                            text = stringResource(R.string.vpn5g_subscription_days, days),
                            color = Vpn5gOnDark,
                            fontSize = 15.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        // Days bar vs a 30-day cycle (labelled; the day count
                        // itself comes from the server, never invented).
                        LinearProgressIndicator(
                            progress = { (days / 30f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = Vpn5gAccentLight,
                            trackColor = Vpn5gOnDarkMuted.copy(alpha = 0.3f),
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    status.remaining_volume_gb?.let { volume ->
                        Text(
                            text = stringResource(R.string.vpn5g_subscription_volume, volume),
                            color = Vpn5gOnDark,
                            fontSize = 15.sp,
                        )
                    }
                }
            }
        }

        Vpn5gStaggeredItem(index = 2, visible = entered) {
            Button(
                onClick = onOpenBot,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Vpn5gAccent,
                    contentColor = Color.White,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.vpn5g_open_bot), fontSize = 16.sp)
            }
        }
        Vpn5gStaggeredItem(index = 3, visible = entered) {
            TextButton(onClick = onOpenAuth, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.vpn5g_auth_title),
                    color = Vpn5gAccentLight,
                    fontSize = 14.sp,
                )
            }
        }
        Vpn5gStaggeredItem(index = 4, visible = entered) {
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.vpn5g_nav_home),
                    color = Vpn5gAccentLight,
                    fontSize = 14.sp,
                )
            }
        }
    }
}
