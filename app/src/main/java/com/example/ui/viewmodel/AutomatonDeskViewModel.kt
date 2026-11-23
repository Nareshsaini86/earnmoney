package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.GenesisAiRepository
import com.example.data.firebase.AutomatonRepository
import com.example.data.model.AutomatonNode
import com.example.data.model.GitHubRepoInfo
import com.example.data.model.MonetizationModel
import com.example.data.model.OnChainWalletBalance
import com.example.data.model.RevenueChannel
import com.example.data.model.RevenueEntryType
import com.example.data.model.RevenueEvent
import com.example.data.model.UiState
import com.example.data.model.UsdInrRateInfo
import com.example.data.model.WithdrawalDestinationType
import com.example.data.model.WithdrawalRequest
import com.example.data.model.WithdrawalStatus
import com.example.data.remote.ExternalTelemetryService
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

sealed interface AiStreamState {
    data object Idle : AiStreamState
    data object Loading : AiStreamState
    data class Streaming(val text: String) : AiStreamState
    data class Success(val text: String) : AiStreamState
    data class Error(val message: String) : AiStreamState
}

data class RevenueSummary(
    val totalEarnedUsdc: Double = 0.0,
    val totalBurnUsdc: Double = 0.0,
    val netUsdc: Double = 0.0,
    val totalEarnedInr: Double = 0.0,
    val totalBurnInr: Double = 0.0,
    val netRevenueInr: Double = 0.0,
    val committedOrSettledInr: Double = 0.0,
    val settledInr: Double = 0.0,
    val withdrawableInr: Double = 0.0
)

