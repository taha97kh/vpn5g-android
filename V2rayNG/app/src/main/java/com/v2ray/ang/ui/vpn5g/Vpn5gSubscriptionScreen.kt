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
import com.v2ray.ang.handler.vpn5g.Vpn5gCountryGroups
import com.v2ray.ang.handler.vpn5g.Vpn5gPlans
import com.v2ray.ang.handler.vpn5g.Vpn5gSubscriptionStatus
import com.v2ray.ang.handler.vpn5g.Vpn5gVolume

/**
 * Premium / subscription screen.
 *
 * Shows ONLY what the remote app_config.json provides — numbers are never
 * invented. The days bar is measured against a 30-day cycle and labelled as
 * such; volume is shown as text (no total is published by the server).
 * v1.1.11: when the signed-in user has their own subscription row
 * (vpn5g_registrations), the status section shows that per-user plan and
 * its remaining days instead of the bot-published global status.
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
 * app_config.json and records the redemption locally. v1.1.12: a GIFT
 * code activates the subscription immediately in the app (plan +
 * gift_days, no admin approval, no second confirm screen); the bot
 * deep link stays only as an optional step so the bot can decrement
 * remaining_uses server-side. Discount codes still go through the bot.
 * v1.1.15: codes are validated SERVER-side first
 * (`validate_and_redeem_code`); the config list is only the fallback
 * when the RPC is unreachable.
 *
 * v1.1.16 layout (master order): title → benefits description →
 * REGISTER-CODE button (top) → gift-code section → locked countries
 * (collapsible tree, closed by default) → subscription status
 * (remaining time inside it) → plans → payment → buy-code →
 * comparison → account → back-home. v1.1.16: gift codes and purchased
 * subscription codes have SEPARATE sections with their OWN entry
 * boxes (never a shared input); the top register button opens the
 * SUBSCRIPTION-code section, the ALL-tab gift button opens the gift
 * section, and the status section opens itself whenever a plan is
 * active or a code was just redeemed, so the active plan is visible.
 * Buying stays inside Premium; the buy deep link carries the selected
 * plan id (`start=buy_<plan_id>`) so the bot can generate the UNIQUE
 * payable amount for that order — the app never shows a round plan
 * price as the amount to pay.
 */
