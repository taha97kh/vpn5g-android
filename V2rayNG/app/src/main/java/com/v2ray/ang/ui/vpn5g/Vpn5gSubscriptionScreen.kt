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
import com.v2ray.ang.handler.vpn5g.Vpn5gReferral
import com.v2ray.ang.handler.vpn5g.Vpn5gSubscriptionStatus
import com.v2ray.ang.handler.vpn5g.Vpn5gVolume

/**
 * Premium / subscription screen.
 *
 * v1.1.30 redesign (Aref's spec 2026-11-26): the screen opens with
 * the user's CURRENT status (from get_subscription_status via the
 * ViewModel's server-truth refresh — plan name in Persian, expiry
 * date, remaining days, remaining GB for GB plans; «اشتراک فعالی
 * نداری» when none), then TWO big group buttons — 📦 اشتراک حجمی
 * (GB packages, Model B) and 📅 اشتراک نامحدود (time-based) — each
 * opening its guide text + plan rows. Prices come ONLY from
 * app_config `plans` (never hardcoded). Purchase ends in the same
 * place from either path: the server applies the subscription via
 * validate_and_redeem_code and the app re-reads the status
 * immediately (v1.1.28 fix). Payment methods are config-driven
 * (bank gateway / admin payment link / USDT wallet — an
 * unpublished method is hidden, never a dead button), plus the
 * bot purchase guide. The share-app referral lives here too:
 * personal link, 1 GB gift for BOTH sides on all servers.
 *
 * Rules kept from earlier versions: the app never validates codes
 * against the published config list (the RPC is the truth); the
 * status is re-fetched at login, every open, and right after a
 * redemption; gift and subscription codes keep SEPARATE entry
 * boxes. v1.1.30: users with an ACTIVE subscription no longer see
 * the code-entry menus (Aref's order) — they return when the
 * subscription ends.
 */
