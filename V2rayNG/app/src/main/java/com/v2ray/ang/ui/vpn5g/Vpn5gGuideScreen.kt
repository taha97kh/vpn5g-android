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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants

/** In-app guide + quick links (the "Browser" tab): how to connect, import a config, and recover from a dead server. */
@Composable
fun Vpn5gGuideScreen(
    onBack: () -> Unit,
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
    ) {
        Vpn5gStaggeredItem(index = 0, visible = entered) {
            Text(
                text = stringResource(R.string.vpn5g_guide_title),
                color = Vpn5gOnDark,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        listOf(
            R.string.vpn5g_guide_step1_title to R.string.vpn5g_guide_step1_body,
            R.string.vpn5g_guide_step2_title to R.string.vpn5g_guide_step2_body,
            R.string.vpn5g_guide_step3_title to R.string.vpn5g_guide_step3_body,
            R.string.vpn5g_guide_step4_title to R.string.vpn5g_guide_step4_body,
        ).forEachIndexed { i, (title, body) ->
            Vpn5gStaggeredItem(index = i + 1, visible = entered) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(title),
                        color = Vpn5gOnDark,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(body),
                        color = Vpn5gOnDarkMuted,
                        fontSize = 14.sp,
                    )
                }
            }
        }
        Vpn5gStaggeredItem(index = 5, visible = entered) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.vpn5g_guide_links_title),
                    color = Vpn5gOnDark,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                GuideLinkRow(
                    label = stringResource(R.string.vpn5g_about_bot),
                    onClick = { onOpenUrl(Vpn5gConstants.BOT_URL) },
                )
                GuideLinkRow(
                    label = stringResource(R.string.vpn5g_about_channel),
                    onClick = { onOpenUrl(Vpn5gConstants.CHANNEL_URL) },
                )
                GuideLinkRow(
                    label = stringResource(R.string.vpn5g_about_support),
                    onClick = { onOpenUrl(Vpn5gConstants.SUPPORT_URL) },
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

@Composable
private fun GuideLinkRow(label: String, onClick: () -> Unit) {
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
