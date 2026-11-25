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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.v2ray.ang.R
import com.v2ray.ang.handler.vpn5g.Vpn5gConnectMode
import com.v2ray.ang.handler.vpn5g.Vpn5gCountryUi
import com.v2ray.ang.handler.vpn5g.Vpn5gServerUi
import com.v2ray.ang.ui.compose.colorPing
import com.v2ray.ang.ui.compose.colorPingRed

/**
 * Server-select screen (v1.1.21, Lantern server-select page): the whole
 * server list moved out of the home screen, in Lantern dress — a smart
 * location card on top, the gold Pro upgrade card, the Pro locations
 * list, and the same free / all / special tabs. The list logic is
 * unchanged from the home screen (same split, same tabs, same picker,
 * same auto row); only the container moved and the surfaces changed.
 *
 * No city is ever shown: the pool carries country data only.
 */
@Composable
fun Vpn5gServerSelectScreen(
    state: Vpn5gHomeUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onServerTap: (String) -> Unit,
    onAutoConnect: () -> Unit,
    onSelectManualServer: (String) -> Boolean,
    onSelectAutoMode: () -> Unit,
    onLockedCountryTap: (String) -> Unit,
    onOpenPremium: () -> Unit,
    onOpenGiftCode: () -> Unit,
) {
    var entered by remember { mutableStateOf(false) }
    var serverTab by rememberSaveable { mutableStateOf(Vpn5gServerTab.FREE) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    // Free-servers picker (v1.1.5 §3): the row expands to a country
    // list, then to that country's servers. Transient presentation
    // state only; the pick itself lives in the ViewModel.
    var freePickerExpanded by rememberSaveable { mutableStateOf(false) }
    var pickerCountry by rememberSaveable { mutableStateOf<String?>(null) }
    // Auto-row info panel (v1.1.5 §4). Transient presentation state only.
    var autoInfoExpanded by rememberSaveable { mutableStateOf(false) }
    // Premium tab (v1.1.2): country groups start collapsed; tapping a
    // country reveals its servers. Transient presentation state only.
    var specialExpanded by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(Unit) { entered = true }

    val displayCountries = state.countries
    val searchedCountries = if (searchQuery.isBlank()) {
        displayCountries
    } else {
        displayCountries.mapNotNull { section ->
            val labelMatch = section.label.contains(searchQuery, ignoreCase = true)
            val servers = if (labelMatch) {
                section.servers
            } else {
                section.servers.filter {
                    it.name.contains(searchQuery, ignoreCase = true) ||
                        it.countryNameFa.contains(searchQuery, ignoreCase = true)
                }
            }
            if (servers.isEmpty()) null else section.copy(servers = servers)
        }
    }
    // Split each country into a free copy (top-20 by ping only) and a
    // locked copy (everything else), so a section never mixes tiers and
    // free counts are per-server, never per-section.
    val splitSections = searchedCountries.flatMap { section ->
        val free = section.servers.filter { it.isFree }
        val locked = section.servers.filter { !it.isFree }
        listOfNotNull(
            if (free.isNotEmpty()) section.copy(servers = free, isFree = true) else null,
            if (locked.isNotEmpty()) section.copy(servers = locked, isFree = false) else null,
        )
    }
    val freeSections = splitSections.filter { it.isFree }
    val lockedSections = splitSections.filter { !it.isFree }
    val freeCount = freeSections.sumOf { it.servers.size }
    // Free servers always have a real through-the-proxy ping (v1.1.7
    // top-20 selection), so the fastest free server is the best one;
    // never fall back to a TCP-only, no-ping or locked server.
    // v1.1.14: servers that verified on the CURRENT operator sort
    // first — the operator's own history beats raw ping; with no
    // history for the operator this is exactly the ping order.
    val bestServer = run {
        val pool = freeSections.flatMap { it.servers }
            .filter { it.hasRealPing && it.pingMillis > 0 }
        val preferred = pool.filter { it.operatorVerified }.ifEmpty { pool }
        preferred.minByOrNull { it.pingMillis }
    }
    // ALL-tab filter pills (v1.1.14): null = show both, subscription
    // first; SUB = subscription only; FREE = free only.
    var allFilter by rememberSaveable { mutableStateOf<String?>(null) }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                Vpn5gStaggeredItem(index = 0, visible = entered) {
                    Text(
                        text = stringResource(R.string.vpn5g_server_select_title),
                        color = Vpn5gOnDark,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start,
                    )
                }
            }

            if (state.offline) {
                item {
                    Text(
                        text = stringResource(R.string.vpn5g_offline_cached),
                        color = Vpn5gOnDarkMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // First sync in progress: show a loading line, never the empty text.
            if (state.isLoading || (state.isRefreshing && state.countries.isEmpty())) {
                item {
                    Text(
                        text = stringResource(R.string.vpn5g_loading_servers),
                        color = Vpn5gOnDarkMuted,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (displayCountries.isEmpty() && !state.isLoading && !state.isRefreshing) {
                item {
                    Text(
                        text = stringResource(R.string.vpn5g_empty_list),
                        color = Vpn5gOnDarkMuted,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // "Free Servers" picker (v1.1.5 §3): a real dropdown — tap
            // the row for the country list (flag + server count + best
            // ping), tap a country for its servers with health + real
            // ping, tap a server to pick it by hand. The pick is
            // persisted and validated exactly like the auto pick; a bad
            // pick is refused with the precise reason, never silently
            // replaced. The list below (tabs + sections) is untouched.
            if (freeSections.isNotEmpty() && serverTab != Vpn5gServerTab.SPECIAL) {
                item(key = "free_servers_row") {
                    val manualServer = state.manualGuid?.let { guid ->
                        freeSections.flatMap { it.servers }
                            .firstOrNull { it.guid == guid }
                    }
                    val manualSubtitle =
                        if (state.connectMode == Vpn5gConnectMode.MANUAL && manualServer != null) {
                            // v1.1.7: the ping suffix is shown only for a
                            // real through-the-proxy delay, never for a
                            // TCP-only one.
                            stringResource(R.string.vpn5g_manual_label) + ": " +
                                manualServer.flag + " " + displayCountryName(
                                manualServer,
                                stringResource(R.string.vpn5g_country_unknown),
                            ) + if (manualServer.hasRealPing) {
                                " — " + stringResource(
                                    R.string.vpn5g_ping_ms, manualServer.pingMillis
                                )
                            } else {
                                ""
                            }
                        } else {
                            null
                        }
                    Vpn5gExpandableSection(
                        title = stringResource(R.string.vpn5g_free_servers),
                        subtitle = manualSubtitle,
                        badge = stringResource(R.string.vpn5g_servers_count, freeCount),
                        leading = { Vpn5gBoltIcon(color = Vpn5gTeal, size = 26.dp) },
                        expanded = freePickerExpanded,
                        onToggleExpand = {
                            freePickerExpanded = !freePickerExpanded
                            if (!freePickerExpanded) pickerCountry = null
                        },
                    ) {
                        if (pickerCountry == null) {
                            freeSections.forEach { section ->
                                val bestPing = section.servers
                                    .filter { it.hasRealPing && it.pingMillis > 0 }
                                    .minOfOrNull { it.pingMillis }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable(
                                            role = Role.Button,
                                            onClickLabel = section.label,
                                        ) { pickerCountry = section.label }
                                        .padding(vertical = 10.dp),
                                ) {
                                    Text(
                                        text = section.label,
                                        color = Vpn5gOnDark,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.vpn5g_servers_count,
                                            section.servers.size,
                                        ),
                                        color = Vpn5gOnDarkMuted,
                                        fontSize = 13.sp,
                                    )
                                    if (bestPing != null) {
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(
                                                R.string.vpn5g_ping_ms, bestPing
                                            ),
                                            color = colorPing,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Vpn5gChevronIcon(color = Vpn5gOnDarkMuted)
                                }
                            }
                        } else {
                            val pickedSection = freeSections
                                .firstOrNull { it.label == pickerCountry }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable(
                                        role = Role.Button,
                                        onClickLabel = stringResource(
                                            R.string.vpn5g_picker_back
                                        ),
                                    ) { pickerCountry = null }
                                    .padding(vertical = 10.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.vpn5g_picker_back),
                                    color = Vpn5gTealLight,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            pickedSection?.servers?.forEach { server ->
                                val selected = server.guid == state.manualGuid
                                val hasPing = server.hasRealPing && server.pingMillis > 0
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (selected) Vpn5gTeal.copy(alpha = 0.30f)
                                            else Color.Transparent
                                        )
                                        .clickable(
                                            enabled = hasPing,
                                            role = Role.Button,
                                            onClickLabel = displayCountryName(
                                                server,
                                                stringResource(
                                                    R.string.vpn5g_country_unknown
                                                ),
                                            ),
                                        ) {
                                            if (onSelectManualServer(server.guid)) {
                                                freePickerExpanded = false
                                                pickerCountry = null
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 10.dp),
                                ) {
                                    Text(
                                        text = server.flag,
                                        fontSize = 22.sp,
                                        modifier = Modifier.padding(end = 12.dp),
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = displayCountryName(
                                                server,
                                                stringResource(
                                                    R.string.vpn5g_country_unknown
                                                ),
                                            ),
                                            color = Vpn5gOnDark,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            text = if (hasPing) {
                                                stringResource(
                                                    R.string.vpn5g_picker_healthy
                                                )
                                            } else {
                                                stringResource(
                                                    R.string.vpn5g_picker_no_ping
                                                )
                                            },
                                            color = if (hasPing) {
                                                colorPing
                                            } else {
                                                Vpn5gOnDarkMuted
                                            },
                                            fontSize = 13.sp,
                                        )
                                    }
                                    Text(
                                        text = if (hasPing) {
                                            stringResource(
                                                R.string.vpn5g_ping_ms,
                                                server.pingMillis,
                                            )
                                        } else {
                                            "—"
                                        },
                                        color = colorPing,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    if (selected) {
                                        Spacer(Modifier.width(8.dp))
                                        Vpn5gCheckIcon(color = Vpn5gTeal, size = 20.dp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // "Auto" row (v1.1.5 §3–§4): the best free server, one tap to
            // connect — it works independently of any manual pick (tapping
            // it clears the manual pick first). The chevron opens an info
            // panel explaining auto mode and, while a manual pick is
            // active, offers the return to auto. On this screen it is
            // the smart-location card: the fastest server, flag + ms.
            if (bestServer != null && serverTab != Vpn5gServerTab.SPECIAL) {
                item(key = "auto_row") {
                    Vpn5gExpandableSection(
                        title = stringResource(R.string.vpn5g_auto_server),
                        subtitle = bestServer.flag + " " + displayCountryName(
                            bestServer,
                            stringResource(R.string.vpn5g_country_unknown),
                        ),
                        badge = if (bestServer.hasRealPing && bestServer.pingMillis > 0L) {
                            stringResource(R.string.vpn5g_ping_ms, bestServer.pingMillis)
                        } else {
                            "—"
                        },
                        leading = { Vpn5gRoutesIcon(color = Vpn5gTeal, size = 26.dp) },
                        expanded = autoInfoExpanded,
                        onToggleExpand = { autoInfoExpanded = !autoInfoExpanded },
                        onHeaderClick = onAutoConnect,
                    ) {
                        Text(
                            text = stringResource(R.string.vpn5g_auto_info_body),
                            color = Vpn5gOnDarkMuted,
                            fontSize = 14.sp,
                        )
                        if (state.connectMode == Vpn5gConnectMode.MANUAL) {
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    onSelectAutoMode()
                                    autoInfoExpanded = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Vpn5gTeal,
                                    contentColor = Color.White,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.vpn5g_manual_back_to_auto
                                    ),
                                    fontSize = 15.sp,
                                )
                            }
                        }
                    }
                }
            }

            // Server-list tabs: Free / All locations / Special (X-VPN style).
            item(key = "server_tabs") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Vpn5gServerTab.entries.forEach { tab ->
                        val selected = serverTab == tab
                        val tabLabelRes = when (tab) {
                            Vpn5gServerTab.FREE -> R.string.vpn5g_tab_free
                            Vpn5gServerTab.ALL -> R.string.vpn5g_tab_all
                            Vpn5gServerTab.SPECIAL -> R.string.vpn5g_tab_special
                        }
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) Vpn5gTeal.copy(alpha = 0.25f)
                                    else Color.Transparent
                                )
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = stringResource(tabLabelRes),
                                ) { serverTab = tab }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(
                                text = stringResource(tabLabelRes),
                                color = if (selected) Vpn5gTeal else Vpn5gOnDarkMuted,
                                fontSize = 15.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }

            // Pro upgrade card (v1.1.21, Lantern): gold border + teal
            // button. No prices here — plans live on Premium.
            if (searchQuery.isBlank()) {
                item(key = "premium_banner") {
                    Vpn5gProUpgradeCard(onOpenPremium = onOpenPremium)
                }
            }

            // Redeem banner (v1.1.12): a visible "activate with code"
            // entry for free users; subscribers already have a plan,
            // so it hides for them (the Premium screen keeps its redeem
            // section for a further code).
            if (searchQuery.isBlank() && !state.isSubscriber) {
                item(key = "redeem_banner") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Vpn5gGold.copy(alpha = 0.15f))
                            .clickable(
                                role = Role.Button,
                                onClickLabel = stringResource(R.string.vpn5g_redeem_title),
                            ) { onOpenPremium() }
                            .padding(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.vpn5g_redeem_title),
                            color = Vpn5gGold,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.vpn5g_redeem_banner_body),
                            color = Vpn5gOnDark,
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.vpn5g_redeem_banner_button),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            // Recommended: the fastest free server, one tap to connect.
            // v1.1.14: on the FIRST page (home) — shown on the Free tab
            // too, not only on All. Hidden on the Special tab only.
            if (bestServer != null && searchQuery.isBlank() &&
                serverTab != Vpn5gServerTab.SPECIAL
            ) {
                item(key = "recommended_header") {
                    Text(
                        text = stringResource(R.string.vpn5g_recommended),
                        color = Vpn5gOnDarkMuted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start,
                    )
                }
                item(key = "recommended_fastest") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Vpn5gOnDark.copy(alpha = 0.06f))
                            .clickable(
                                role = Role.Button,
                                onClickLabel = displayCountryName(
                                    bestServer,
                                    stringResource(R.string.vpn5g_country_unknown),
                                ),
                            ) { onServerTap(bestServer.guid) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    ) {
                        Text(
                            text = bestServer.flag,
                            fontSize = 22.sp,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayCountryName(
                                    bestServer,
                                    stringResource(R.string.vpn5g_country_unknown),
                                ),
                                color = Vpn5gOnDark,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = stringResource(R.string.vpn5g_server_subtitle),
                                color = Vpn5gOnDarkMuted,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = if (bestServer.hasRealPing && bestServer.pingMillis > 0L) {
                                stringResource(R.string.vpn5g_ping_ms, bestServer.pingMillis)
                            } else {
                                "—"
                            },
                            color = colorPing,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            // Search by country or server name. Hidden on the Free tab
            // (v1.1.2): no server list is shown there.
            if (serverTab != Vpn5gServerTab.FREE) {
                item(key = "server_search") {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(stringResource(R.string.vpn5g_search_hint)) },
                        leadingIcon = { Vpn5gSearchIcon(color = Vpn5gOnDarkMuted, size = 20.dp) },
                        singleLine = true,
                        colors = vpn5gSearchFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Free tab (v1.1.2): no server rows at all — only the
            // free-service picker and the auto row above. The list is
            // always shown on the other tabs (v1.1.5 §3: the free-servers
            // row is now the picker, not a list toggle).
            // v1.1.14: on the ALL tab the subscription sections come
            // FIRST, then free — and the two floating filter pills
            // (اشتراکی / رایگان, subscription first) narrow the list.
            if (serverTab == Vpn5gServerTab.ALL) {
                item(key = "all_filter_pills") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            "sub" to R.string.vpn5g_filter_sub,
                            "free" to R.string.vpn5g_filter_free,
                        ).forEach { (key, labelRes) ->
                            val selected = allFilter == key
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        if (selected) Vpn5gTeal.copy(alpha = 0.25f)
                                        else Vpn5gOnDark.copy(alpha = 0.08f)
                                    )
                                    .border(
                                        width = if (selected) 1.dp else 0.dp,
                                        color = Vpn5gTeal,
                                        shape = RoundedCornerShape(20.dp),
                                    )
                                    .clickable(
                                        role = Role.Button,
                                        onClickLabel = stringResource(labelRes),
                                    ) {
                                        allFilter = if (selected) null else key
                                    }
                                    .padding(vertical = 10.dp),
                            ) {
                                Text(
                                    text = stringResource(labelRes),
                                    color = if (selected) Vpn5gTeal else Vpn5gOnDark,
                                    fontSize = 14.sp,
                                    fontWeight = if (selected) {
                                        FontWeight.Bold
                                    } else {
                                        FontWeight.Normal
                                    },
                                )
                            }
                        }
                    }
                }
            }
            // Gift-code button (v1.1.15, C): GIFT codes live in Premium
            // — this button opens Premium straight at the gift-code
            // section. It is NOT a buy button; no purchase lives in
            // All Locations.
            if (serverTab == Vpn5gServerTab.ALL) {
                item(key = "all_gift_code") {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Vpn5gGold.copy(alpha = 0.15f))
                            .border(
                                width = 1.dp,
                                color = Vpn5gGold,
                                shape = RoundedCornerShape(20.dp),
                            )
                            .clickable(
                                role = Role.Button,
                                onClickLabel = stringResource(R.string.vpn5g_gift_code_button),
                            ) { onOpenGiftCode() }
                            .padding(vertical = 10.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Vpn5gGiftIcon(color = Vpn5gGold, size = 20.dp)
                            Text(
                                text = stringResource(R.string.vpn5g_gift_code_button),
                                color = Vpn5gGold,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
            val ordered = when (serverTab) {
                Vpn5gServerTab.FREE -> emptyList()
                Vpn5gServerTab.ALL -> when (allFilter) {
                    "sub" -> lockedSections.map { it to true }
                    "free" -> freeSections.map { it to false }
                    else -> lockedSections.map { it to true } +
                        freeSections.map { it to false }
                }
                Vpn5gServerTab.SPECIAL -> lockedSections.map { it to true }
            }
            // Pro locations (Lantern): on the ALL tab the locked
            // countries come first, one Lantern row each — flag, country,
            // fastest protocol + ping, gold lock. Tapping a locked row
            // opens the subscription dialog; a subscriber taps to
            // connect to that country's fastest server. The header only
            // shows when locked rows are actually listed (never under
            // the "free" filter).
            if (serverTab == Vpn5gServerTab.ALL && allFilter != "free" &&
                lockedSections.isNotEmpty()
            ) {
                item(key = "pro_locations_header") {
                    Text(
                        text = stringResource(R.string.vpn5g_pro_locations),
                        color = Vpn5gOnDarkMuted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start,
                    )
                }
            }
            var promoShown = false
            ordered.forEach { (country, locked) ->
                if (serverTab == Vpn5gServerTab.ALL && locked && !promoShown) {
                    item(key = "bot_promo") {
                        Vpn5gBotPromoCard(onOpenPremium = onOpenPremium)
                    }
                    promoShown = true
                }
                item(key = "country_${if (locked) "locked" else "free"}_${country.label}") {
                    // Premium tab (v1.1.2): fast servers grouped by
                    // country; tapping a country reveals its servers.
                    val expandable = serverTab == Vpn5gServerTab.SPECIAL
                    Vpn5gCountrySection(
                        country = country,
                        onServerTap = if (locked) {
                            {}
                        } else {
                            onServerTap
                        },
                        onLockedTap = if (locked) {
                            { onLockedCountryTap(country.label) }
                        } else {
                            null
                        },
                        expandable = expandable,
                        expanded = !expandable ||
                            country.label in specialExpanded,
                        onToggleExpand = if (expandable) {
                            {
                                specialExpanded =
                                    if (country.label in specialExpanded) {
                                        specialExpanded - country.label
                                    } else {
                                        specialExpanded + country.label
                                    }
                            }
                        } else {
                            null
                        },
                    )
                }
            }

            item {
                Vpn5gFooter()
            }

            item {
                TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.vpn5g_nav_home),
                        color = Vpn5gTealLight,
                        fontSize = 14.sp,
                    )
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/**
 * The only name a server row may show: the Persian country name, the
 * country key when no Persian name is known, or [unknown] when neither
 * exists. The config remark is never displayed anywhere.
 */
internal fun displayCountryName(server: Vpn5gServerUi, unknown: String): String {
    return server.countryNameFa.ifBlank { server.country }.ifBlank { unknown }
}

@Composable
private fun vpn5gSearchFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Vpn5gOnDark,
    unfocusedTextColor = Vpn5gOnDark,
    focusedPlaceholderColor = Vpn5gOnDarkMuted,
    unfocusedPlaceholderColor = Vpn5gOnDarkMuted,
    focusedBorderColor = Vpn5gTeal,
    unfocusedBorderColor = Vpn5gOnDarkMuted,
    cursorColor = Vpn5gTeal,
)

@Composable
private fun Vpn5gBotPromoCard(onOpenPremium: () -> Unit) {
    // v1.1.15: the ALL-tab promo card opens Premium (no buy button in
    // All Locations); buying stays inside Premium.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Vpn5gTeal.copy(alpha = 0.25f))
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.vpn5g_premium_banner_button),
            ) {
                onOpenPremium()
            }
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.vpn5g_bot_promo_title),
            color = Vpn5gOnDark,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.vpn5g_bot_promo_body),
            color = Vpn5gOnDarkMuted,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.vpn5g_premium_banner_button),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun Vpn5gCountrySection(
    country: Vpn5gCountryUi,
    onServerTap: (String) -> Unit,
    onLockedTap: (() -> Unit)?,
    /** Premium tab (v1.1.2): the country header toggles the group. */
    expandable: Boolean = false,
    /** Collapsed groups hide their servers until tapped. */
    expanded: Boolean = true,
    onToggleExpand: (() -> Unit)? = null,
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .then(
                    when {
                        onToggleExpand != null -> Modifier.clickable(
                            role = Role.Button,
                            onClickLabel = country.label,
                        ) { onToggleExpand() }
                        onLockedTap != null -> Modifier.clickable(
                            role = Role.Button,
                            onClickLabel = stringResource(R.string.vpn5g_needs_subscription),
                        ) { onLockedTap() }
                        else -> Modifier
                    }
                ),
        ) {
            Text(
                text = country.label,
                color = Vpn5gOnDark,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.vpn5g_servers_count, country.servers.size),
                color = Vpn5gOnDarkMuted,
                fontSize = 13.sp,
            )
            if (!country.isFree) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.vpn5g_needs_subscription),
                    color = Vpn5gGold,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (expandable) {
                Spacer(Modifier.width(8.dp))
                Vpn5gChevronIcon(
                    color = Vpn5gOnDarkMuted,
                    modifier = Modifier.graphicsLayer {
                        rotationZ = if (expanded) 90f else 0f
                    },
                )
            }
        }
        if (!expanded) return@Column
        country.servers.forEach { server ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (server.isSelected) Vpn5gTeal.copy(alpha = 0.30f)
                        else Vpn5gOnDark.copy(alpha = 0.06f)
                    )
                    .clickable(
                        enabled = country.isFree || (expandable && onLockedTap != null),
                        role = Role.Button,
                        onClickLabel = displayCountryName(
                            server,
                            stringResource(R.string.vpn5g_country_unknown),
                        ),
                    ) {
                        if (country.isFree) {
                            onServerTap(server.guid)
                        } else {
                            // Locked row in an expandable group opens the
                            // subscription dialog (the header now toggles).
                            onLockedTap?.invoke()
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    text = server.flag,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    // Never the config remark: country name only.
                    Text(
                        text = displayCountryName(
                            server,
                            stringResource(R.string.vpn5g_country_unknown),
                        ),
                        color = Vpn5gOnDark,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.vpn5g_server_subtitle),
                        color = Vpn5gOnDarkMuted,
                        fontSize = 12.sp,
                    )
                }
                // VIP badge on every locked row (v1.1.2).
                if (!country.isFree) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.vpn5g_vip),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Vpn5gTeal)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Text(
                    // v1.1.7: a TCP-only ping is never shown as a number.
                    text = if (server.hasRealPing && server.pingMillis > 0L) {
                        stringResource(R.string.vpn5g_ping_ms, server.pingMillis)
                    } else {
                        "—"
                    },
                    color = if (server.pingMillis < 0L) colorPingRed else colorPing,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
