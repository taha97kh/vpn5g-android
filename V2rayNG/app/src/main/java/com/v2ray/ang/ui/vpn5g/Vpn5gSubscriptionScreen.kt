package com.v2ray.ang.ui.vpn5g

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.handler.vpn5g.SubscriptionPlanRemote

/**
 * Premium / subscription screen.
 *
 * Shows ONLY what the remote app_config.json provides — numbers are never
 * invented. The days bar is measured against a 30-day cycle and labelled as
 * such; volume is shown as text (no total is published by the server).
 *
 * Plans, prices, the 24h free trial, and the bank gateway are ALL driven
 * by app_config.json (bot-managed): when the bot publishes no plans, no
 * plan cards appear; when the gateway is not configured, its row is shown
 * as coming soon and routes to the bot. The app never hardcodes a plan,
 * price, or gateway value, and never mints a subscription by itself —
 * the bot applies every purchase, trial, and promo code centrally.
 *
 * Promo codes: the user enters a code issued by the admin through the
 * Telegram bot; the app validates it against the code list fetched in
 * app_config.json and records the redemption locally. The bot applies the
 * benefit centrally (the app never mints a subscription by itself).
 */
@Composable
fun Vpn5gSubscriptionScreen(
    state: Vpn5gHomeUiState,
    onBack: () -> Unit,
    onOpenBot: () -> Unit,
    onOpenAuth: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onPromoInputChange: (String) -> Unit,
    onRedeemPromo: () -> Unit,
    onOpenPromoBot: () -> Unit,
) {
    var entered by remember { mutableStateOf(false) }
    // Section expansion is transient presentation state only (v1.1.5
    // §4). All sections start open, as before, except the long
    // comparison table, which starts closed.
    var statusExpanded by remember { mutableStateOf(true) }
    var comparisonExpanded by remember { mutableStateOf(false) }
    var plansExpanded by remember { mutableStateOf(true) }
    var paymentExpanded by remember { mutableStateOf(true) }
    var promoExpanded by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { entered = true }

    // Plans as published by the bot (valid entries only: id + price + a
    // known period). Yearly is the default selection, like the reference.
    val plans = state.plans
        ?.filter {
            !it.id.isNullOrBlank() && it.price_toman != null &&
                (it.period == "monthly" || it.period == "yearly")
        }
        ?.sortedBy { if (it.period == "yearly") 0 else 1 }
        ?: emptyList()
    var selectedPlanId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedPlan = plans.firstOrNull { it.id == selectedPlanId }
        ?: plans.firstOrNull { it.period == "yearly" }
        ?: plans.firstOrNull()
    var showPayment by rememberSaveable { mutableStateOf(false) }
    val gateway = state.paymentGateway
    val gatewayReady = gateway?.enabled == true && !gateway.url.isNullOrBlank()
    // Real live counts, per server: free countries are the countries
    // with at least one free server; free servers are the top-20
    // fastest configs. Never count a whole section as free.
    val freeCountryCount = state.countries.count { section ->
        section.servers.any { it.isFree }
    }
    val totalCountryCount = state.countries.size
    val freeServerCount = state.countries.sumOf { section ->
        section.servers.count { it.isFree }
    }
    val totalServerCount = state.countries.sumOf { it.servers.size }

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
        Vpn5gStaggeredItem(index = 1, visible = entered) {
            Vpn5gExpandableSection(
                title = stringResource(R.string.vpn5g_subscription_title),
                expanded = statusExpanded,
                onToggleExpand = { statusExpanded = !statusExpanded },
            ) {
                if (status == null) {
                    Text(
                        text = stringResource(R.string.vpn5g_subscription_none),
                        color = Vpn5gOnDark,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.vpn5g_subscription_none_body),
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

        // Free vs Premium comparison (counts come from the live list).
        Vpn5gStaggeredItem(index = 2, visible = entered) {
            Vpn5gExpandableSection(
                title = stringResource(R.string.vpn5g_premium_title),
                expanded = comparisonExpanded,
                onToggleExpand = { comparisonExpanded = !comparisonExpanded },
            ) {
                Vpn5gCompareRow(
                    feature = "",
                    free = stringResource(R.string.vpn5g_premium_free),
                    premium = stringResource(R.string.vpn5g_premium_name),
                    header = true,
                )
                Vpn5gCompareRow(
                    feature = stringResource(R.string.vpn5g_premium_speed),
                    free = stringResource(R.string.vpn5g_premium_speed_free),
                    premium = stringResource(R.string.vpn5g_premium_speed_premium),
                )
                Vpn5gCompareRow(
                    feature = stringResource(R.string.vpn5g_premium_locations),
                    free = stringResource(
                        R.string.vpn5g_premium_locations_free, freeCountryCount
                    ),
                    premium = stringResource(
                        R.string.vpn5g_premium_locations_premium, totalCountryCount
                    ),
                )
                Vpn5gCompareRow(
                    feature = stringResource(R.string.vpn5g_premium_servers),
                    free = stringResource(
                        R.string.vpn5g_premium_servers_free, freeServerCount
                    ),
                    premium = stringResource(
                        R.string.vpn5g_premium_servers_premium, totalServerCount
                    ),
                )
                Vpn5gCompareRow(
                    feature = stringResource(R.string.vpn5g_premium_no_ads),
                    free = "✗",
                    premium = "✓",
                )
                Vpn5gCompareRow(
                    feature = stringResource(R.string.vpn5g_premium_support),
                    free = "✗",
                    premium = "✓",
                )
            }
        }

        // Plans from remote config (never hardcoded). No plans published
        // means no cards — the user buys through the bot instead.
        Vpn5gStaggeredItem(index = 3, visible = entered) {
            Vpn5gExpandableSection(
                title = stringResource(R.string.vpn5g_premium_plans_title),
                expanded = plansExpanded,
                onToggleExpand = { plansExpanded = !plansExpanded },
            ) {
                if (plans.isEmpty()) {
                    Text(
                        text = stringResource(R.string.vpn5g_premium_plans_none),
                        color = Vpn5gOnDarkMuted,
                        fontSize = 14.sp,
                    )
                } else {
                    plans.forEach { plan ->
                        Vpn5gPlanCard(
                            plan = plan,
                            selected = plan.id == selectedPlan?.id,
                            trialEnabled = state.trialEnabled,
                            trialHours = state.trialHours,
                            onSelect = { selectedPlanId = plan.id },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    if (state.trialEnabled) {
                        Text(
                            text = stringResource(R.string.vpn5g_premium_trial_note),
                            color = Vpn5gOnDarkMuted,
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(
                        onClick = { showPayment = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Vpn5gCyan,
                            contentColor = Color.White,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.vpn5g_premium_continue), fontSize = 16.sp)
                    }
                }
            }
        }

        // Payment methods for the selected plan.
        if (showPayment && plans.isNotEmpty()) {
            Vpn5gStaggeredItem(index = 4, visible = entered) {
                Vpn5gExpandableSection(
                    title = stringResource(R.string.vpn5g_premium_payment_title),
                    expanded = paymentExpanded,
                    onToggleExpand = { paymentExpanded = !paymentExpanded },
                ) {
                    // Direct bank gateway (config-driven). While it is not
                    // configured the row is shown as coming soon and routes
                    // to the bot to complete the purchase manually.
                    Vpn5gPaymentRow(
                        title = gateway?.name_fa
                            ?: stringResource(R.string.vpn5g_premium_pay_gateway),
                        body = stringResource(R.string.vpn5g_premium_pay_gateway_body),
                        badge = if (gatewayReady) {
                            null
                        } else {
                            stringResource(R.string.vpn5g_premium_coming_soon)
                        },
                        onClick = {
                            val url = gateway?.url
                            if (gatewayReady && !url.isNullOrBlank()) {
                                onOpenUrl(url)
                            } else {
                                onOpenBot()
                            }
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    Vpn5gPaymentRow(
                        title = stringResource(R.string.vpn5g_premium_pay_bot),
                        body = stringResource(R.string.vpn5g_premium_pay_bot_body),
                        badge = null,
                        onClick = onOpenBot,
                    )
                }
            }
        }

        Vpn5gStaggeredItem(index = 5, visible = entered) {
            Vpn5gExpandableSection(
                title = stringResource(R.string.vpn5g_promo_title),
                expanded = promoExpanded,
                onToggleExpand = { promoExpanded = !promoExpanded },
            ) {
                Text(
                    text = stringResource(R.string.vpn5g_promo_body),
                    color = Vpn5gOnDarkMuted,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.promoInput,
                    onValueChange = onPromoInputChange,
                    label = { Text(stringResource(R.string.vpn5g_promo_code)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    colors = vpn5gPromoFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onRedeemPromo,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Vpn5gAccent,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.vpn5g_promo_redeem), fontSize = 16.sp)
                }
                when (state.promoStatus) {
                    Vpn5gPromoStatus.VALID -> {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.vpn5g_promo_valid),
                            color = Color(0xFF4ADE80),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = onOpenPromoBot,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Vpn5gAccent,
                                contentColor = Color.White,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = stringResource(R.string.vpn5g_promo_confirm_bot), fontSize = 16.sp)
                        }
                    }
                    Vpn5gPromoStatus.UNKNOWN_CODE -> {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.vpn5g_promo_unknown),
                            color = Color(0xFFF87171),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Vpn5gPromoStatus.EXHAUSTED -> {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.vpn5g_promo_exhausted),
                            color = Color(0xFFF87171),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Vpn5gPromoStatus.ALREADY_REDEEMED -> {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.vpn5g_promo_already),
                            color = Color(0xFFF87171),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Vpn5gPromoStatus.INVALID -> {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.vpn5g_promo_invalid),
                            color = Color(0xFFF87171),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Vpn5gPromoStatus.IDLE -> Unit
                }
            }
        }

        Vpn5gStaggeredItem(index = 6, visible = entered) {
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
        Vpn5gStaggeredItem(index = 7, visible = entered) {
            TextButton(onClick = onOpenAuth, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.vpn5g_auth_title),
                    color = Vpn5gAccentLight,
                    fontSize = 14.sp,
                )
            }
        }
        Vpn5gStaggeredItem(index = 8, visible = entered) {
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
private fun Vpn5gCompareRow(
    feature: String,
    free: String,
    premium: String,
    header: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text(
            text = feature,
            color = Vpn5gOnDark,
            fontSize = 14.sp,
            fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1.2f),
        )
        Text(
            text = free,
            color = Vpn5gOnDarkMuted,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = premium,
            color = if (header) Vpn5gCyan else Vpn5gOnDark,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Vpn5gPlanCard(
    plan: SubscriptionPlanRemote,
    selected: Boolean,
    trialEnabled: Boolean,
    trialHours: Int,
    onSelect: () -> Unit,
) {
    val name = plan.name_fa?.takeIf { it.isNotBlank() }
        ?: stringResource(
            if (plan.period == "yearly") R.string.vpn5g_premium_plan_yearly
            else R.string.vpn5g_premium_plan_monthly
        )
    val priceText = stringResource(
        R.string.vpn5g_premium_price,
        "%,d".format(plan.price_toman ?: 0L),
        stringResource(
            if (plan.period == "yearly") R.string.vpn5g_premium_per_year
            else R.string.vpn5g_premium_per_month
        ),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Vpn5gCyan else Vpn5gOnDarkMuted.copy(alpha = 0.4f),
                shape = RoundedCornerShape(16.dp),
            )
            .background(Vpn5gOnDark.copy(alpha = 0.06f))
            .clickable(role = Role.Button, onClickLabel = name) { onSelect() }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name,
                color = Vpn5gCyan,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (plan.period == "yearly") {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Vpn5gCyan)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = stringResource(R.string.vpn5g_premium_most_popular),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = priceText,
            color = Vpn5gOnDark,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        if (trialEnabled) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.vpn5g_premium_trial, trialHours),
                color = Vpn5gOnDarkMuted,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun Vpn5gPaymentRow(
    title: String,
    body: String,
    badge: String?,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Vpn5gOnDark.copy(alpha = 0.06f))
            .clickable(role = Role.Button, onClickLabel = title) { onClick() }
            .padding(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Vpn5gOnDark,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = body,
                color = Vpn5gOnDarkMuted,
                fontSize = 13.sp,
            )
        }
        if (badge != null) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Vpn5gOnDark.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badge,
                    color = Vpn5gOnDarkMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun vpn5gPromoFieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedTextColor = Vpn5gOnDark,
    unfocusedTextColor = Vpn5gOnDark,
    focusedLabelColor = Vpn5gAccentLight,
    unfocusedLabelColor = Vpn5gOnDarkMuted,
    focusedBorderColor = Vpn5gAccentLight,
    unfocusedBorderColor = Vpn5gOnDarkMuted,
    cursorColor = Vpn5gAccentLight,
)
