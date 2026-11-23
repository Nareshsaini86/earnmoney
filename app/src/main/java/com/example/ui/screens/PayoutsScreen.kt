package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.UiState
import com.example.data.model.WithdrawalDestinationType
import com.example.data.model.WithdrawalRequest
import com.example.data.model.WithdrawalStatus
import com.example.ui.viewmodel.RevenueSummary
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PayoutsScreen(
    withdrawalsState: UiState<List<WithdrawalRequest>>,
    summary: RevenueSummary,
    usdToInrRate: Double,
    isBusy: Boolean,
    onRequestWithdrawal: (WithdrawalDestinationType, String, String, Double) -> Unit,
    onUpdateStatus: (String, WithdrawalStatus) -> Unit
) {
    var destinationType by rememberSaveable { mutableStateOf(WithdrawalDestinationType.UPI) }
    var destinationLabel by rememberSaveable { mutableStateOf("") }
    var beneficiaryName by rememberSaveable { mutableStateOf("") }
    var amountInrText by rememberSaveable { mutableStateOf("") }
    var statusFilter by rememberSaveable { mutableStateOf<WithdrawalStatus?>(null) }

    val amountInr = amountInrText.toDoubleOrNull() ?: 0.0
    val usdcEq = if (usdToInrRate > 0) amountInr / usdToInrRate else 0.0
    val canSubmit = !isBusy &&
        amountInr >= 100.0 &&
        amountInr <= summary.withdrawableInr + 0.01 &&
        destinationLabel.trim().length in 3..120 &&
        beneficiaryName.trim().length in 2..80

    val allWithdrawals = (withdrawalsState as? UiState.Success)?.data.orEmpty()
    val filteredWithdrawals = remember(allWithdrawals, statusFilter) {
        if (statusFilter == null) allWithdrawals else allWithdrawals.filter { it.statusEnum == statusFilter }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "WITHDRAWABLE ₹ INR BALANCE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "₹${String.format("%,.2f", summary.withdrawableInr)}",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "≈ ${String.format("%.4f", summary.withdrawableInr / usdToInrRate)} USDC  •  Settled: ₹${String.format("%,.2f", summary.settledInr)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AccountBalance,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Withdraw Rupees / Payout Request",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        WithdrawalDestinationType.entries.forEach { rail ->
                            FilterChip(
                                selected = destinationType == rail,
                                onClick = { destinationType = rail },
                                label = { Text(rail.label) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = beneficiaryName,
                        onValueChange = { if (it.length <= 80) beneficiaryName = it },
                        label = { Text("Beneficiary / Account Holder Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("payout_beneficiary_input")
                    )

                    OutlinedTextField(
                        value = destinationLabel,
                        onValueChange = { if (it.length <= 120) destinationLabel = it },
                        label = { Text("${destinationType.label} Destination") },
                        placeholder = { Text(destinationType.hint) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("payout_destination_input")
                    )

                    OutlinedTextField(
                        value = amountInrText,
                        onValueChange = { amountInrText = it },
                        label = { Text("Amount in ₹ INR (Min ₹100)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        supportingText = {
                            if (amountInr > 0) {
                                Text("≈ ${String.format("%.4f", usdcEq)} USDC at ₹${String.format("%.2f", usdToInrRate)}/USD")
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("payout_amount_input")
                    )

                    if (summary.withdrawableInr >= 100.0) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(0.25 to "25%", 0.50 to "50%", 1.0 to "Max").forEach { (pct, label) ->
                                OutlinedButton(
                                    onClick = {
                                        val v = (summary.withdrawableInr * pct).coerceAtLeast(100.0)
                                        amountInrText = String.format(Locale.US, "%.2f", v.coerceAtMost(summary.withdrawableInr))
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(label)
                                }
                            }
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = stringResource(R.string.payout_disclosure),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = {
                            onRequestWithdrawal(
                                destinationType,
                                destinationLabel,
                                beneficiaryName,
                                amountInr
                            )
                            amountInrText = ""
                        },
                        enabled = canSubmit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("submit_withdrawal_button")
                    ) {
                        Icon(Icons.Rounded.Payments, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Submit Withdrawal Request")
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Payout Settlement History",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = statusFilter == null,
                        onClick = { statusFilter = null },
                        label = { Text("All (${allWithdrawals.size})") }
                    )
                    WithdrawalStatus.entries.forEach { st ->
                        FilterChip(
                            selected = statusFilter == st,
                            onClick = { statusFilter = st },
                            label = { Text(st.label) }
                        )
                    }
                }
            }
        }

        when (withdrawalsState) {
            is UiState.Loading -> item {
                Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = withdrawalsState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
            is UiState.Success -> {
                if (filteredWithdrawals.isEmpty()) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "No payout requests in this view yet. Earn or sync USDC revenue on the Revenue tab, then submit a UPI/IMPS/USDC payout above.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(filteredWithdrawals, key = { it.id }) { req ->
                        WithdrawalItemCard(
                            request = req,
                            isBusy = isBusy,
                            onUpdateStatus = { newStatus -> onUpdateStatus(req.id, newStatus) }
                        )
                    }
                }
            }
        }

        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
private fun WithdrawalItemCard(
    request: WithdrawalRequest,
    isBusy: Boolean,
    onUpdateStatus: (WithdrawalStatus) -> Unit
) {
    val statusColor = when (request.statusEnum) {
        WithdrawalStatus.REQUESTED -> MaterialTheme.colorScheme.secondary
        WithdrawalStatus.PROCESSING -> MaterialTheme.colorScheme.tertiary
        WithdrawalStatus.SETTLED -> MaterialTheme.colorScheme.primary
        WithdrawalStatus.CANCELLED -> MaterialTheme.colorScheme.error
    }
    val statusIcon = when (request.statusEnum) {
        WithdrawalStatus.REQUESTED -> Icons.Rounded.Schedule
        WithdrawalStatus.PROCESSING -> Icons.Rounded.Payments
        WithdrawalStatus.SETTLED -> Icons.Rounded.CheckCircle
        WithdrawalStatus.CANCELLED -> Icons.Rounded.Cancel
    }
    val dateStr = remember(request.createdAt) {
        val d = request.createdAt?.toDate()
        if (d != null) SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(d) else "Pending"
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
                Column {
                    Text(
                        text = "₹${String.format("%,.2f", request.amountInr)}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${request.referenceCode} • ${request.destinationEnum.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    color = statusColor.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(50.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = statusIcon,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = request.statusEnum.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor
                        )
                    }
                }
            }

            Text(
                text = "${request.beneficiaryName} → ${request.destinationLabel}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${String.format("%.4f", request.amountUsdcEquivalent)} USDC eq. • $dateStr",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (request.statusEnum == WithdrawalStatus.REQUESTED || request.statusEnum == WithdrawalStatus.PROCESSING) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (request.statusEnum == WithdrawalStatus.REQUESTED) {
                        OutlinedButton(
                            onClick = { onUpdateStatus(WithdrawalStatus.PROCESSING) },
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Processing")
                        }
                    }
                    Button(
                        onClick = { onUpdateStatus(WithdrawalStatus.SETTLED) },
                        enabled = !isBusy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Mark Settled")
                    }
                    OutlinedButton(
                        onClick = { onUpdateStatus(WithdrawalStatus.CANCELLED) },
                        enabled = !isBusy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}
