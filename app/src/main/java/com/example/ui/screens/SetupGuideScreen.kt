package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ForkRight
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.GitHubRepoInfo
import com.example.data.model.MonetizationModel
import com.example.data.model.UiState

private data class SetupStepItem(
    val title: String,
    val detail: String,
    val cliCommand: String?,
    val verdictTag: String
)

private val CONWAY_SETUP_STEPS = listOf(
    SetupStepItem(
        title = "1. Clone & Build Conway-Research/automaton",
        detail = "Verified against official Conway Research TypeScript repo. Runs on Linux/macOS/VPS Node.js sandbox (not directly inside Android runtime).",
        cliCommand = "git clone https://github.com/Conway-Research/automaton.git && cd automaton && npm install && npm run build",
        verdictTag = "VERIFIED REPO"
    ),
    SetupStepItem(
        title = "2. Run Setup Wizard & Generate SIWE Wallet",
        detail = "First boot launches interactive setup: generates an Ethereum/Base 0x wallet, provisions Sign-In With Ethereum (SIWE) API credentials, and stores creator address.",
        cliCommand = "node dist/index.js --run",
        verdictTag = "BASE WALLET"
    ),
    SetupStepItem(
        title = "3. Fund Initial Compute Credits (USDC on Base)",
        detail = "Reality check: Conway Automaton is NOT zero-investment free money. It pays for LLM inference & sandbox compute from its Base USDC wallet.",
        cliCommand = null,
        verdictTag = "REQUIRED CAPITAL"
    ),
    SetupStepItem(
        title = "4. Harden Genesis Prompt (Anti-Infinite-Burn)",
        detail = "Mulazim guide tip confirmed: set strict daily spend caps & survival sleep rules in your Genesis Prompt so the agent doesn't drain credits in an idle Think-Act loop.",
        cliCommand = "curl -fsSL https://conway.tech/automaton.sh | sh",
        verdictTag = "SAFETY GUARD"
    ),
    SetupStepItem(
        title = "5. Expose x402 Paid Endpoint & Track ₹ INR",
        detail = "Configure x402 HTTP 402 paywall or bounty worker, then register your node & Base 0x address below to track live USDC → ₹ INR revenue & payouts here.",
        cliCommand = null,
        verdictTag = "MONETIZATION"
    )
)

@Composable
fun SetupGuideScreen(
    repoState: UiState<GitHubRepoInfo>,
    isBusy: Boolean,
    onRefreshRepo: () -> Unit,
    onOpenAiStudio: () -> Unit,
    onRegisterNode: (String, String, MonetizationModel, String, String) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val completedSteps = remember { mutableStateListOf(false, false, false, false, false) }
    var showReadme by rememberSaveable { mutableStateOf(false) }
    var showAddNodeDialog by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            AuditVerdictCard(onOpenAiStudio = onOpenAiStudio)
        }

        item {
            LiveGitHubRepoCard(
                repoState = repoState,
                showReadme = showReadme,
                onToggleReadme = { showReadme = !showReadme },
                onRefresh = onRefreshRepo,
                onOpenUrl = { url ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                }
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Setup Checklist (Verified Steps)",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${completedSteps.count { it }} of ${CONWAY_SETUP_STEPS.size} steps completed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = { showAddNodeDialog = true },
                    enabled = !isBusy,
                    modifier = Modifier.testTag("register_node_button")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Node")
                }
            }
        }

        itemsIndexed(CONWAY_SETUP_STEPS) { index, step ->
            val checked = completedSteps[index]
            SetupStepCard(
                step = step,
                checked = checked,
                onToggle = { completedSteps[index] = !checked },
                onCopyCli = { cmd ->
                    copyToClipboard(context, "Conway CLI", cmd)
                    onShowMessage("Copied CLI command to clipboard")
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showAddNodeDialog) {
        RegisterNodeDialog(
            initialPrompt = "",
            onDismiss = { showAddNodeDialog = false },
            onSubmit = { name, wallet, model, prompt, endpoint ->
                onRegisterNode(name, wallet, model, prompt, endpoint)
                showAddNodeDialog = false
            }
        )
    }
}

@Composable
private fun AuditVerdictCard(onOpenAiStudio: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Verified,
                    contentDescription = "Verified",
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Guide & Repo Audit: Legit with Risk Guards",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = "Mulazim blog ke Conway Automaton setup steps real open-source repo (Conway-Research/automaton) par based hain. Agent khud ka Base wallet & x402 API chala kar USDC kama sakta hai — lekin ye automatic money machine nahi hai.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "Infinite-Burn Risk: Bina strict Genesis Prompt aur daily USDC cap ke agent sleep-wake loop me compute credits kharch kar deta hai.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            OutlinedButton(
                onClick = onOpenAiStudio,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("open_ai_studio_cta")
            ) {
                Text("Generate Safe Genesis Prompt with Gemini")
            }
        }
    }
}

