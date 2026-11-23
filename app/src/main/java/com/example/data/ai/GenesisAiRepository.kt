package com.example.data.ai

import com.example.data.model.MonetizationModel
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeout

class GenesisAiRepository {
    private val model by lazy {
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    fun streamGenesisStrategy(
        niche: MonetizationModel,
        budgetUsdc: Double,
        usdToInrRate: Double,
        customIdea: String
    ): Flow<String> {
        val budgetInr = String.format("%.0f", budgetUsdc * usdToInrRate)
        val prompt = buildString {
            appendLine("You are an expert architect for Conway-Research/automaton (the open-source autonomous AI agent framework on Base with x402 USDC micropayments).")
            appendLine("Create a practical, realistic, safe setup blueprint for the user in clear Hinglish/English mix:")
            appendLine("1. Selected Monetization Model: ${niche.label} (${niche.howItEarns})")
            appendLine("2. Initial Compute Budget: $${String.format("%.2f", budgetUsdc)} USDC (~₹$budgetInr INR at ₹${String.format("%.2f", usdToInrRate)}/USD)")
            if (customIdea.isNotBlank()) {
                appendLine("3. User's Custom Service Idea: $customIdea")
            }
            appendLine()
            appendLine("Provide strictly these 3 concise sections:")
            appendLine("## 1. Hardened Genesis Prompt (Paste into Automaton Wizard)")
            appendLine("- Include concrete x402 pricing or bounty rules, strict daily burn cap, anti-infinite-burn sleep guard (halt or switch to low-compute if ROI < 1.2x), and Constitution compliance.")
            appendLine("## 2. How It Earns Rupees (₹) Mechanics")
            appendLine("- Explain exact cashflow: Customer/Agent pays x402 USDC on Base -> Net USDC after LLM/sandbox compute cost -> Convert to ₹ INR.")
            appendLine("- Give a realistic break-even calculation (e.g. calls/bounties needed per day to cover compute).")
            appendLine("## 3. Safety & Reality Checks")
            appendLine("- Remind user about testing in dry-run/testnet first and monitoring logs so funds do not drain.")
        }

        return model.generateContentStream(prompt).mapNotNull { chunk -> chunk.text }
    }

    suspend fun generateQuickPromptOnly(
        niche: MonetizationModel,
        customIdea: String
    ): String {
        val prompt = """
            Write ONLY a clean, production-ready Conway Automaton Genesis Prompt (120-220 words, plain text, no markdown fences) for:
            Model: ${niche.label}
            Idea: ${customIdea.ifBlank { niche.howItEarns }}
            Include: clear revenue target, x402/Base settlement rule, strict anti-infinite-burn guard, and Constitution safety rule.
        """.trimIndent()
        val response = withTimeout(60_000L) {
            model.generateContent(prompt)
        }
        return response.text?.trim().orEmpty()
    }
}
