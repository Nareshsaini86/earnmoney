package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class MonetizationModel(val label: String, val shortTag: String, val howItEarns: String) {
    X402_API(
        label = "x402 Micro-API",
        shortTag = "x402",
        howItEarns = "Exposes HTTP 402 endpoints so other AI agents pay USDC per call on Base."
    ),
    TASK_BOUNTY(
        label = "Task & Bounty Hunter",
        shortTag = "Bounty",
        howItEarns = "Claims developer/data bounties, executes tests, and collects USDC rewards."
    ),
    SKILL_SERVICE(
        label = "Skill Registry Tool",
        shortTag = "Skill",
        howItEarns = "Publishes reusable skills/tooling to Conway registries for recurring fees."
    ),
    DATA_SCRAPER(
        label = "Verified Data Feed",
        shortTag = "Feed",
        howItEarns = "Aggregates structured market/price feeds sold via M2M micropayments."
    );

    companion object {
        fun fromRaw(raw: String): MonetizationModel =
            entries.firstOrNull { it.name == raw } ?: X402_API
    }
}

enum class SurvivalTier(val label: String) {
    THRIVING("Thriving"),
    NORMAL("Normal"),
    LOW_COMPUTE("Low Compute"),
    CRITICAL("Critical");

    companion object {
        fun fromRaw(raw: String): SurvivalTier =
            entries.firstOrNull { it.name == raw } ?: NORMAL
    }
}

enum class RevenueChannel(val label: String) {
    X402_MICROPAYMENT("x402 Micropayment"),
    BOUNTY_PAYOUT("Bounty Payout"),
    SKILL_LICENSE("Skill License"),
    COMPUTE_BURN("Compute & LLM Burn");

    companion object {
        fun fromRaw(raw: String): RevenueChannel =
            entries.firstOrNull { it.name == raw } ?: X402_MICROPAYMENT
    }
}

enum class RevenueEntryType(val label: String) {
    EARNING("Earning"),
    BURN("Compute Burn");

    companion object {
        fun fromRaw(raw: String): RevenueEntryType =
            entries.firstOrNull { it.name == raw } ?: EARNING
    }
}

enum class WithdrawalDestinationType(val label: String, val hint: String) {
    UPI("UPI Instant", "e.g. yourname@okaxis"),
    BANK_IMPS("Bank IMPS", "e.g. HDFC •••• 4821 (IFSC)"),
    BASE_USDC("Base USDC", "e.g. 0x742d...f44e");

    companion object {
        fun fromRaw(raw: String): WithdrawalDestinationType =
            entries.firstOrNull { it.name == raw } ?: UPI
    }
}

enum class WithdrawalStatus(val label: String) {
    REQUESTED("Requested"),
    PROCESSING("Processing"),
    SETTLED("Settled"),
    CANCELLED("Cancelled");

    companion object {
        fun fromRaw(raw: String): WithdrawalStatus =
            entries.firstOrNull { it.name == raw } ?: REQUESTED
    }
}

@Keep
@IgnoreExtraProperties
data class AutomatonNode(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val walletAddress: String = "",
    val monetizationModel: String = MonetizationModel.X402_API.name,
    val genesisPrompt: String = "",
    val survivalTier: String = SurvivalTier.NORMAL.name,
    val earnedUsdc: Double = 0.0,
    val computeBurnUsdc: Double = 0.0,
    val endpointUrl: String = "",
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val netUsdc: Double get() = (earnedUsdc - computeBurnUsdc).coerceAtLeast(0.0)
    val modelEnum: MonetizationModel get() = MonetizationModel.fromRaw(monetizationModel)
    val tierEnum: SurvivalTier get() = SurvivalTier.fromRaw(survivalTier)
}

@Keep
@IgnoreExtraProperties
data class RevenueEvent(
    @DocumentId val id: String = "",
    val userId: String = "",
    val automatonId: String = "",
    val automatonName: String = "",
    val sourceChannel: String = RevenueChannel.X402_MICROPAYMENT.name,
    val entryType: String = RevenueEntryType.EARNING.name,
    val amountUsdc: Double = 0.0,
    val usdToInrRate: Double = 84.0,
    val amountInr: Double = 0.0,
    val txReference: String = "",
    val note: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val channelEnum: RevenueChannel get() = RevenueChannel.fromRaw(sourceChannel)
    val typeEnum: RevenueEntryType get() = RevenueEntryType.fromRaw(entryType)
}

@Keep
@IgnoreExtraProperties
data class WithdrawalRequest(
    @DocumentId val id: String = "",
    val userId: String = "",
    val destinationType: String = WithdrawalDestinationType.UPI.name,
    val destinationLabel: String = "",
    val beneficiaryName: String = "",
    val amountInr: Double = 0.0,
    val amountUsdcEquivalent: Double = 0.0,
    val usdToInrRate: Double = 84.0,
    val status: String = WithdrawalStatus.REQUESTED.name,
    val referenceCode: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val destinationEnum: WithdrawalDestinationType get() = WithdrawalDestinationType.fromRaw(destinationType)
    val statusEnum: WithdrawalStatus get() = WithdrawalStatus.fromRaw(status)
}

data class GitHubRepoInfo(
    val fullName: String,
    val description: String,
    val stars: Int,
    val forks: Int,
    val openIssues: Int,
    val language: String,
    val defaultBranch: String,
    val pushedAt: String,
    val htmlUrl: String,
    val readmeSnippet: String
)

data class UsdInrRateInfo(
    val rate: Double,
    val source: String,
    val dateLabel: String
)

data class OnChainWalletBalance(
    val address: String,
    val usdcBalance: Double,
    val inrEquivalent: Double
)
