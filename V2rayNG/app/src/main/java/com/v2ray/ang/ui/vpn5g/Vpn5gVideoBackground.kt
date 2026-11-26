package com.v2ray.ang.ui.vpn5g

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Looping video (v1.1.30, placements corrected in v1.1.31 per
 * Aref's orders): the entrance video plays on the 0→100% entry
 * loading screen («دقیقاً منظورم همون ۱ تا ۱۰۰ بود»), and the globe
 * video covers the whole screen while connecting («هنگام وصل زدن
 * اون ویدیو کامل بیاد رو صفحه... نه اینکه پس‌زمینه باشه»), ending
 * the moment the tunnel is CONNECTED (or fails/disconnects).
 *
 * Playback rules: muted, looping, center-crop fill (the video is
 * scaled to cover the whole area, overflow clipped). Paused when
 * the app leaves the foreground and released when the composable
 * leaves composition — it never keeps playing invisibly. Any
 * playback failure renders nothing, so whatever is behind it
 * (animated gradient / home UI) shows through (graceful fallback).
 */
@Composable
fun Vpn5gLoopingVideo(
    rawResId: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var videoView by remember { mutableStateOf<VideoView?>(null) }
    var prepared by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    // Real video size from the player (0 until prepared); the
    // center-crop scale is computed from it.
    var videoWidth by remember { mutableStateOf(0) }
    var videoHeight by remember { mutableStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> videoView?.pause()
                Lifecycle.Event.ON_RESUME -> if (prepared) videoView?.start()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            videoView?.stopPlayback()
            videoView = null
        }
    }

    if (failed) return

    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val density = LocalDensity.current
        // Center-crop fill (v1.1.32, Aref's order «ویدیو وصل شدن هم
        // زوم کن تمام صفحه باشه حتما» + the entrance video «تمام صفحه
        // باشه اونم»): scale the video so it COVERS the whole box on
        // every screen ratio — no letterbox, no empty edges — centered,
        // overflow clipped. `requiredSize` is essential: a plain
        // `size` is coerced into the parent's max constraints, so the
        // view could never overflow the box and VideoView then
        // letterboxed inside it (the empty edges Aref saw).
        val contentModifier = if (videoWidth > 0 && videoHeight > 0) {
            val boxW = with(density) { maxWidth.toPx() }
            val boxH = with(density) { maxHeight.toPx() }
            val scale = maxOf(boxW / videoWidth, boxH / videoHeight)
            Modifier
                .requiredSize(
                    width = with(density) { (videoWidth * scale).toDp() },
                    height = with(density) { (videoHeight * scale).toDp() },
                )
                .align(Alignment.Center)
        } else {
            Modifier.fillMaxSize()
        }
        AndroidView(
            modifier = contentModifier,
            factory = { ctx ->
                VideoView(ctx).also { view ->
                    videoView = view
                    view.setVideoURI(
                        Uri.parse("android.resource://${ctx.packageName}/$rawResId")
                    )
                    view.setOnPreparedListener { player ->
                        player.isLooping = true
                        player.setVolume(0f, 0f)
                        videoWidth = player.videoWidth
                        videoHeight = player.videoHeight
                        // Some devices report a 0 size at prepare time
                        // and deliver the real size here — without
                        // this the center-crop scale would never
                        // engage and the video would stay letterboxed
                        // (v1.1.32). The listener lives on the
                        // MediaPlayer, not on VideoView.
                        player.setOnVideoSizeChangedListener { _, width, height ->
                            if (width > 0 && height > 0) {
                                videoWidth = width
                                videoHeight = height
                            }
                        }
                        prepared = true
                        view.start()
                    }
                    view.setOnErrorListener { _, _, _ ->
                        // Fallback: render nothing — the animated
                        // background behind shows through.
                        failed = true
                        true
                    }
                }
            },
        )
    }
}
