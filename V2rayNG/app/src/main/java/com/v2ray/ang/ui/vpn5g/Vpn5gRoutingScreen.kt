package com.v2ray.ang.ui.vpn5g

import android.content.Intent
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.v2ray.ang.R
import com.v2ray.ang.ui.perappproxy.PerAppProxyActivity
import com.v2ray.ang.ui.perappproxy.PerAppProxyViewModel

/**
 * Routing screen ("مسیریابی"): per-app tunnel settings.
 *
 * Wired to the SAME settings the VPN service reads
 * ([PerAppProxyViewModel]: PREF_PER_APP_PROXY, PREF_BYPASS_APPS,
 * PREF_PER_APP_PROXY_SET) — no parallel state. App selection itself opens
 * the upstream PerAppProxyActivity; this screen only owns the enable switch
 * and the proxy/bypass mode. A change applies on the next connect.
 */
@Composable
fun Vpn5gRoutingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val perAppViewModel: PerAppProxyViewModel = viewModel()
    val perAppEnabled by perAppViewModel.perAppProxyEnabled.collectAsStateWithLifecycle()
    val bypassApps by perAppViewModel.bypassApps.collectAsStateWithLifecycle()
    val blacklist by perAppViewModel.blacklist.collectAsStateWithLifecycle()

    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        entered = true
        perAppViewModel.loadApps(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Vpn5gStaggeredItem(index = 0, visible = entered) {
            Text(
                text = stringResource(R.string.vpn5g_routing_title),
                color = Vpn5gOnDark,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Vpn5gStaggeredItem(index = 1, visible = entered) {
            Text(
                text = stringResource(R.string.vpn5g_routing_body),
                color = Vpn5gOnDarkMuted,
                fontSize = 14.sp,
            )
        }
        Vpn5gStaggeredItem(index = 2, visible = entered) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.vpn5g_routing_enable),
                        color = Vpn5gOnDark,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.vpn5g_routing_enable_body),
                        color = Vpn5gOnDarkMuted,
                        fontSize = 13.sp,
                    )
                }
                Switch(
                    checked = perAppEnabled,
                    onCheckedChange = { perAppViewModel.setPerAppProxyEnabled(it) },
                )
            }
        }
        if (perAppEnabled) {
            Vpn5gStaggeredItem(index = 3, visible = entered) {
                Column {
                    Text(
                        text = stringResource(R.string.vpn5g_routing_mode_title),
                        color = Vpn5gOnDark,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.vpn5g_routing_mode_bypass),
                                color = Vpn5gOnDark,
                                fontSize = 15.sp,
                            )
                            Text(
                                text = if (bypassApps) {
                                    stringResource(R.string.vpn5g_routing_mode_bypass_body)
                                } else {
                                    stringResource(R.string.vpn5g_routing_mode_proxy_body)
                                },
                                color = Vpn5gOnDarkMuted,
                                fontSize = 13.sp,
                            )
                        }
                        Switch(
                            checked = bypassApps,
                            onCheckedChange = { perAppViewModel.setBypassAppsEnabled(it) },
                        )
                    }
                }
            }
            Vpn5gStaggeredItem(index = 4, visible = entered) {
                Button(
                    onClick = {
                        context.startActivity(Intent(context, PerAppProxyActivity::class.java))
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Vpn5gAccent,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.vpn5g_routing_choose_apps, blacklist.size),
                        fontSize = 16.sp,
                    )
                }
            }
            Vpn5gStaggeredItem(index = 5, visible = entered) {
                Text(
                    text = stringResource(R.string.vpn5g_routing_apply_note),
                    color = Vpn5gOnDarkMuted,
                    fontSize = 13.sp,
                )
            }
        }
        Vpn5gStaggeredItem(index = 6, visible = entered) {
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