class AutomatonDeskViewModel(
    private val repository: AutomatonRepository,
    private val telemetryService: ExternalTelemetryService,
    private val aiRepository: GenesisAiRepository,
    private val currentUserId: String
) : ViewModel() {

    val automatonsState: StateFlow<UiState<List<AutomatonNode>>> =
        repository.observeAutomatons(currentUserId)
            .map<List<AutomatonNode>, UiState<List<AutomatonNode>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing automatons", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load Automaton nodes"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    val revenueEventsState: StateFlow<UiState<List<RevenueEvent>>> =
        repository.observeRevenueEvents(currentUserId)
            .map<List<RevenueEvent>, UiState<List<RevenueEvent>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing revenue events", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load revenue ledger"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    val withdrawalsState: StateFlow<UiState<List<WithdrawalRequest>>> =
        repository.observeWithdrawals(currentUserId)
            .map<List<WithdrawalRequest>, UiState<List<WithdrawalRequest>>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing withdrawals", rootError)
                emit(UiState.Error(rootError.message ?: "Failed to load payouts"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = UiState.Loading
            )

    private val _repoState = MutableStateFlow<UiState<GitHubRepoInfo>>(UiState.Loading)
    val repoState: StateFlow<UiState<GitHubRepoInfo>> = _repoState.asStateFlow()

    private val _fxRateState = MutableStateFlow<UiState<UsdInrRateInfo>>(UiState.Loading)
    val fxRateState: StateFlow<UiState<UsdInrRateInfo>> = _fxRateState.asStateFlow()

    private val _onChainCheckState = MutableStateFlow<UiState<OnChainWalletBalance>?>(null)
    val onChainCheckState: StateFlow<UiState<OnChainWalletBalance>?> = _onChainCheckState.asStateFlow()

    private val _aiStreamState = MutableStateFlow<AiStreamState>(AiStreamState.Idle)
    val aiStreamState: StateFlow<AiStreamState> = _aiStreamState.asStateFlow()

    private val _extractedPrompt = MutableStateFlow("")
    val extractedPrompt: StateFlow<String> = _extractedPrompt.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private var aiJob: Job? = null

    val revenueSummary: StateFlow<RevenueSummary> = combine(
        automatonsState,
        revenueEventsState,
        withdrawalsState,
        fxRateState
    ) { nodesState, _, wdState, fxState ->
        val nodes = (nodesState as? UiState.Success)?.data.orEmpty()
        val withdrawals = (wdState as? UiState.Success)?.data.orEmpty()
        val rate = (fxState as? UiState.Success)?.data?.rate ?: DEFAULT_FALLBACK_RATE

        val totalEarnedUsdc = nodes.sumOf { it.earnedUsdc }
        val totalBurnUsdc = nodes.sumOf { it.computeBurnUsdc }
        val netUsdc = (totalEarnedUsdc - totalBurnUsdc).coerceAtLeast(0.0)

        val totalEarnedInr = totalEarnedUsdc * rate
        val totalBurnInr = totalBurnUsdc * rate
        val netInr = netUsdc * rate

        val committedInr = withdrawals
            .filter { it.statusEnum != WithdrawalStatus.CANCELLED }
            .sumOf { it.amountInr }
        val settledInr = withdrawals
            .filter { it.statusEnum == WithdrawalStatus.SETTLED }
            .sumOf { it.amountInr }
        val withdrawable = (netInr - committedInr).coerceAtLeast(0.0)

        RevenueSummary(
            totalEarnedUsdc = totalEarnedUsdc,
            totalBurnUsdc = totalBurnUsdc,
            netUsdc = netUsdc,
            totalEarnedInr = totalEarnedInr,
            totalBurnInr = totalBurnInr,
            netRevenueInr = netInr,
            committedOrSettledInr = committedInr,
            settledInr = settledInr,
            withdrawableInr = withdrawable
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = RevenueSummary()
    )

    init {
        refreshExternalTelemetry()
    }

    fun refreshExternalTelemetry() {
        viewModelScope.launch {
            _repoState.value = UiState.Loading
            telemetryService.fetchConwayRepoInfo()
                .onSuccess { _repoState.value = UiState.Success(it) }
                .onFailure { err ->
                    _repoState.value = UiState.Error(err.localizedMessage ?: "Failed to fetch GitHub repo")
                }
        }
        viewModelScope.launch {
            _fxRateState.value = UiState.Loading
            telemetryService.fetchLiveUsdToInrRate()
                .onSuccess { _fxRateState.value = UiState.Success(it) }
                .onFailure { err ->
                    _fxRateState.value = UiState.Error(err.localizedMessage ?: "Failed to fetch live USD/INR FX rate")
                }
        }
    }

    fun currentFxRateOrNull(): Double? = (_fxRateState.value as? UiState.Success)?.data?.rate

    fun checkBaseWalletBalance(walletAddress: String, automatonToSync: AutomatonNode? = null) {
        val rate = currentFxRateOrNull() ?: DEFAULT_FALLBACK_RATE
        viewModelScope.launch {
            _onChainCheckState.value = UiState.Loading
            telemetryService.fetchBaseMainnetUsdcBalance(walletAddress, rate)
                .onSuccess { bal ->
                    _onChainCheckState.value = UiState.Success(bal)
                    if (automatonToSync != null && automatonToSync.id.isNotBlank()) {
                        repository.syncOnChainBalance(
                            automatonId = automatonToSync.id,
                            onChainUsdc = bal.usdcBalance,
                            currentBurnUsdc = automatonToSync.computeBurnUsdc
                        ).onSuccess {
                            _actionMessage.value =
                                "Synced Base USDC (${String.format("%.4f", bal.usdcBalance)} USDC ≈ ₹${String.format("%.2f", bal.inrEquivalent)})"
                        }.onFailure { err ->
                            _actionMessage.value = err.localizedMessage ?: "Failed to sync balance"
                        }
                    }
                }
                .onFailure { err ->
                    _onChainCheckState.value = UiState.Error(err.localizedMessage ?: "Base RPC lookup failed")
                }
        }
    }

    fun clearOnChainCheck() {
        _onChainCheckState.value = null
    }

    fun createAutomaton(
        name: String,
        walletAddress: String,
        monetizationModel: MonetizationModel,
        genesisPrompt: String,
        endpointUrl: String
    ) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.createAutomaton(
                name = name,
                walletAddress = walletAddress,
                monetizationModel = monetizationModel,
                genesisPrompt = genesisPrompt,
                endpointUrl = endpointUrl
            ).onSuccess {
                _actionMessage.value = "Automaton node registered to your dashboard."
            }.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "Failed to register Automaton node."
            }
            _isBusy.value = false
        }
    }

    fun deleteAutomaton(automatonId: String) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.deleteAutomaton(automatonId)
                .onSuccess { _actionMessage.value = "Automaton node removed." }
                .onFailure { err -> _actionMessage.value = err.localizedMessage ?: "Delete failed." }
            _isBusy.value = false
        }
    }

    fun recordRevenueEvent(
        automaton: AutomatonNode,
        channel: RevenueChannel,
        entryType: RevenueEntryType,
        amountUsdc: Double,
        txReference: String,
        note: String
    ) {
        val rate = currentFxRateOrNull()
        if (rate == null) {
            _actionMessage.value = "Waiting for live USD/INR FX rate. Tap Refresh first."
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            repository.recordRevenueEvent(
                automaton = automaton,
                channel = channel,
                entryType = entryType,
                amountUsdc = amountUsdc,
                usdToInrRate = rate,
                txReference = txReference,
                note = note
            ).onSuccess {
                val verb = if (entryType == RevenueEntryType.EARNING) "Revenue logged" else "Compute burn logged"
                _actionMessage.value = "$verb: $${String.format("%.2f", amountUsdc)} USDC (₹${String.format("%.2f", amountUsdc * rate)})"
            }.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "Failed to log revenue event."
            }
            _isBusy.value = false
        }
    }

    fun requestWithdrawal(
        destinationType: WithdrawalDestinationType,
        destinationLabel: String,
        beneficiaryName: String,
        amountInr: Double
    ) {
        val available = revenueSummary.value.withdrawableInr
        if (amountInr > available + 0.01) {
            _actionMessage.value = "Amount exceeds withdrawable balance (₹${String.format("%.2f", available)})."
            return
        }
        val rate = currentFxRateOrNull() ?: DEFAULT_FALLBACK_RATE
        viewModelScope.launch {
            _isBusy.value = true
            repository.createWithdrawalRequest(
                destinationType = destinationType,
                destinationLabel = destinationLabel,
                beneficiaryName = beneficiaryName,
                amountInr = amountInr,
                usdToInrRate = rate
            ).onSuccess { refCode ->
                _actionMessage.value = "Withdrawal request $refCode created for ₹${String.format("%.2f", amountInr)}."
            }.onFailure { err ->
                _actionMessage.value = err.localizedMessage ?: "Withdrawal request failed."
            }
            _isBusy.value = false
        }
    }

    fun updateWithdrawalStatus(withdrawalId: String, newStatus: WithdrawalStatus) {
        viewModelScope.launch {
            _isBusy.value = true
            repository.updateWithdrawalStatus(withdrawalId, newStatus)
                .onSuccess {
                    _actionMessage.value = "Payout marked as ${newStatus.label}."
                }
                .onFailure { err ->
                    _actionMessage.value = err.localizedMessage ?: "Status update failed."
                }
            _isBusy.value = false
        }
    }

    fun generateAiGenesisStrategy(
        niche: MonetizationModel,
        budgetUsdc: Double,
        customIdea: String
    ) {
        val rate = currentFxRateOrNull() ?: DEFAULT_FALLBACK_RATE
        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            _aiStreamState.value = AiStreamState.Loading
            val accumulated = StringBuilder()
            try {
                withTimeout(60_000L) {
                    aiRepository.streamGenesisStrategy(
                        niche = niche,
                        budgetUsdc = budgetUsdc,
                        usdToInrRate = rate,
                        customIdea = customIdea
                    ).collect { chunk ->
                        accumulated.append(chunk)
                        _aiStreamState.value = AiStreamState.Streaming(accumulated.toString())
                    }
                }
                val finalText = accumulated.toString().trim()
                if (finalText.isNotBlank()) {
                    _aiStreamState.value = AiStreamState.Success(finalText)
                    _extractedPrompt.value = finalText.take(3800)
                } else {
                    _aiStreamState.value = AiStreamState.Error("Gemini returned an empty response.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI generation timed out", e)
                _aiStreamState.value = AiStreamState.Error("Generation timed out after 60s. Please retry.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Firebase AI generation failed", e)
                _aiStreamState.value = AiStreamState.Error(e.localizedMessage ?: "AI generation failed.")
            }
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    private companion object {
        const val TAG = "AutomatonDeskVM"
        const val STOP_TIMEOUT_MS = 5000L
        const val DEFAULT_FALLBACK_RATE = 84.5
    }
}