@Composable
private fun LiveGitHubRepoCard(
    repoState: UiState<GitHubRepoInfo>,
    showReadme: Boolean,
    onToggleReadme: () -> Unit,
    onRefresh: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Code,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        text = "Conway-Research/automaton",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.testTag("refresh_github_button")
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh GitHub stats")
                }
            }

            when (repoState) {
                is UiState.Loading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = "Fetching live GitHub repository metadata...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is UiState.Error -> {
                    Text(
                        text = "Live GitHub fetch failed (${repoState.message}). Tap refresh to retry.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                is UiState.Success -> {
                    val repo = repoState.data
                    Text(
                        text = repo.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RepoStatBadge(
                            icon = Icons.Rounded.Star,
                            label = "${repo.stars} Stars",
                            modifier = Modifier.weight(1f)
                        )
                        RepoStatBadge(
                            icon = Icons.Rounded.ForkRight,
                            label = "${repo.forks} Forks",
                            modifier = Modifier.weight(1f)
                        )
                        RepoStatBadge(
                            icon = Icons.Rounded.Code,
                            label = repo.language,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (repo.readmeSnippet.isNotBlank()) {
                            OutlinedButton(
                                onClick = onToggleReadme,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (showReadme) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(if (showReadme) "Hide README" else "Preview README")
                            }
                        }
                        OutlinedButton(
                            onClick = { onOpenUrl(repo.htmlUrl) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("GitHub")
                        }
                    }

                    AnimatedVisibility(visible = showReadme && repo.readmeSnippet.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.background,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Text(
                                text = repo.readmeSnippet,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RepoStatBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SetupStepCard(
    step: SetupStepItem,
    checked: Boolean,
    onToggle: () -> Unit,
    onCopyCli: (String) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (checked) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ),
        border = BorderStroke(
            1.dp,
            if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
    ) {
        Column(
            modifier = Modifier.padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (checked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = if (checked) "Completed" else "Pending",
                        tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = step.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = step.verdictTag,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = step.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (!step.cliCommand.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = step.cliCommand,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { onCopyCli(step.cliCommand) }) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = "Copy command",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterNodeDialog(
    initialPrompt: String,
    onDismiss: () -> Unit,
    onSubmit: (name: String, wallet: String, model: MonetizationModel, prompt: String, endpoint: String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var wallet by rememberSaveable { mutableStateOf("") }
    var endpoint by rememberSaveable { mutableStateOf("") }
    var prompt by rememberSaveable {
        mutableStateOf(
            initialPrompt.ifBlank {
                "Run an x402 paid utility API on Base. Charge 0.02 USDC per call. Pause inference if 24h net profit drops below 1.2x compute burn."
            }
        )
    }
    var selectedModel by rememberSaveable { mutableStateOf(MonetizationModel.X402_API) }
    var modelMenuExpanded by remember { mutableStateOf(false) }

    val canSave = name.trim().length >= 2 && prompt.trim().length >= 10

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Register Automaton Node") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 60) name = it },
                    label = { Text("Node Name (e.g. Alpha-x402)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(
                    expanded = modelMenuExpanded,
                    onExpandedChange = { modelMenuExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedModel.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Monetization Channel") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelMenuExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = modelMenuExpanded,
                        onDismissRequest = { modelMenuExpanded = false }
                    ) {
                        MonetizationModel.entries.forEach { model ->
                            DropdownMenuItem(
                                text = { Text(model.label) },
                                onClick = {
                                    selectedModel = model
                                    modelMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = wallet,
                    onValueChange = { if (it.length <= 64) wallet = it },
                    label = { Text("Base Wallet 0x... (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = { if (it.length <= 250) endpoint = it },
                    label = { Text("x402 Endpoint URL (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { if (it.length <= 4000) prompt = it },
                    label = { Text("Genesis Prompt (min 10 chars)") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(name, wallet, selectedModel, prompt, endpoint) },
                enabled = canSave
            ) {
                Text("Save Node")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
}