@Composable
fun Vpn5gSubscriptionScreen(
    state: Vpn5gHomeUiState,
    onBack: () -> Unit,
    /** v1.1.22 (fix #4): refresh the subscription + locks on entry,
     * so the status section is never stale after a redeem or a
     * return to the app. */
    onEntered: () -> Unit = {},
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
    /** v1.1.30: share the referral link via the system share sheet. */
    onShareReferral: (String) -> Unit = {},
    /** v1.1.30: copy a text (referral link / USDT address) to the clipboard. */
    onCopyText: (String, String) -> Unit = { _, _ -> },
) {
    var entered by remember { mutableStateOf(false) }
    // v1.1.22: refresh the subscription + locks on entry (fix #4).
    LaunchedEffect(Unit) { onEntered() }
    // Section expansion is transient presentation state only (v1.1.5
    // §4). v1.1.15: ALL sections start CLOSED — the user opens them.
    var comparisonExpanded by remember { mutableStateOf(false) }
    var promoExpanded by remember { mutableStateOf(false) }
    var buyCodeExpanded by remember { mutableStateOf(false) }
    var countriesExpanded by remember { mutableStateOf(false) }
    var referralExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    // v1.1.15: a gift-code deep entry (ALL-tab button) opens the gift
    // section directly.
    LaunchedEffect(giftCodeRequest) {
        if (giftCodeRequest > 0) promoExpanded = true
    }

    // Plans as published by the bot (valid entries only), split
    // into the two groups (v1.1.30): GB packages (volume cap) and
    // unlimited time plans. The longest plan is the default
    // selection, like the old yearly default.
    val gbPlans = Vpn5gPlans.plansInGroup(state.plans, Vpn5gPlans.PlanGroup.GB)
    val timePlans = Vpn5gPlans.plansInGroup(state.plans, Vpn5gPlans.PlanGroup.TIME)
    val plans = gbPlans + timePlans
    var selectedPlanId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedPlan = plans.firstOrNull { it.id == selectedPlanId }
        ?: plans.maxByOrNull { Vpn5gPlans.planDays(it) ?: 0 }
        ?: plans.firstOrNull()
    // Which group is open (v1.1.30): null = both closed. Transient
    // presentation state only.
    var openGroup by rememberSaveable { mutableStateOf<String?>(null) }
    // Countries with their servers, from the live pool (v1.1.14): at
    // most 100 countries, largest first.
    val countryGroups = Vpn5gCountryGroups.groupByCountry(state.countries)
    var accountExpanded by remember { mutableStateOf(false) }
    val gateway = state.paymentGateway
    // v1.1.30: config-driven payment methods (gateway / payment
    // link / USDT) — only published ones appear.
    val paymentMethods = Vpn5gPlans.paymentMethods(gateway)
    // v1.1.11: the signed-in user's own subscription (from their
    // vpn5g_registrations row) — the per-user truth.
    val ownSubscription = state.userSubscription
    // v1.1.30 (Aref's order): plan names are Persian, never the raw
    // id ("sub_12" is never shown).
    val ownPlanName = Vpn5gPlans.displayNameForPlanId(ownSubscription?.plan, state.plans)
    // v1.1.30 (Aref's order): a user with an ACTIVE subscription no
    // longer sees the code-entry menus.
    val showCodeEntry = !state.isSubscriber
    // Real live counts, per server (never count a whole section as free).
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
        // v1.1.29 (Aref's order): visible back button at the top.
        Vpn5gBackRow(onBack)
        Vpn5gStaggeredItem(index = 0, visible = entered) {
            Text(
                text = stringResource(R.string.vpn5g_subscription_title),
                color = Vpn5gOnDark,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        // v1.1.30: CURRENT STATUS header — always visible at the
        // top (plan in Persian, expiry date, remaining days,
        // remaining GB for GB plans), straight from the server
        // truth the ViewModel re-reads on entry. «اشتراک فعالی
        // نداری» when there is none.
        Vpn5gStaggeredItem(index = 1, visible = entered) {
            Vpn5gSubscriptionStatusHeader(
                state = state,
                ownPlanName = ownPlanName,
            )
        }

        // v1.1.31 (Aref's order): a user with a PURCHASE
        // subscription (isSubscriber) sees no purchase UI anywhere
        // — the plan groups and the purchase area below are hidden
        // for them («هیچ جایی اسمی از خرید اشتراک نباشه») and every
        // feature is already active. Free and gift-tier users keep
        // the full purchase flow.
        if (!state.isSubscriber) {
        // v1.1.30: TWO big group buttons (not a flat list of 8
        // plans). Tapping one opens its guide + plan rows.
        Vpn5gStaggeredItem(index = 2, visible = entered) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.vpn5g_sub_choose_group),
                    color = Vpn5gOnDarkMuted,
                    fontSize = 14.sp,
                )
                Vpn5gPlanGroupButton(
                    title = stringResource(R.string.vpn5g_sub_group_gb_title),
                    expanded = openGroup == "gb",
                    onClick = { openGroup = if (openGroup == "gb") null else "gb" },
                )
                if (openGroup == "gb") {
                    Text(
                        text = stringResource(R.string.vpn5g_sub_group_gb_guide),
                        color = Vpn5gOnDarkMuted,
                        fontSize = 14.sp,
                    )
                    if (gbPlans.isEmpty()) {
                        Text(
                            text = stringResource(R.string.vpn5g_premium_plans_none),
                            color = Vpn5gOnDarkMuted,
                            fontSize = 14.sp,
                        )
                    } else {
                        gbPlans.forEach { plan ->
                            Vpn5gPlanCard(
                                plan = plan,
                                selected = plan.id == selectedPlan?.id,
                                trialEnabled = state.trialEnabled,
                                trialHours = state.trialHours,
                                onSelect = { selectedPlanId = plan.id },
                            )
                        }
                    }
                }
                Vpn5gPlanGroupButton(
                    title = stringResource(R.string.vpn5g_sub_group_time_title),
                    expanded = openGroup == "time",
                    onClick = { openGroup = if (openGroup == "time") null else "time" },
                )
                if (openGroup == "time") {
                    Text(
                        text = stringResource(R.string.vpn5g_sub_group_time_guide),
                        color = Vpn5gOnDarkMuted,
                        fontSize = 14.sp,
                    )
                    if (timePlans.isEmpty()) {
                        Text(
                            text = stringResource(R.string.vpn5g_premium_plans_none),
                            color = Vpn5gOnDarkMuted,
                            fontSize = 14.sp,
                        )
                    } else {
                        timePlans.forEach { plan ->
                            Vpn5gPlanCard(
                                plan = plan,
                                selected = plan.id == selectedPlan?.id,
                                trialEnabled = state.trialEnabled,
                                trialHours = state.trialHours,
                                onSelect = { selectedPlanId = plan.id },
                            )
                        }
                    }
                }
                if (state.trialEnabled) {
                    Text(
                        text = stringResource(R.string.vpn5g_premium_trial_note),
                        color = Vpn5gOnDarkMuted,
                        fontSize = 13.sp,
                    )
                }
            }
        }

        // v1.1.30: purchase area for the selected plan — the
        // config-driven payment methods (bank gateway / admin
        // payment link / USDT) plus the bot purchase guide. Both
        // paths end at validate_and_redeem_code + an immediate
        // status re-read (the ViewModel's redeem flow).
        if (selectedPlan != null) {
            Vpn5gStaggeredItem(index = 3, visible = entered) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.vpn5g_sub_purchase_title),
                        color = Vpn5gOnDark,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    paymentMethods.forEach { method ->
                        when (method.kind) {
                            Vpn5gPlans.PaymentMethodUi.Kind.GATEWAY -> Vpn5gPaymentRow(
                                title = method.nameFa
                                    ?: stringResource(R.string.vpn5g_premium_pay_gateway),
                                body = stringResource(R.string.vpn5g_premium_pay_gateway_body),
                                badge = null,
                                onClick = { method.url?.let(onOpenUrl) },
                            )
                            Vpn5gPlans.PaymentMethodUi.Kind.PAYMENT_LINK -> Vpn5gPaymentRow(
                                title = method.nameFa
                                    ?: stringResource(R.string.vpn5g_pay_link_default_name),
                                body = method.guideFa
                                    ?: stringResource(R.string.vpn5g_pay_link_guide_default),
                                badge = null,
                                onClick = { method.url?.let(onOpenUrl) },
                            )
                            Vpn5gPlans.PaymentMethodUi.Kind.USDT -> Vpn5gUsdtPaymentBlock(
                                method = method,
                                onCopyText = onCopyText,
                            )
                        }
                    }
                    // Bot purchase (always available): guide + the
                    // buy deep link carrying the selected plan id so
                    // the bot generates the UNIQUE payable amount.
                    Vpn5gPaymentRow(
                        title = stringResource(R.string.vpn5g_sub_buy_from_bot_button),
                        body = stringResource(R.string.vpn5g_sub_buy_from_bot_guide),
                        badge = null,
                        onClick = {
                            val planId = selectedPlan.id
                            if (planId != null) onOpenBuyBot(planId) else onOpenBot()
                        },
                    )
                    Text(
                        text = stringResource(R.string.vpn5g_premium_pay_unique_note),
                        color = Vpn5gOnDarkMuted,
                        fontSize = 13.sp,
                    )
                }
            }
        }
        }

        // v1.1.30: share-app referral — personal link, 1 GB gift
        // for BOTH sides on all servers, full explanation.
        Vpn5gStaggeredItem(index = 4, visible = entered) {
            Vpn5gExpandableSection(
                title = stringResource(R.string.vpn5g_referral_title),
                expanded = referralExpanded,
                onToggleExpand = { referralExpanded = !referralExpanded },
            ) {
                Text(
                    text = stringResource(R.string.vpn5g_referral_body),
                    color = Vpn5gOnDarkMuted,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(8.dp))
                val referralCode = Vpn5gReferral.ownCode(state.signedInEmail)
                if (referralCode == null) {
                    Text(
                        text = stringResource(R.string.vpn5g_referral_signin_needed),
                        color = Vpn5gOnDark,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onOpenAuth,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Vpn5gTeal,
                            contentColor = Color.White,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.vpn5g_auth_title), fontSize = 16.sp)
                    }
                } else {
                    val copiedToast = stringResource(R.string.vpn5g_referral_copied)
                    val shareLink = Vpn5gReferral.shareLink(referralCode)
                    Text(
                        text = stringResource(R.string.vpn5g_referral_your_link),
                        color = Vpn5gOnDark,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = shareLink,
                        color = Vpn5gTealLight,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    val shareText = stringResource(
                        R.string.vpn5g_referral_share_text,
                        shareLink,
                    )
                    Button(
                        onClick = { onShareReferral(shareText) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Vpn5gGold,
                            contentColor = Color.Black,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.vpn5g_referral_share_button),
                            fontSize = 16.sp,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { onCopyText(shareLink, copiedToast) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.vpn5g_referral_copy),
                            color = Vpn5gTealLight,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }

        // Code entry (v1.1.30: hidden while a subscription is
        // ACTIVE — Aref's order; the menus return when it ends).
        if (showCodeEntry) {
            // v1.1.15: the register-code button. v1.1.16: it expands
            // the SUBSCRIPTION-code section; redeeming never
            // navigates anywhere.
            Vpn5gStaggeredItem(index = 5, visible = entered) {
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

            // Gift code (v1.1.14): its OWN section with its OWN
            // entry box (v1.1.16, never a shared input).
            Vpn5gStaggeredItem(index = 6, visible = entered) {
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
                            containerColor = Vpn5gTeal,
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

            // Subscription code (v1.1.14/v1.1.16): a PURCHASED code
            // is redeemed HERE, in its own entry box.
            Vpn5gStaggeredItem(index = 7, visible = entered) {
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
                            containerColor = Vpn5gTeal,
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
                            containerColor = Vpn5gTeal,
                            contentColor = Color.White,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.vpn5g_open_bot), fontSize = 16.sp)
                    }
                }
            }
        }

        // Countries (v1.1.14; tree v1.1.15): up to 100 countries
        // with their servers, collapsible, closed by default.
        Vpn5gStaggeredItem(index = 8, visible = entered) {
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

        // Account (v1.1.11): sign-in only while signed out; a
        // signed-in user sees email + remaining + sign-out.
        Vpn5gStaggeredItem(index = 10, visible = entered) {
            if (state.signedInEmail.isNullOrBlank()) {
                TextButton(onClick = onOpenAuth, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.vpn5g_auth_title),
                        color = Vpn5gTealLight,
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
                            color = Vpn5gTealLight,
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
                    color = Vpn5gTealLight,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

/**
 * Current-status header (v1.1.30): the signed-in user's own
 * subscription (server truth) — plan in Persian, expiry date,
 * remaining days, remaining GB for GB plans; the gift tier in
 * hours; «اشتراک فعالی نداری» when there is none. Always visible
 * at the top of the subscription screen.
 */
@Composable
private fun Vpn5gSubscriptionStatusHeader(
    state: Vpn5gHomeUiState,
    ownPlanName: String?,
) {
    val ownSubscription = state.userSubscription
    val status = state.subscriptionStatus
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Vpn5gCard)
            .border(1.dp, Vpn5gGold.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        if (ownSubscription != null && state.isSubscriber) {
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
            ownSubscription.expiresAtEpochSeconds?.let { expiresAt ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.vpn5g_promo_redeemed_expiry,
                        Vpn5gSubscriptionStatus.formatExpiryDate(expiresAt),
                    ),
                    color = Vpn5gOnDark,
                    fontSize = 15.sp,
                )
            }
            // Remaining GB for GB plans (v1.1.30): cap minus the
            // on-device measured consumption of THIS grant.
            ownSubscription.volumeCapBytes?.let { capBytes ->
                val remaining = Vpn5gVolume.remainingBytes(
                    state.volumeConsumedBytes.coerceAtLeast(0L),
                    capBytes,
                ) ?: 0L
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.vpn5g_sub_remaining_gb,
                        Vpn5gVolume.formatGb(remaining),
                    ),
                    color = Vpn5gOnDark,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Vpn5gVolumeBlock(state)
        } else if (ownSubscription != null && state.isGift) {
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
            ownSubscription.expiresAtEpochSeconds?.let { expiresAt ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.vpn5g_promo_redeemed_expiry,
                        Vpn5gSubscriptionStatus.formatExpiryDate(expiresAt),
                    ),
                    color = Vpn5gOnDark,
                    fontSize = 15.sp,
                )
            }
            Vpn5gVolumeBlock(state)
        } else if (ownSubscription?.status == "active") {
            Text(
                text = stringResource(R.string.vpn5g_subscription_expired),
                color = Vpn5gOnDark,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
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
                text = stringResource(R.string.vpn5g_sub_status_none),
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
                LinearProgressIndicator(
                    progress = { (days / 30f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = Vpn5gTealLight,
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

/** One of the two big plan-group buttons (v1.1.30). */
@Composable
private fun Vpn5gPlanGroupButton(
    title: String,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (expanded) Vpn5gTeal else Vpn5gCard)
            .border(
                1.dp,
                if (expanded) Vpn5gTeal else Vpn5gOnDarkMuted.copy(alpha = 0.4f),
                RoundedCornerShape(16.dp),
            )
            .clickable(role = Role.Button, onClickLabel = title) { onClick() }
            .padding(vertical = 16.dp),
    ) {
        Text(
            text = title,
            color = if (expanded) Color.White else Vpn5gOnDark,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * USDT (Tether) payment block (v1.1.30): the published wallet
 * address (copyable), the network, and the guide text. The
 * payment itself is confirmed server-side via the code flow —
 * the app only displays.
 */
@Composable
private fun Vpn5gUsdtPaymentBlock(
    method: Vpn5gPlans.PaymentMethodUi,
    onCopyText: (String, String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Vpn5gOnDark.copy(alpha = 0.06f))
            .padding(16.dp),
    ) {
        Text(
            text = method.nameFa ?: stringResource(R.string.vpn5g_pay_usdt_default_name),
            color = Vpn5gOnDark,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.vpn5g_pay_usdt_address),
            color = Vpn5gOnDarkMuted,
            fontSize = 13.sp,
        )
        method.walletAddress?.let { address ->
            Text(
                text = address,
                color = Vpn5gTealLight,
                fontSize = 13.sp,
            )
            method.network?.let { network ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.vpn5g_pay_usdt_network, network),
                    color = Vpn5gOnDarkMuted,
                    fontSize = 13.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
            val copiedToast = stringResource(R.string.vpn5g_pay_usdt_copied)
            Button(
                onClick = { onCopyText(address, copiedToast) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Vpn5gTeal,
                    contentColor = Color.White,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.vpn5g_pay_usdt_copy), fontSize = 15.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = method.guideFa ?: stringResource(R.string.vpn5g_pay_usdt_guide_default),
            color = Vpn5gOnDarkMuted,
            fontSize = 13.sp,
        )
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
            color = if (header) Vpn5gTealLight else Vpn5gOnDark,
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
    // v1.1.30 (Aref's order): the plan name is Persian, never the
    // raw id ("sub_12" is never shown to the user).
    val name = Vpn5gPlans.displayNameFa(plan) ?: plan.id.orEmpty()
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
                color = if (selected) Vpn5gTeal else Vpn5gOnDarkMuted.copy(alpha = 0.4f),
                shape = RoundedCornerShape(16.dp),
            )
            .background(Vpn5gOnDark.copy(alpha = 0.06f))
            .clickable(role = Role.Button, onClickLabel = name) { onSelect() }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name,
                color = Vpn5gTealLight,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (plan.period == "yearly") {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Vpn5gTeal)
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
    focusedLabelColor = Vpn5gTealLight,
    unfocusedLabelColor = Vpn5gOnDarkMuted,
    focusedBorderColor = Vpn5gTealLight,
    unfocusedBorderColor = Vpn5gOnDarkMuted,
    cursorColor = Vpn5gTealLight,
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
            color = Vpn5gTealLight,
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
    // v1.1.30: Persian plan name, never the raw id.
    val ownPlanName = Vpn5gPlans.displayNameForPlanId(
        state.userSubscription?.plan,
        state.plans,
    )
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
                    containerColor = Vpn5gTeal,
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
        Vpn5gPromoStatus.NO_REGISTRATION -> {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.vpn5g_promo_no_registration),
                color = Color(0xFFF87171),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Vpn5gPromoStatus.SERVER_UNAVAILABLE -> {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.vpn5g_promo_server_unavailable),
                color = Color(0xFFF87171),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Vpn5gPromoStatus.NOT_APPLIED -> {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.vpn5g_promo_not_applied),
                color = Color(0xFFF87171),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Vpn5gPromoStatus.REDEEMED -> {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.vpn5g_promo_redeemed),
                color = Color(0xFF4ADE80),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            val activatedExpiry = state.userSubscription?.expiresAtEpochSeconds
            if (activatedExpiry != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.vpn5g_promo_redeemed_expiry,
                        Vpn5gSubscriptionStatus.formatExpiryDate(activatedExpiry),
                    ),
                    color = Vpn5gOnDark,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
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
                    containerColor = Vpn5gTeal,
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
