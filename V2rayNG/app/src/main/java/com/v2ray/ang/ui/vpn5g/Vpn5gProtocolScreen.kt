package com.v2ray.ang.ui.vpn5g

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.handler.vpn5g.Vpn5gProtocol

/**
 * Protocol picker (v1.1), modeled on the X-VPN protocol screen: Auto plus
 * only the protocols this app's V2Ray core actually supports (VLESS /
 * VMess / Trojan / Shadowsocks). Auto is the recommended default — the
 * server list and Auto-connect use every protocol. Picking one protocol
 * filters the home list and Auto-connect to servers of that protocol.
 */
@Composable
fun Vpn5gProtocolScreen(
    currentProtocol: String,
    onSelect: (String) -> Unit,
    onBack: () -> Unit,
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
            Text(
                text = stringResource(R.string.vpn5g_protocol_title),
                color = Vpn5gOnDark,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        val rows = listOf(
            Triple(
                Vpn5gProtocol.AUTO,
                R.string.vpn5g_protocol_auto,
                R.string.vpn5g_protocol_auto_body,
            ),
            Triple(
                Vpn5gProtocol.VLESS,
                R.string.vpn5g_protocol_vless,
                R.string.vpn5g_protocol_vless_body,
            ),
            Triple(
                Vpn5gProtocol.VMESS,
                R.string.vpn5g_protocol_vmess,
                R.string.vpn5g_protocol_vmess_body,
            ),
            Triple(
                Vpn5gProtocol.TROJAN,
                R.string.vpn5g_protocol_trojan,
                R.string.vpn5g_protocol_trojan_body,
            ),
            Triple(
                Vpn5gProtocol.SHADOWSOCKS,
                R.string.vpn5g_protocol_ss,
                R.string.vpn5g_protocol_ss_body,
            ),
        )
        Vpn5gStaggeredItem(index = 1, visible = entered) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Vpn5gOnDark.copy(alpha = 0.06f))
                    .padding(vertical = 4.dp),
            ) {
                rows.forEachIndexed { index, (id, nameRes, bodyRes) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                role = Role.Button,
                                onClickLabel = stringResource(nameRes),
                            ) { onSelect(id) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(nameRes),
                                    color = if (id == Vpn5gProtocol.AUTO) Vpn5gCyan else Vpn5gOnDark,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                if (id == Vpn5gProtocol.AUTO) {
                                    Spacer(Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Vpn5gCyan.copy(alpha = 0.2f))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = stringResource(R.string.vpn5g_protocol_recommended),
                                            color = Vpn5gCyan,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = stringResource(bodyRes),
                                color = Vpn5gOnDarkMuted,
                                fontSize = 13.sp,
                            )
                        }
                        if (currentProtocol == id) {
                            Spacer(Modifier.width(8.dp))
                            Vpn5gCheckIcon(color = Vpn5gCyan, size = 24.dp)
                        }
                    }
                    if (index < rows.lastIndex) {
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Vpn5gOnDark.copy(alpha = 0.08f))
                        )
                    }
                }
            }
        }

        Vpn5gStaggeredItem(index = 2, visible = entered) {
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.vpn5g_nav_me),
                    color = Vpn5gAccentLight,
                    fontSize = 14.sp,
                )
            }
        }
    }
}
