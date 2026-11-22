package com.v2ray.ang.ui.vpn5g

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R

/**
 * Reusable expandable section (v1.1.5 §4): one header row and one
 * animated body, used the same way on every screen — free servers,
 * country, server, auto, subscription status, account features, guide,
 * FAQ, support, per-feature info. No dead/demo menus: every section
 * really opens and its content really works.
 *
 * The header is a single semantic node when it only toggles ([onHeaderClick]
 * is null). When the header also performs a primary action (e.g. the
 * "Auto" row connects on tap), the title area runs that action and the
 * chevron is a separate node that toggles the body.
 */
@Composable
fun Vpn5gExpandableSection(
    title: String,
    subtitle: String? = null,
    badge: String? = null,
    leading: @Composable (() -> Unit)? = null,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onHeaderClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Vpn5gOnDark.copy(alpha = 0.06f)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onHeaderClick == null) {
                        Modifier.clickable(
                            role = Role.Button,
                            onClickLabel = title,
                        ) { onToggleExpand() }
                    } else {
                        Modifier
                    }
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            val headerBody: @Composable () -> Unit = {
                if (leading != null) {
                    leading()
                    Spacer(Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = Vpn5gOnDark,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            color = Vpn5gOnDarkMuted,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (!badge.isNullOrBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = badge,
                        color = Vpn5gOnDarkMuted,
                        fontSize = 13.sp,
                    )
                }
            }
            if (onHeaderClick != null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = title,
                        ) { onHeaderClick() },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        headerBody()
                    }
                }
            } else {
                headerBody()
            }
            Spacer(Modifier.width(8.dp))
            // Chevron: rotates 180° when open. A separate node only when
            // the header has its own primary action; otherwise the whole
            // header toggles and the chevron is decorative.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .then(
                        if (onHeaderClick != null) {
                            Modifier
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = stringResource(
                                        if (expanded) R.string.vpn5g_collapse_details
                                        else R.string.vpn5g_expand_details
                                    ),
                                ) { onToggleExpand() }
                                .semantics { contentDescription = title }
                        } else {
                            Modifier
                        }
                    ),
            ) {
                Vpn5gChevronIcon(
                    color = Vpn5gOnDarkMuted,
                    modifier = Modifier.graphicsLayer {
                        // Same convention as the country sections: "›"
                        // closed, "⌄" open.
                        rotationZ = if (expanded) 90f else 0f
                    },
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
            ) {
                content()
            }
        }
    }
}
