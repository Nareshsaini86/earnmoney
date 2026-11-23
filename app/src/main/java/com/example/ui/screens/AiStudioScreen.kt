package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.MonetizationModel
import com.example.ui.viewmodel.AiStreamState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AiStudioScreen(
    aiStreamState: AiStreamState,
    usdToInrRate: Double,
    isBusy: Boolean,
    onGenerateStrategy: (MonetizationModel, Double, String) -> Unit,
    onRegisterNode: (String, String, MonetizationModel, String, String) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedNiche by rememberSaveable { mutableStateOf(MonetizationModel.X402_API) }
    var budgetUsdc by rememberSaveable { mutableFloatStateOf(25f) }
    var customIdea by rememberSaveable { mutableStateOf("") }
    var showSaveDialog by rememberSaveable { mutableStateOf(false) }

    val budgetInr = budgetUsdc * usdToInrRate
    val isGenerating = aiStreamState is AiStreamState.Loading || aiStreamState is AiStreamState.Streaming

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Gemini Genesis Prompt Architect",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "Conway Automaton ke liye safe, anti-infinite-burn Genesis Prompt aur ₹ INR monetization plan generate karein.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "Select Monetization Model",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MonetizationModel.entries.forEach { model ->
                            FilterChip(
                                selected = selectedNiche == model,
                                onClick = { selectedNiche = model },
                                label = { Text(model.label) }
                            )
                        }
                    }

                    Text(
                        text = selectedNiche.howItEarns,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Starting Compute Budget",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = "$${String.format("%.0f", budgetUsdc)} USDC (≈ ₹${String.format("%,.0f", budgetInr)})",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = budgetUsdc,
                            onValueChange = { budgetUsdc = it },
                            valueRange = 5f..200f,
                            steps = 38
                        )
                    }

                    OutlinedTextField(
                        value = customIdea,
                        onValueChange = { if (it.length <= 240) customIdea = it },
                        label = { Text("Custom Service Idea (Optional)") },
                        placeholder = { Text("e.g. Paid GST / crypto tax JSON validator API via x402") },
                        minLines = 2,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            onGenerateStrategy(selectedNiche, budgetUsdc.toDouble(), customIdea)
                        },
                        enabled = !isGenerating,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("generate_genesis_button")
                    ) {
                        if (aiStreamState is AiStreamState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Connecting to Gemini...")
                        } else {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (aiStreamState is AiStreamState.Streaming) "Streaming Blueprint..." else "Generate Genesis Prompt & Plan")
                        }
                    }
                }
            }
        }

        item {
            when (aiStreamState) {
                is AiStreamState.Idle -> {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Tap 'Generate Genesis Prompt & Plan' above to stream a custom Conway Automaton setup prompt and ₹ INR break-even calculation powered by Firebase AI Logic.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                }
                is AiStreamState.Loading -> Unit
                is AiStreamState.Error -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = aiStreamState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                is AiStreamState.Streaming -> {
                    AiOutputCard(
                        text = aiStreamState.text,
                        isStreaming = true,
                        isBusy = isBusy,
                        onCopy = {
                            copyToClipboard(context, "Genesis Strategy", aiStreamState.text)
                            onShowMessage("Copied to clipboard")
                        },
                        onSaveNode = { showSaveDialog = true }
                    )
                }
                is AiStreamState.Success -> {
                    AiOutputCard(
                        text = aiStreamState.text,
                        isStreaming = false,
                        isBusy = isBusy,
                        onCopy = {
                            copyToClipboard(context, "Genesis Strategy", aiStreamState.text)
                            onShowMessage("Copied Genesis Prompt & Plan")
                        },
                        onSaveNode = { showSaveDialog = true }
                    )
                }
            }
        }
    }

    if (showSaveDialog) {
        val outputText = when (aiStreamState) {
            is AiStreamState.Success -> aiStreamState.text.take(3800)
            is AiStreamState.Streaming -> aiStreamState.text.take(3800)
            else -> ""
        }
        RegisterNodeDialog(
            initialPrompt = outputText,
            onDismiss = { showSaveDialog = false },
            onSubmit = { name, wallet, model, prompt, endpoint ->
                onRegisterNode(name, wallet, model, prompt, endpoint)
                showSaveDialog = false
            }
        )
    }
}

@Composable
private fun AiOutputCard(
    text: String,
    isStreaming: Boolean,
    isBusy: Boolean,
    onCopy: () -> Unit,
    onSaveNode: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
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
                Text(
                    text = if (isStreaming) "Streaming Blueprint..." else "Generated Blueprint",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (isStreaming) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            }

            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (!isStreaming) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onCopy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy")
                    }
                    Button(
                        onClick = onSaveNode,
                        enabled = !isBusy,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.AddCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save Node")
                    }
                }
            }
        }
    }
}
