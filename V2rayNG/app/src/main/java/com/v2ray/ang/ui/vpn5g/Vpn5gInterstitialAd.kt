package com.v2ray.ang.ui.vpn5g

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.v2ray.ang.R
import com.v2ray.ang.handler.vpn5g.Vpn5gAds
import kotlinx.coroutines.delay

/**
 * Full-screen interstitial ad (v1.1.15).
 *
 * Rules (Aref's master spec):
 *  - the VPN starts ONLY after the ad is FULLY viewed: the countdown
 *    (admin-set, 1–20 s) must complete. There is NO skip button
 *    before it ends, and after it ends the connect starts
 *    AUTOMATICALLY ([onComplete] fires once).
 *  - closing the ad early (system Back, handled by the Activity) is
 *    an INCOMPLETE view: [onIncomplete] fires and the connection
 *    must NOT start.
 *  - the countdown runs on the MONOTONIC clock
 *    (SystemClock.elapsedRealtime): [Vpn5gAds.remainingSeconds]
 *    recomputes from the deadline fixed at show time, so changing
 *    the phone's time cannot shorten the ad, and backgrounding the
 *    app does not pause or reset it.
 *  - tapping the ad content opens its link ([onAdClick]) WITHOUT
 *    ending the countdown; the click is recorded server-side
 *    separately from the view.
 *  - ads never crash the app: this screen renders from the resolved
 *    ad only, and a missing image hides the image slot while the
 *    text still shows. The server verification (record_ad_view) runs
 *    best-effort in the ViewModel — if the ad server is off, the
 *    local countdown completion still starts the VPN.
 */
@Composable
fun Vpn5gInterstitialAdScreen(
    ad: Vpn5gAds.InterstitialAdUi,
    onAdClick: (String) -> Unit,
    onComplete: () -> Unit,
    onIncomplete: () -> Unit,
) {
    // Deadline fixed once at show time, on the monotonic clock.
    val deadlineRealtimeMs = remember(ad.id) {
        SystemClock.elapsedRealtime() + ad.durationSeconds * 1000L
    }
    var remainingSeconds by remember { mutableLongStateOf(ad.durationSeconds.toLong()) }
    var completed by remember { mutableStateOf(false) }
    var showImage by remember { mutableStateOf(!ad.imageUrl.isNullOrBlank()) }
    // Local capture: a property smart-cast does not survive into the
    // clickable lambda below, so the link is read once here.
    val adLink = ad.linkUrl
    LaunchedEffect(ad.id) {
        while (!completed) {
            val remaining = Vpn5gAds.remainingSeconds(
                deadlineRealtimeMs,
                SystemClock.elapsedRealtime(),
                ad.durationSeconds,
            )
            remainingSeconds = remaining
            if (remaining <= 0L) {
                completed = true
                onComplete()
                break
            }
            delay(200L)
        }
    }
    Vpn5gAnimatedBackground {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 48.dp),
        ) {
            Spacer(Modifier.weight(1f))
            Vpn5gGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (!adLink.isNullOrBlank()) {
                            Modifier
                                .semantics(mergeDescendants = true) {}
                                .clickable(
                                    onClickLabel = stringResource(
                                        R.string.vpn5g_open_link,
                                    ),
                                ) { onAdClick(adLink) }
                        } else {
                            Modifier
                        }
                    ),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.vpn5g_ad_title),
                        color = Vpn5gOnDark,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    if (showImage) {
                        AsyncImage(
                            model = ad.imageUrl.orEmpty(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            onError = { showImage = false },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    if (!ad.text.isNullOrBlank()) {
                        Text(
                            text = ad.text,
                            color = Vpn5gOnDark,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    // Countdown: big number + bar. No skip before it ends.
                    Text(
                        text = stringResource(
                            R.string.vpn5g_ad_countdown,
                            remainingSeconds,
                        ),
                        color = Vpn5gElectricBlue,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = {
                            (remainingSeconds.toFloat() / ad.durationSeconds)
                                .coerceIn(0f, 1f)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color = Vpn5gElectricBlue,
                        trackColor = Vpn5gOnDark.copy(alpha = 0.15f),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (completed) {
                            stringResource(R.string.vpn5g_ad_starting)
                        } else {
                            stringResource(R.string.vpn5g_ad_watch_full)
                        },
                        color = Vpn5gOnDarkMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            // Early close is an incomplete view: the connection must
            // NOT start. There is deliberately no skip button here.
            TextButton(
                onClick = onIncomplete,
            ) {
                Text(
                    text = stringResource(R.string.vpn5g_close),
                    color = Vpn5gOnDarkMuted,
                    fontSize = 14.sp,
                )
            }
        }
    }
}
