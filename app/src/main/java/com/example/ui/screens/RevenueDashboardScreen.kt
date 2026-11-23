package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CurrencyRupee
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.AutomatonNode
import com.example.data.model.OnChainWalletBalance
import com.example.data.model.RevenueChannel
import com.example.data.model.RevenueEntryType
import com.example.data.model.RevenueEvent
import com.example.data.model.SurvivalTier
import com.example.data.model.UiState
import com.example.data.model.UsdInrRateInfo
import com.example.ui.viewmodel.RevenueSummary
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun RevenueDashboardScreen(
    automatonsState: UiState<List<AutomatonNode>>,
    eventsState: UiState<List<RevenueEvent>>,
    fxRateState: UiState<UsdInrRateInfo>,
    summary: RevenueSummary,
    onChainCheckState: UiState<OnChainWalletBalance>?,
    isBusy: Boolean,
    onRefreshFx: () -> Unit,
    onCheckBaseWallet: (String, AutomatonNode?) -> Unit,
    onRecordEvent: (AutomatonNode, RevenueChannel, RevenueEntryType, Double, String, String) -> Unit,
    onRegisterNode: (String, String, com.example.data.model.MonetizationModel, String, String) -> Unit,
    onDeleteNode: (String) -> Unit,
    onNavigateToPayouts: () -> Unit
) {
    val nodes = (automatonsState as? UiState.Success)?.data.orEmpty()
    val events = (eventsState as? UiState.Success)?.data.orEmpty()
    val fxRate = (fxRateState as? UiState.Success)?.data?.rate ?: 84.5

    var showLogEventDialog by rememberSaveable { mutableStateOf(false) }
    var showRegisterDialog by rememberSaveable { mutableStateOf(false) }
    var lookupAddress by rememberSaveable { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            HeroRevenueCard(
                summary = summary,
                fxRateState = fxRateState,
                onRefreshFx = onRefreshFx,
                onWithdrawClick = onNavigateToPayouts,
                onLogEventClick = {
                    if (nodes.isEmpty()) showRegisterDialog = true else showLogEventDialog = true
                },
                hasNodes = nodes.isNotEmpty()
            )
        }

        item {
            HowAutomatonEarnsCard(
                events = events,
                fxRate = fxRate,
                summary = summary
            )
        }

        item {
            BaseOnChainCheckerCard(
                address = lookupAddress,
                onAddressChange = { lookupAddress = it },
                checkState = onChainCheckState,
                onCheck = { onCheckBaseWallet(lookupAddress, null) }
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Registered Automaton Nodes (${nodes.size})",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                TextButton(onClick = { showRegisterDialog = true }) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("New Node")
                }
            }
        }

        when (automatonsState) {
            is UiState.Loading -> item {
                Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> item {
                ErrorBannerCard(automatonsState.message)
            }
            is UiState.Success -> {
                if (nodes.isEmpty()) {
                    item {
                        EmptyRevenueNodesCard(onRegister = { showRegisterDialog = true })
                    }
                } else {
                    items(nodes, key = { it.id }) { node ->
                        AutomatonNodeCard(
                            node = node,
                            fxRate = fxRate,
                            isBusy = isBusy,
                            onSyncBase = {
                                if (node.walletAddress.isNotBlank()) {
                                    onCheckBaseWallet(node.walletAddress, node)
                                }
                            },
                            onDelete = { onDeleteNode(node.id) }
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Monetization & Burn Ledger",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (nodes.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { showLogEventDialog = true },
                        modifier = Modifier.testTag("log_revenue_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Log Entry")
                    }
                }
            }
        }

        when (eventsState) {
            is UiState.Loading -> item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> item {
                ErrorBannerCard(eventsState.message)
            }
            is UiState.Success -> {
                if (events.isEmpty()) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "No monetization or compute burn entries yet. Tap 'Log Entry' to record an x402 payment, bounty payout, or LLM compute burn.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(events, key = { it.id }) { event ->
                        RevenueEventRow(event = event)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }

    if (showLogEventDialog && nodes.isNotEmpty()) {
        LogRevenueEventDialog(
            nodes = nodes,
            fxRate = fxRate,
            onDismiss = { showLogEventDialog = false },
            onSubmit = { node, channel, type, amountUsdc, txRef, note ->
                onRecordEvent(node, channel, type, amountUsdc, txRef, note)
                showLogEventDialog = false
            }
        )
    }

    if (showRegisterDialog) {
        RegisterNodeDialog(
            initialPrompt = "",
            onDismiss = { showRegisterDialog = false },
            onSubmit = { name, wallet, model, prompt, endpoint ->
                onRegisterNode(name, wallet, model, prompt, endpoint)
                showRegisterDialog = false
            }
        )
    }
}

@Composable
private fun HeroRevenueCard(
    summary: RevenueSummary,
    fxRateState: UiState<UsdInrRateInfo>,
    onRefreshFx: () -> Unit,
    onWithdrawClick: () -> Unit,
    onLogEventClick: () -> Unit,
    hasNodes: Boolean
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "NET AUTOMATON REVENUE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(50.dp),
                        onClick = onRefreshFx
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val rateLabel = when (fxRateState) {
                                is UiState.Success -> "1 USDC = ₹${String.format("%.2f", fxRateState.data.rate)}"
                                is UiState.Loading -> "FX Loading..."
                                is UiState.Error -> "FX Retry"
                            }
                            Text(
                                text = rateLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Refresh FX rate",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "₹${String.format("%,.2f", summary.withdrawableInr)}",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Withdrawable",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricPill(
                        label = "Gross Earned",
                        value = "₹${String.format("%,.0f", summary.totalEarnedInr)} ($${String.format("%.2f", summary.totalEarnedUsdc)})",
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    MetricPill(
                        label = "Compute Burn",
                        value = "₹${String.format("%,.0f", summary.totalBurnInr)} ($${String.format("%.2f", summary.totalBurnUsdc)})",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                }

                val earnColor = MaterialTheme.colorScheme.primary
                val burnColor = MaterialTheme.colorScheme.error
                val trackColor = MaterialTheme.colorScheme.outlineVariant
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                ) {
                    drawRoundRect(
                        color = trackColor,
                        cornerRadius = CornerRadius(12f, 12f),
                        size = size
                    )
                    val total = (summary.totalEarnedUsdc + summary.totalBurnUsdc).coerceAtLeast(0.001)
                    if (summary.totalEarnedUsdc > 0 || summary.totalBurnUsdc > 0) {
                        val earnWidth = ((summary.totalEarnedUsdc / total) * size.width).toFloat()
                        drawRoundRect(
                            color = earnColor,
                            cornerRadius = CornerRadius(12f, 12f),
                            size = Size(earnWidth, size.height)
                        )
                        val burnWidth = ((summary.totalBurnUsdc / total) * size.width).toFloat()
                        if (burnWidth > 1f) {
                            drawRect(
                                color = burnColor,
                                topLeft = Offset(earnWidth, 0f),
                                size = Size(burnWidth, size.height)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onWithdrawClick,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("hero_withdraw_button")
                    ) {
                        Icon(Icons.Rounded.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Withdraw ₹")
                    }
                    OutlinedButton(
                        onClick = onLogEventClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (hasNodes) "Log Entry" else "Add Node")
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricPill(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.65f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = color,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun HowAutomatonEarnsCard(
    events: List<RevenueEvent>,
    fxRate: Double,
    summary: RevenueSummary
) {
    val channelTotals = remember(events) {
        RevenueChannel.entries.associateWith { ch ->
            events.filter { it.channelEnum == ch }.sumOf { it.amountUsdc }
        }
    }
    val maxChannel = channelTotals.values.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CurrencyRupee,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = "How Automaton Earns Rupees (₹)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "Conway Automaton earns USDC on Base (via x402 API paywalls, task bounties, & skill licensing), pays its own LLM/sandbox compute burn, and converts net USDC profit into ₹ INR.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            RevenueChannel.entries.forEach { ch ->
                val usdc = channelTotals[ch] ?: 0.0
                val inr = usdc * fxRate
                val isBurn = ch == RevenueChannel.COMPUTE_BURN
                val barColor = if (isBurn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                val fraction = (usdc / maxChannel).toFloat().coerceIn(0f, 1f)

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = ch.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "₹${String.format("%,.2f", inr)} ($${String.format("%.2f", usdc)})",
                            style = MaterialTheme.typography.labelSmall,
                            color = barColor
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction = if (usdc > 0) fraction.coerceAtLeast(0.06f) else 0f)
                                .height(6.dp)
                                .background(barColor, RoundedCornerShape(8.dp))
                        )
                    }
                }
            }

            if (summary.settledInr > 0) {
                Text(
                    text = "Already Settled / Withdrawn: ₹${String.format("%,.2f", summary.settledInr)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun BaseOnChainCheckerCard(
    address: String,
    onAddressChange: (String) -> Unit,
    checkState: UiState<OnChainWalletBalance>?,
    onCheck: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.AccountBalanceWallet,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
                Text(
                    text = "Live Base Mainnet USDC Balance Check",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            OutlinedTextField(
                value = address,
                onValueChange = { if (it.length <= 64) onAddressChange(it) },
                label = { Text("Paste Automaton Base 0x... Wallet") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = onCheck,
                enabled = address.trim().length == 42 && checkState !is UiState.Loading,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Query Base RPC & Convert to ₹")
            }

            when (checkState) {
                is UiState.Loading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("Querying mainnet.base.org...", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is UiState.Error -> {
                    Text(
                        text = checkState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                is UiState.Success -> {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(
                                text = "On-Chain Base USDC: ${String.format("%.4f", checkState.data.usdcBalance)} USDC",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "INR Value: ₹${String.format("%,.2f", checkState.data.inrEquivalent)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                null -> Unit
            }
        }
    }
}

@Composable
private fun AutomatonNodeCard(
    node: AutomatonNode,
    fxRate: Double,
    isBusy: Boolean,
    onSyncBase: () -> Unit,
    onDelete: () -> Unit
) {
    val tierColor = when (node.tierEnum) {
        SurvivalTier.THRIVING -> MaterialTheme.colorScheme.primary
        SurvivalTier.NORMAL -> MaterialTheme.colorScheme.tertiary
        SurvivalTier.LOW_COMPUTE -> MaterialTheme.colorScheme.secondary
        SurvivalTier.CRITICAL -> MaterialTheme.colorScheme.error
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = node.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = node.modelEnum.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    color = tierColor.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(50.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = null,
                            tint = tierColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = node.tierEnum.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = tierColor
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Net: ₹${String.format("%,.2f", node.netUsdc * fxRate)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "+$${String.format("%.2f", node.earnedUsdc)} / -$${String.format("%.2f", node.computeBurnUsdc)} USDC",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (node.walletAddress.isNotBlank()) {
                Text(
                    text = "Wallet: ${node.walletAddress}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (node.walletAddress.matches(Regex("^0x[0-9a-fA-F]{40}$"))) {
                    OutlinedButton(
                        onClick = onSyncBase,
                        enabled = !isBusy
                    ) {
                        Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Sync On-Chain")
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }
                IconButton(onClick = onDelete, enabled = !isBusy) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete node",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun RevenueEventRow(event: RevenueEvent) {
    val isEarning = event.typeEnum == RevenueEntryType.EARNING
    val accent = if (isEarning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val dateStr = remember(event.createdAt) {
        val d = event.createdAt?.toDate()
        if (d != null) SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(d) else "Pending"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = accent.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isEarning) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "${event.channelEnum.label} • ${event.automatonName}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = listOfNotNull(
                            dateStr,
                            event.note.takeIf { it.isNotBlank() }
                        ).joinToString(" • "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (isEarning) "+" else "-"}₹${String.format("%,.2f", event.amountInr)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = accent
                )
                Text(
                    text = "${String.format("%.2f", event.amountUsdc)} USDC",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyRevenueNodesCard(onRegister: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "No Automaton Nodes Registered Yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Add your Conway Automaton node (and optional Base 0x wallet) to track live x402 earnings, compute burn, and withdrawable ₹ INR.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onRegister) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Register First Node")
            }
        }
    }
}

@Composable
private fun ErrorBannerCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(14.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(14.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogRevenueEventDialog(
    nodes: List<AutomatonNode>,
    fxRate: Double,
    onDismiss: () -> Unit,
    onSubmit: (AutomatonNode, RevenueChannel, RevenueEntryType, Double, String, String) -> Unit
) {
    var selectedNode by remember { mutableStateOf(nodes.first()) }
    var nodeExpanded by remember { mutableStateOf(false) }
    var entryType by rememberSaveable { mutableStateOf(RevenueEntryType.EARNING) }
    var channel by rememberSaveable { mutableStateOf(RevenueChannel.X402_MICROPAYMENT) }
    var amountUsdcStr by rememberSaveable { mutableStateOf("") }
    var txRef by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }

    val parsedUsdc = amountUsdcStr.toDoubleOrNull() ?: 0.0
    val valid = parsedUsdc > 0.0 && parsedUsdc <= 1_000_000.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log Revenue / Compute Burn") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ExposedDropdownMenuBox(
                    expanded = nodeExpanded,
                    onExpandedChange = { nodeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedNode.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Automaton Node") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = nodeExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = nodeExpanded,
                        onDismissRequest = { nodeExpanded = false }
                    ) {
                        nodes.forEach { n ->
                            DropdownMenuItem(
                                text = { Text(n.name) },
                                onClick = {
                                    selectedNode = n
                                    nodeExpanded = false
                                }
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = entryType == RevenueEntryType.EARNING,
                        onClick = {
                            entryType = RevenueEntryType.EARNING
                            if (channel == RevenueChannel.COMPUTE_BURN) {
                                channel = RevenueChannel.X402_MICROPAYMENT
                            }
                        },
                        label = { Text("Earning (+)") }
                    )
                    FilterChip(
                        selected = entryType == RevenueEntryType.BURN,
                        onClick = {
                            entryType = RevenueEntryType.BURN
                            channel = RevenueChannel.COMPUTE_BURN
                        },
                        label = { Text("Compute Burn (-)") }
                    )
                }

                if (entryType == RevenueEntryType.EARNING) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            RevenueChannel.X402_MICROPAYMENT to "x402",
                            RevenueChannel.BOUNTY_PAYOUT to "Bounty",
                            RevenueChannel.SKILL_LICENSE to "Skill"
                        ).forEach { (ch, label) ->
                            FilterChip(
                                selected = channel == ch,
                                onClick = { channel = ch },
                                label = { Text(label) }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = amountUsdcStr,
                    onValueChange = { amountUsdcStr = it },
                    label = { Text("Amount in USDC") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = {
                        if (parsedUsdc > 0) {
                            Text("≈ ₹${String.format("%,.2f", parsedUsdc * fxRate)} INR at ₹${String.format("%.2f", fxRate)}/USD")
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = txRef,
                    onValueChange = { if (it.length <= 128) txRef = it },
                    label = { Text("Base Tx Hash / Invoice ID (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 240) note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(selectedNode, channel, entryType, parsedUsdc, txRef, note) },
                enabled = valid
            ) {
                Text("Save Entry")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
