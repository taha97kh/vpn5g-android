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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.handler.vpn5g.Vpn5gConstants

/**
 * In-app guide (the "Guide" tab, v1.1.5 §5): twenty expandable Persian
 * sections describing exactly what the app really does — never a feature
 * that does not exist — plus a separate FAQ (tap a question, the answer
 * opens under it) and the three support links in the last section.
 *
 * Expansion state is transient presentation state only. The stagger
 * delay is capped so the last of the 28 rows does not wait seconds to
 * appear.
 */
@Composable
fun Vpn5gGuideScreen(
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    /** v1.1.21: the features-preview card opens the Features page. */
    onOpenFeatures: () -> Unit,
) {
    var entered by remember { mutableStateOf(false) }
    var expandedSections by remember { mutableStateOf(setOf<Int>()) }
    var expandedFaq by remember { mutableStateOf(setOf<Int>()) }
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
        // Features preview (v1.1.21, Lantern): a card linking to the
        // Features page — the same real features, listed there.
        Vpn5gStaggeredItem(index = 1, visible = entered) {
            Vpn5gGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.vpn5g_features_title),
                    ) { onOpenFeatures() },
            ) {
                Text(
                    text = stringResource(R.string.vpn5g_features_title),
                    color = Vpn5gOnDark,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.vpn5g_features_headline),
                    color = Vpn5gOnDarkMuted,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.vpn5g_features_more),
                        color = Vpn5gTealLight,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Vpn5gChevronIcon(color = Vpn5gTealLight)
                }
            }
        }
        guideSections.forEachIndexed { i, (titleRes, bodyRes) ->
            // Stagger delay caps at 500 ms (index 5); the 20 sections
            // must not make the last row wait seconds to appear.
            Vpn5gStaggeredItem(index = minOf(i + 1, 5), visible = entered) {
                val expanded = i in expandedSections
                val toggle: () -> Unit = {
                    expandedSections =
                        if (expanded) expandedSections - i else expandedSections + i
                }
                if (i == guideSections.lastIndex) {
                    // Last section (support): the three real links live
                    // inside the expandable body, nothing else under them.
                    Vpn5gExpandableSection(
                        title = stringResource(titleRes),
                        expanded = expanded,
                        onToggleExpand = toggle,
                    ) {
                        Text(
                            text = stringResource(bodyRes),
                            color = Vpn5gOnDarkMuted,
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        GuideLinkRow(
                            label = stringResource(R.string.vpn5g_link_channel),
                            onClick = { onOpenUrl(Vpn5gConstants.CHANNEL_URL) },
                        )
                        GuideLinkRow(
                            label = stringResource(R.string.vpn5g_link_bot),
                            onClick = { onOpenUrl(Vpn5gConstants.BOT_URL) },
                        )
                        GuideLinkRow(
                            label = stringResource(R.string.vpn5g_link_support),
                            // v1.1.14: support goes DIRECTLY to the bot's
                            // support section (start=support); the bot
                            // handles membership (not verifiable app-side).
                            onClick = { onOpenUrl(Vpn5gConstants.SUPPORT_BOT_URL) },
                        )
                    }
                } else {
                    Vpn5gExpandableSection(
                        title = stringResource(titleRes),
                        expanded = expanded,
                        onToggleExpand = toggle,
                    ) {
                        Text(
                            text = stringResource(bodyRes),
                            color = Vpn5gOnDarkMuted,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
        Vpn5gStaggeredItem(index = 5, visible = entered) {
            Text(
                text = stringResource(R.string.vpn5g_guide_faq_title),
                color = Vpn5gOnDark,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        guideFaq.forEachIndexed { i, (questionRes, answerRes) ->
            Vpn5gStaggeredItem(index = 5, visible = entered) {
                val expanded = i in expandedFaq
                Vpn5gExpandableSection(
                    title = stringResource(questionRes),
                    expanded = expanded,
                    onToggleExpand = {
                        expandedFaq =
                            if (expanded) expandedFaq - i else expandedFaq + i
                    },
                ) {
                    Text(
                        text = stringResource(answerRes),
                        color = Vpn5gOnDarkMuted,
                        fontSize = 14.sp,
                    )
                }
            }
        }
        Vpn5gStaggeredItem(index = 5, visible = entered) {
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.vpn5g_nav_home),
                    color = Vpn5gTealLight,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

/** The twenty guide sections, in the order the spec lists them. */
private val guideSections = listOf(
    R.string.vpn5g_guide_s01_title to R.string.vpn5g_guide_s01_body,
    R.string.vpn5g_guide_s02_title to R.string.vpn5g_guide_s02_body,
    R.string.vpn5g_guide_s03_title to R.string.vpn5g_guide_s03_body,
    R.string.vpn5g_guide_s04_title to R.string.vpn5g_guide_s04_body,
    R.string.vpn5g_guide_s05_title to R.string.vpn5g_guide_s05_body,
    R.string.vpn5g_guide_s06_title to R.string.vpn5g_guide_s06_body,
    R.string.vpn5g_guide_s07_title to R.string.vpn5g_guide_s07_body,
    R.string.vpn5g_guide_s08_title to R.string.vpn5g_guide_s08_body,
    R.string.vpn5g_guide_s09_title to R.string.vpn5g_guide_s09_body,
    R.string.vpn5g_guide_s10_title to R.string.vpn5g_guide_s10_body,
    R.string.vpn5g_guide_s11_title to R.string.vpn5g_guide_s11_body,
    R.string.vpn5g_guide_s12_title to R.string.vpn5g_guide_s12_body,
    R.string.vpn5g_guide_s13_title to R.string.vpn5g_guide_s13_body,
    R.string.vpn5g_guide_s14_title to R.string.vpn5g_guide_s14_body,
    R.string.vpn5g_guide_s15_title to R.string.vpn5g_guide_s15_body,
    R.string.vpn5g_guide_s16_title to R.string.vpn5g_guide_s16_body,
    R.string.vpn5g_guide_s17_title to R.string.vpn5g_guide_s17_body,
    R.string.vpn5g_guide_s18_title to R.string.vpn5g_guide_s18_body,
    R.string.vpn5g_guide_s19_title to R.string.vpn5g_guide_s19_body,
    R.string.vpn5g_guide_s20_title to R.string.vpn5g_guide_s20_body,
)

/** Frequently asked questions, each with its answer. */
private val guideFaq = listOf(
    R.string.vpn5g_faq_q01 to R.string.vpn5g_faq_a01,
    R.string.vpn5g_faq_q02 to R.string.vpn5g_faq_a02,
    R.string.vpn5g_faq_q03 to R.string.vpn5g_faq_a03,
    R.string.vpn5g_faq_q04 to R.string.vpn5g_faq_a04,
    R.string.vpn5g_faq_q05 to R.string.vpn5g_faq_a05,
    R.string.vpn5g_faq_q06 to R.string.vpn5g_faq_a06,
    R.string.vpn5g_faq_q07 to R.string.vpn5g_faq_a07,
    R.string.vpn5g_faq_q08 to R.string.vpn5g_faq_a08,
    R.string.vpn5g_faq_q09 to R.string.vpn5g_faq_a09,
    R.string.vpn5g_faq_q10 to R.string.vpn5g_faq_a10,
)

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
            color = Vpn5gTealLight,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