@Composable
fun Vpn5gSubscriptionScreen(
    state: Vpn5gHomeUiState,
    onBack: () -> Unit,
    onOpenBot: () -> Unit,
    onOpenAuth: () -> Unit,
    onSignOut: () -> Unit,
    onOpenUrl: (String) -> Unit,
    /** Gift-code section input (v1.1.16: separate from subscription codes). */
    onGiftCodeInputChange: (String) -> Unit,
    onRedeemGiftCode: () -> Unit,
    /** Subscription-code section input (v1.1.16: separate from gift codes). */
    onSubCodeInputChange: (String) -> Unit,
    onRedeemSubCode: () -> Unit,
    onOpenPromoBot: () -> Unit,
    /** One-tap connect to a country's fastest server (subscribers / gift tier). */
    onCountryServerTap: (String) -> Unit,
    /** Locked country tapped: shows the subscription dialog. */
    onLockedCountryTap: (String) -> Unit,
    /** Buy via bot with the selected plan id (v1.1.15, G): the deep
     * link is t.me/VPN5G_IRBot?start=buy_<plan_id> so the bot can
     * generate the unique payable amount for that order. */
    onOpenBuyBot: (String) -> Unit,
    /** Gift-code deep entry counter (v1.1.15, C): when it changes, the
     * gift section opens directly. */
    giftCodeRequest: Int = 0,
    /** Toggles a locked-country group (v1.1.15): state lives in the
     * ViewModel and is persisted. */
    onToggleCountryExpanded: (String) -> Unit,
) {
    var entered by remember { mutableStateOf(false) }
    // Section expansion is transient presentation state only (v1.1.5
    // §4). v1.1.15: ALL sections start CLOSED — the user opens them.
    // (Before: all open except the comparison table.)
    var statusExpanded by remember { mutableStateOf(false) }
    var comparisonExpanded by remember { mutableStateOf(false) }
    var plansExpanded by remember { mutableStateOf(false) }
    var paymentExpanded by remember { mutableStateOf(false) }
    var promoExpanded by remember { mutableStateOf(false) }
    var buyCodeExpanded by remember { mutableStateOf(false) }
    var countriesExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    // v1.1.15: a gift-code deep entry (ALL-tab button) opens the gift
    // section directly — no scroll hack, the section sits right below
    // the register button at the top.
    LaunchedEffect(giftCodeRequest) {
        if (giftCodeRequest > 0) promoExpanded = true
    }
    // v1.1.16: the active plan must be VISIBLE after redeeming — the
    // status section opens itself whenever a plan is active or a code
    // was just redeemed. The user can still close it by hand.
    LaunchedEffect(
        state.isSubscriber, state.isGift,
        state.giftCodeStatus, state.subCodeStatus,
    ) {
        if (state.isSubscriber || state.isGift ||
            state.giftCodeStatus == Vpn5gPromoStatus.REDEEMED ||
            state.subCodeStatus == Vpn5gPromoStatus.REDEEMED
        ) {
            statusExpanded = true
        }
    }

    // Plans as published by the bot (valid entries only: id + price +
    // a known duration — sub_1/sub_3/sub_6/sub_12 or an explicit days
    // field, or the legacy monthly/yearly period). The longest plan is
    // the default selection, like the old yearly default.
    val plans = Vpn5gPlans.displayablePlans(state.plans)
    var selectedPlanId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedPlan = plans.firstOrNull { it.id == selectedPlanId }
        ?: plans.maxByOrNull { Vpn5gPlans.planDays(it) ?: 0 }
        ?: plans.firstOrNull()
    // Countries with their servers, from the live pool (v1.1.14): at
    // most 100 countries, largest first. The pool carries country data
    // only — no cities exist anywhere, so no city names are shown.
    val countryGroups = Vpn5gCountryGroups.groupByCountry(state.countries)
    var showPayment by rememberSaveable { mutableStateOf(false) }
    var accountExpanded by remember { mutableStateOf(false) }
    val gateway = state.paymentGateway
    val gatewayReady = gateway?.enabled == true && !gateway.url.isNullOrBlank()
    // v1.1.11: the signed-in user's own subscription (from their
    // vpn5g_registrations row) — the per-user truth, used by the
    // status section above and by the signed-in account block below.
    val ownSubscription = state.userSubscription
    val ownPlanName = ownSubscription?.plan?.let { planId ->
        state.plans?.firstOrNull { it.id == planId }?.name_fa ?: planId
    }
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

        // v1.1.15: short benefits description, right under the title.
        Vpn5gStaggeredItem(index = 1, visible = entered) {
            Text(
                text = stringResource(R.string.vpn5g_subscription_benefits_desc),
                color = Vpn5gOnDarkMuted,
                fontSize = 14.sp,
            )
        }

        // v1.1.15: the register-code button at the TOP of Premium.
        // v1.1.16: it expands the SUBSCRIPTION-code section (a
        // purchased code is registered there, not in the gift
        // section); redeeming never navigates anywhere.
        Vpn5gStaggeredItem(index = 2, visible = entered) {
            Button(
                onClick = { buyCodeExpanded = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Vpn5gGold,
                    contentColor = Color.Black,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.vpn5g_register_code_top), fontSize = 16.sp)
            }
        }

        // Gift code (v1.1.14): its OWN section, separate from buying a
        // subscription code — with its OWN entry box (v1.1.16, never a
        // shared input). The code input + submit are RIGHT HERE
        // inline — redeeming never navigates to another page.
        Vpn5gStaggeredItem(index = 3, visible = entered) {
            Vpn5gExpandableSection(
                title = stringResource(R.string.vpn5g_gift_title),
                expanded = promoExpanded,
                onToggleExpand = { promoExpanded = !promoExpanded },
            ) {
                Text(
                    text = stringResource(R.string.vpn5g_gift_body),
                    color = Vpn5gOnDarkMuted,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.giftCodeInput,
                    onValueChange = onGiftCodeInputChange,
                    label = { Text(stringResource(R.string.vpn5g_promo_code)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    colors = vpn5gPromoFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onRedeemGiftCode,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Vpn5gAccent,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.vpn5g_promo_redeem), fontSize = 16.sp)
                }
                Vpn5gCodeStatusBlock(
                    status = state.giftCodeStatus,
                    state = state,
                    onOpenPromoBot = onOpenPromoBot,
                    onOpenAuth = onOpenAuth,
                )
            }
        }

        // Countries (v1.1.14; tree v1.1.15): up to 100 countries with
        // their servers, as a collapsible tree, closed by default.
        // from the live pool. Locked for non-subscribers — only
        // subscription buyers can open/use them; subscribers tap a
        // country to connect to its fastest server (auto-connect
        // includes them — the full pool). The gift tier opens the
        // countries its top-50 servers belong to. A locked tap prompts
        // subscription. No city data exists in the pool, so each server
        // row is one location inside its country.
        Vpn5gStaggeredItem(index = 4, visible = entered) {
            Vpn5gExpandableSection(
                title = stringResource(R.string.vpn5g_premium_countries_title),
                expanded = countriesExpanded,
                onToggleExpand = { countriesExpanded = !countriesExpanded },
            ) {
                if (countryGroups.isEmpty()) {
                    Text(
                        text = stringResource(R.string.vpn5g_premium_countries_none),
                        color = Vpn5gOnDarkMuted,
                        fontSize = 14.sp,
                    )
                } else {
                    Text(
                        text = stringResource(
                            R.string.vpn5g_premium_countries_count,
                            countryGroups.size,
                        ),
                        color = Vpn5gOnDarkMuted,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    countryGroups.forEach { group ->
                        val groupKey = group.code.ifBlank { group.nameFa }
                        val unlocked = state.isSubscriber || group.hasFreeServer
                        // v1.1.15: nested collapsible group — closed by
                        // default, lock badge when locked, open/closed
                        // state persisted in the ViewModel.
                        Vpn5gExpandableSection(
                            title = group.nameFa,
                            subtitle = stringResource(
                                R.string.vpn5g_servers_count,
                                group.servers.size,
                            ),
                            badge = if (unlocked) null else "\uD83D\uDD12",
                            leading = {
                                Text(
                                    text = group.flag,
                                    fontSize = 20.sp,
                                )
                            },
                            expanded = groupKey in state.expandedCountryKeys,
                            onToggleExpand = { onToggleCountryExpanded(groupKey) },
                        ) {
                            group.servers.forEach { server ->
                                // Per-server gate (v1.1.14 pool): isFree
                                // already reflects the tier — top 20 for
                                // free, top 50 for gift, all for
                                // subscribers — so one rule covers all.
                                val serverUnlocked = state.isSubscriber || server.isFree
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Vpn5gOnDark.copy(alpha = 0.06f))
                                        .clickable(
                                            role = Role.Button,
                                            onClickLabel = group.nameFa +
                                                " " + server.protocol.uppercase(),
                                        ) {
                                            if (serverUnlocked) {
                                                onCountryServerTap(server.guid)
                                            } else {
                                                onLockedCountryTap(group.nameFa)
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        // The config remark is never
                                        // displayed anywhere: a server row
                                        // shows its protocol + ping only.
                                        Text(
                                            text = server.protocol.uppercase(),
                                            color = Vpn5gOnDark,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            text = if (server.pingMillis > 0L) {
                                                "${server.pingMillis} ms"
                                            } else {
                                                "\u2014"
                                            },
                                            color = Vpn5gOnDarkMuted,
                                            fontSize = 13.sp,
                                        )
                                    }
                                    if (!serverUnlocked) {
                                        Vpn5gLockIcon(color = Vpn5gGold, size = 18.dp)
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }

        val status = state.subscriptionStatus
        // v1.1.11: the signed-in user's own subscription (from their
        // vpn5g_registrations row) wins over the bot-published global
        // status: it is the only per-user truth. An "active" row that
        // already expired shows the expired line, not plan days.
        Vpn5gStaggeredItem(index = 5, visible = entered) {
            Vpn5gExpandableSection(
                title = stringResource(R.string.vpn5g_subscription_title),
                expanded = statusExpanded,
                onToggleExpand = { statusExpanded = !statusExpanded },
            ) {
                if (ownSubscription != null && state.isSubscriber) {
                    // v1.1.12: premium look — gold pill + the remaining
                    // days prominent (bold, larger) for subscribers.
                    Vpn5gPremiumPill()
                    Spacer(Modifier.height(8.dp))
                    ownPlanName?.let { plan ->
                        Text(
                            text = stringResource(R.string.vpn5g_subscription_plan, plan),
                            color = Vpn5gOnDark,
                            fontSize = 15.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        text = stringResource(
                            R.string.vpn5g_subscription_days,
                            Vpn5gSubscriptionStatus.remainingDays(
                                ownSubscription,
                                System.currentTimeMillis() / 1000,
                            ),
                        ),
                        color = Vpn5gOnDark,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    // v1.1.18 (Model B): used / remaining volume for
                    // this grant, measured on-device.
                    Vpn5gVolumeBlock(state)
                } else if (ownSubscription != null && state.isGift) {
                    // v1.1.14: the GIFT tier — middle tier, top-50
                    // servers, no 30-minute cutoff while it lasts.
                    // Remaining time is shown in hours, never days.
                    Text(
                        text = stringResource(R.string.vpn5g_gift_title),
                        color = Vpn5gGold,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(
                            R.string.vpn5g_gift_remaining_hours,
                            state.giftRemainingHours,
                        ),
                        color = Vpn5gOnDark,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Vpn5gVolumeBlock(state)
                } else if (ownSubscription?.status == "active") {
                    Text(
                        text = stringResource(R.string.vpn5g_subscription_expired),
                        color = Vpn5gOnDark,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    // v1.1.18 (Model B): when the volume cap ended the
                    // grant, say so — "expired" alone would hide the
                    // real reason.
                    if (Vpn5gSubscriptionStatus.isVolumeExhausted(
                            ownSubscription, state.volumeState()
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.vpn5g_volume_finished),
                            color = Vpn5gOnDark,
                            fontSize = 14.sp,
                        )
                        Vpn5gVolumeBlock(state)
                    } else {
                        Text(
                            text = stringResource(R.string.vpn5g_subscription_none_body),
                            color = Vpn5gOnDarkMuted,
                            fontSize = 14.sp,
                        )
                    }
                } else if (status == null) {
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

        // Plans from remote config (never hardcoded). No plans published
        // means no cards — the user buys through the bot instead.
        Vpn5gStaggeredItem(index = 6, visible = entered) {
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
            Vpn5gStaggeredItem(index = 7, visible = entered) {
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
                            val planId = selectedPlan?.id
                            if (gatewayReady && !url.isNullOrBlank()) {
                                onOpenUrl(url)
                            } else if (planId != null) {
                                // v1.1.15 (unique payments, G): the buy
                                // deep link carries the selected plan so
                                // the bot can generate the UNIQUE payable
                                // amount for that order.
                                onOpenBuyBot(planId)
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
                        onClick = {
                            val planId = selectedPlan?.id
                            if (planId != null) onOpenBuyBot(planId) else onOpenBot()
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    // v1.1.15 (unique payments, G): the payable amount
                    // comes FROM THE BOT, unique per order — the app
                    // never shows a round plan price as the amount to
                    // pay in a payment context.
                    Text(
                        text = stringResource(R.string.vpn5g_premium_pay_unique_note),
                        color = Vpn5gOnDarkMuted,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        // Buy a subscription code (v1.1.14): a SEPARATE section from the
        // gift-code section above — buying happens through the bot; the
        // app never sells a code itself. v1.1.16: a PURCHASED code is
        // redeemed HERE, in its own entry box — never in the gift
        // section's box.
        Vpn5gStaggeredItem(index = 8, visible = entered) {
            Vpn5gExpandableSection(
                title = stringResource(R.string.vpn5g_buy_code_title),
                expanded = buyCodeExpanded,
                onToggleExpand = { buyCodeExpanded = !buyCodeExpanded },
            ) {
                Text(
                    text = stringResource(R.string.vpn5g_sub_code_body),
                    color = Vpn5gOnDarkMuted,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.subCodeInput,
                    onValueChange = onSubCodeInputChange,
                    label = { Text(stringResource(R.string.vpn5g_promo_code)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    colors = vpn5gPromoFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onRedeemSubCode,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Vpn5gAccent,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(text = stringResource(R.string.vpn5g_promo_redeem), fontSize = 16.sp)
                }
                Vpn5gCodeStatusBlock(
                    status = state.subCodeStatus,
                    state = state,
                    onOpenPromoBot = onOpenPromoBot,
                    onOpenAuth = onOpenAuth,
                )
                Spacer(Modifier.height(8.dp))
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
        }

        // Free vs Premium comparison (counts come from the live list).
        Vpn5gStaggeredItem(index = 9, visible = entered) {
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

        // v1.1.11 (refinement, Aref 2026-11-23 21:20): the email
        // sign-in option appears ONLY while signed out. A signed-in
        // user sees their account here instead — email, remaining
        // credit, and sign-out — and never a second sign-in button.
        // Sign-out is direct, same as the Me screen (no confirmation).
        Vpn5gStaggeredItem(index = 10, visible = entered) {
            if (state.signedInEmail.isNullOrBlank()) {
                TextButton(onClick = onOpenAuth, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.vpn5g_auth_title),
                        color = Vpn5gAccentLight,
                        fontSize = 14.sp,
                    )
                }
            } else {
                Vpn5gExpandableSection(
                    title = stringResource(R.string.vpn5g_profile_account),
                    expanded = accountExpanded,
                    onToggleExpand = { accountExpanded = !accountExpanded },
                ) {
                    Text(
                        text = state.signedInEmail,
                        color = Vpn5gOnDark,
                        fontSize = 15.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (ownSubscription != null && state.isSubscriber) {
                        ownPlanName?.let { plan ->
                            Text(
                                text = stringResource(R.string.vpn5g_subscription_plan, plan),
                                color = Vpn5gOnDark,
                                fontSize = 15.sp,
                            )
                            Spacer(Modifier.height(4.dp))
                        }
                        Text(
                            text = stringResource(
                                R.string.vpn5g_subscription_days,
                                Vpn5gSubscriptionStatus.remainingDays(
                                    ownSubscription,
                                    System.currentTimeMillis() / 1000,
                                ),
                            ),
                            color = Vpn5gOnDark,
                            fontSize = 15.sp,
                        )
                    } else if (ownSubscription?.status == "active") {
                        Text(
                            text = stringResource(R.string.vpn5g_subscription_expired),
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
                    }
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
        Vpn5gStaggeredItem(index = 11, visible = entered) {
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
        ?: when (plan.period?.trim()?.lowercase()) {
            "yearly" -> stringResource(R.string.vpn5g_premium_plan_yearly)
            "monthly" -> stringResource(R.string.vpn5g_premium_plan_monthly)
            // Bot plan ids (sub_1 …) with no published name: show the id.
            else -> plan.id.orEmpty()
        }
    // Legacy monthly/yearly plans carry a per-period suffix; bot plan
    // ids (sub_1 …) show the plain price — their duration is the days
    // line below (v1.1.14).
    val priceText = when (plan.period?.trim()?.lowercase()) {
        "monthly", "yearly" -> stringResource(
            R.string.vpn5g_premium_price,
            "%,d".format(plan.price_toman ?: 0L),
            stringResource(
                if (plan.period == "yearly") R.string.vpn5g_premium_per_year
                else R.string.vpn5g_premium_per_month
            ),
        )
        else -> stringResource(
            R.string.vpn5g_premium_price_plain,
            "%,d".format(plan.price_toman ?: 0L),
        )
    }
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
        // Duration from the remote plan (v1.1.14): the bot's days field,
        // the sub_* id, or the legacy period — never hardcoded.
        Vpn5gPlans.planDays(plan)?.let { days ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.vpn5g_premium_plan_days, days),
                color = Vpn5gOnDark,
                fontSize = 14.sp,
            )
        }
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

/**
 * Measured consumption for the home state's current grant (v1.1.18):
 * the grant key is derived from the effective subscription (the
 * ViewModel's ensureGrant keeps the store's key on the same grant),
 * so bytes measured under another grant never attribute here.
 */
internal fun Vpn5gHomeUiState.volumeState(): Vpn5gVolume.ConsumedState {
    return Vpn5gVolume.ConsumedState(
        Vpn5gVolume.grantKeyFor(userSubscription),
        volumeConsumedBytes,
    )
}

/**
 * Volume meter for the current grant (v1.1.18, Model B), shared by
 * the Premium status section, the REDEEMED block and the Me screen:
 * used vs cap with a progress bar, "نامحدود" when the plan has no
 * cap (never a made-up number), and the consumed amount even then —
 * the user only ever sees the on-device measured consumption. Shows
 * nothing when there is no subscription at all.
 */
@Composable
internal fun Vpn5gVolumeBlock(state: Vpn5gHomeUiState) {
    val subscription = state.userSubscription ?: return
    val capBytes = subscription.volumeCapBytes
    val consumed = state.volumeConsumedBytes.coerceAtLeast(0L)
    Spacer(Modifier.height(8.dp))
    if (capBytes == null) {
        Text(
            text = stringResource(R.string.vpn5g_volume_unlimited),
            color = Vpn5gOnDark,
            fontSize = 15.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(
                R.string.vpn5g_volume_used,
                Vpn5gVolume.formatGb(consumed),
            ),
            color = Vpn5gOnDarkMuted,
            fontSize = 14.sp,
        )
    } else {
        Text(
            text = stringResource(
                R.string.vpn5g_volume_used_of,
                Vpn5gVolume.formatGb(consumed),
                Vpn5gVolume.formatGb(capBytes),
            ),
            color = Vpn5gOnDark,
            fontSize = 15.sp,
        )
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = {
                Vpn5gVolume.usageFraction(consumed, capBytes) ?: 0f
            },
            modifier = Modifier.fillMaxWidth(),
            color = Vpn5gAccentLight,
            trackColor = Vpn5gOnDarkMuted.copy(alpha = 0.3f),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(
                R.string.vpn5g_volume_remaining,
                Vpn5gVolume.formatGb(
                    Vpn5gVolume.remainingBytes(consumed, capBytes) ?: 0L
                ),
            ),
            color = Vpn5gOnDarkMuted,
            fontSize = 14.sp,
        )
    }
}

/**
 * Redeem-result block shared by the gift and subscription code
 * sections (v1.1.16): one status display, two entry boxes. On
 * REDEEMED it shows the activated plan + remaining time right where
 * the code was entered, so the active plan is visible immediately.
 */
@Composable
private fun Vpn5gCodeStatusBlock(
    status: Vpn5gPromoStatus,
    state: Vpn5gHomeUiState,
    onOpenPromoBot: () -> Unit,
    onOpenAuth: () -> Unit,
) {
    val ownPlanName = state.userSubscription?.plan?.let { planId ->
        state.plans?.firstOrNull { it.id == planId }?.name_fa ?: planId
    }
    when (status) {
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
                Text(text = stringResource(R.string.vpn5g_promo_open_bot), fontSize = 16.sp)
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
        Vpn5gPromoStatus.INVALID_EMAIL -> {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.vpn5g_promo_invalid_email),
                color = Color(0xFFF87171),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Vpn5gPromoStatus.REDEEMED -> {
            // v1.1.12: the gift code activated the subscription
            // immediately — plan + remaining days prominent.
            // v1.1.18: no bot confirm here — the grant is active
            // in-app; the bot button is gone.
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.vpn5g_promo_redeemed),
                color = Color(0xFF4ADE80),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            val activated = state.userSubscription
            if (activated != null && state.isSubscriber) {
                Spacer(Modifier.height(8.dp))
                ownPlanName?.let { plan ->
                    Text(
                        text = stringResource(R.string.vpn5g_subscription_plan, plan),
                        color = Vpn5gOnDark,
                        fontSize = 15.sp,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.vpn5g_subscription_days,
                        Vpn5gSubscriptionStatus.remainingDays(
                            activated,
                            System.currentTimeMillis() / 1000,
                        ),
                    ),
                    color = Vpn5gOnDark,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
            } else if (activated != null && state.isGift) {
                // v1.1.14: a gift-tier redemption shows its
                // remaining HOURS, not days.
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        R.string.vpn5g_gift_remaining_hours,
                        state.giftRemainingHours,
                    ),
                    color = Vpn5gOnDark,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            // v1.1.18: a redeemed grant needs no bot confirm — the
            // bot note and button are gone. The volume meter below
            // shows the grant's cap (or "نامحدود").
            Vpn5gVolumeBlock(state)
        }
        Vpn5gPromoStatus.EXPIRED -> {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.vpn5g_promo_expired),
                color = Color(0xFFF87171),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Vpn5gPromoStatus.NOT_YOURS -> {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.vpn5g_promo_not_yours),
                color = Color(0xFFF87171),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Vpn5gPromoStatus.NOT_SIGNED_IN -> {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.vpn5g_promo_not_signed_in),
                color = Color(0xFFF87171),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
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
        }
        Vpn5gPromoStatus.WRONG_SECTION -> {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.vpn5g_promo_wrong_section),
                color = Color(0xFFF87171),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Vpn5gPromoStatus.IDLE -> Unit
    }
}
