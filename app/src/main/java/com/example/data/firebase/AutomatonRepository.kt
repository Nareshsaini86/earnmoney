package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.AutomatonNode
import com.example.data.model.MonetizationModel
import com.example.data.model.RevenueChannel
import com.example.data.model.RevenueEntryType
import com.example.data.model.RevenueEvent
import com.example.data.model.SurvivalTier
import com.example.data.model.WithdrawalDestinationType
import com.example.data.model.WithdrawalRequest
import com.example.data.model.WithdrawalStatus
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlin.math.roundToInt

class AutomatonRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeAutomatons(userId: String): Flow<List<AutomatonNode>> = flow {
        require(userId.isNotBlank()) { "Authenticated userId is required." }
        val path = "users/$userId/automatons"
        emitAll(
            db.collection("users").document(userId).collection("automatons")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        AutomatonNode::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeRevenueEvents(userId: String): Flow<List<RevenueEvent>> = flow {
        require(userId.isNotBlank()) { "Authenticated userId is required." }
        val path = "users/$userId/revenueEvents"
        emitAll(
            db.collection("users").document(userId).collection("revenueEvents")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        RevenueEvent::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeWithdrawals(userId: String): Flow<List<WithdrawalRequest>> = flow {
        require(userId.isNotBlank()) { "Authenticated userId is required." }
        val path = "users/$userId/withdrawals"
        emitAll(
            db.collection("users").document(userId).collection("withdrawals")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        WithdrawalRequest::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun createAutomaton(
        name: String,
        walletAddress: String,
        monetizationModel: MonetizationModel,
        genesisPrompt: String,
        endpointUrl: String
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/automatons"
        return try {
            val uid = requireUserId()
            val cleanName = name.trim()
            val cleanPrompt = genesisPrompt.trim()
            val cleanWallet = walletAddress.trim()
            val cleanEndpoint = endpointUrl.trim()
            require(cleanName.length in 2..60) { "Node name must be 2 to 60 characters." }
            require(cleanPrompt.length in 10..4000) { "Genesis prompt must be 10 to 4000 characters." }
            require(cleanWallet.length <= 64) { "Wallet address cannot exceed 64 characters." }
            require(cleanEndpoint.length <= 250) { "Endpoint URL cannot exceed 250 characters." }

            val docRef = db.collection("users").document(uid).collection("automatons").document()
            docPath = docRef.path

            val payload = mapOf(
                "userId" to uid,
                "name" to cleanName,
                "walletAddress" to cleanWallet.ifBlank { null },
                "monetizationModel" to monetizationModel.name,
                "genesisPrompt" to cleanPrompt,
                "survivalTier" to SurvivalTier.NORMAL.name,
                "earnedUsdc" to 0.0,
                "computeBurnUsdc" to 0.0,
                "endpointUrl" to cleanEndpoint.ifBlank { null },
                "active" to true,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun syncOnChainBalance(
        automatonId: String,
        onChainUsdc: Double,
        currentBurnUsdc: Double
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/automatons/$automatonId"
        return try {
            val uid = requireUserId()
            require(automatonId.isNotBlank()) { "Automaton ID required." }
            require(onChainUsdc >= 0.0) { "Balance must be non-negative." }

            val net = onChainUsdc - currentBurnUsdc
            val tier = when {
                net >= 50.0 -> SurvivalTier.THRIVING
                net >= 10.0 -> SurvivalTier.NORMAL
                net >= 2.0 -> SurvivalTier.LOW_COMPUTE
                else -> SurvivalTier.CRITICAL
            }

            val docRef = db.collection("users").document(uid).collection("automatons").document(automatonId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "earnedUsdc" to onChainUsdc,
                    "survivalTier" to tier.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteAutomaton(automatonId: String): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/automatons/$automatonId"
        return try {
            val uid = requireUserId()
            require(automatonId.isNotBlank()) { "Automaton ID required." }
            val docRef = db.collection("users").document(uid).collection("automatons").document(automatonId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun recordRevenueEvent(
        automaton: AutomatonNode,
        channel: RevenueChannel,
        entryType: RevenueEntryType,
        amountUsdc: Double,
        usdToInrRate: Double,
        txReference: String,
        note: String
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/revenueEvents"
        return try {
            val uid = requireUserId()
            require(automaton.id.isNotBlank()) { "Select a registered Automaton node first." }
            require(amountUsdc > 0.0 && amountUsdc <= 1_000_000.0) { "Amount must be between 0 and 1,000,000 USDC." }
            require(usdToInrRate > 0.0 && usdToInrRate <= 1_000.0) { "Invalid USD/INR FX rate." }

            val amountInr = ((amountUsdc * usdToInrRate) * 100.0).roundToInt() / 100.0
            require(amountInr > 0.0) { "INR equivalent must be greater than zero." }

            val cleanTx = txReference.trim().take(128)
            val cleanNote = note.trim().take(240)
            val cleanNodeName = automaton.name.trim().take(60).ifEmpty { "Automaton Node" }

            val eventRef = db.collection("users").document(uid).collection("revenueEvents").document()
            docPath = eventRef.path
            val nodeRef = db.collection("users").document(uid).collection("automatons").document(automaton.id)

            val eventPayload = mapOf(
                "userId" to uid,
                "automatonId" to automaton.id,
                "automatonName" to cleanNodeName,
                "sourceChannel" to channel.name,
                "entryType" to entryType.name,
                "amountUsdc" to amountUsdc,
                "usdToInrRate" to usdToInrRate,
                "amountInr" to amountInr,
                "txReference" to cleanTx.ifBlank { null },
                "note" to cleanNote.ifBlank { null },
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            ).filterValues { it != null }

            val updatedEarned = if (entryType == RevenueEntryType.EARNING) {
                automaton.earnedUsdc + amountUsdc
            } else {
                automaton.earnedUsdc
            }
            val updatedBurn = if (entryType == RevenueEntryType.BURN) {
                automaton.computeBurnUsdc + amountUsdc
            } else {
                automaton.computeBurnUsdc
            }
            val net = updatedEarned - updatedBurn
            val newTier = when {
                net >= 50.0 -> SurvivalTier.THRIVING
                net >= 10.0 -> SurvivalTier.NORMAL
                net >= 2.0 -> SurvivalTier.LOW_COMPUTE
                else -> SurvivalTier.CRITICAL
            }

            val batch = db.batch()
            batch.set(eventRef, eventPayload)
            batch.update(
                nodeRef,
                mapOf(
                    "earnedUsdc" to updatedEarned,
                    "computeBurnUsdc" to updatedBurn,
                    "survivalTier" to newTier.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun createWithdrawalRequest(
        destinationType: WithdrawalDestinationType,
        destinationLabel: String,
        beneficiaryName: String,
        amountInr: Double,
        usdToInrRate: Double
    ): Result<String> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/withdrawals"
        return try {
            val uid = requireUserId()
            val cleanDest = destinationLabel.trim()
            val cleanName = beneficiaryName.trim()
            require(cleanDest.length in 3..120) { "Destination details must be 3 to 120 characters." }
            require(cleanName.length in 2..80) { "Beneficiary name must be 2 to 80 characters." }
            require(amountInr in 100.0..10_000_000.0) { "Minimum withdrawal is ₹100." }
            require(usdToInrRate > 0.0) { "FX rate unavailable." }

            val usdcEq = ((amountInr / usdToInrRate) * 10000.0).roundToInt() / 10000.0
            val refCode = "WD-${(100000..999999).random()}"

            val docRef = db.collection("users").document(uid).collection("withdrawals").document()
            docPath = docRef.path

            val payload = mapOf(
                "userId" to uid,
                "destinationType" to destinationType.name,
                "destinationLabel" to cleanDest,
                "beneficiaryName" to cleanName,
                "amountInr" to amountInr,
                "amountUsdcEquivalent" to usdcEq.coerceAtLeast(0.01),
                "usdToInrRate" to usdToInrRate,
                "status" to WithdrawalStatus.REQUESTED.name,
                "referenceCode" to refCode,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(refCode)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateWithdrawalStatus(
        withdrawalId: String,
        newStatus: WithdrawalStatus
    ): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/withdrawals/$withdrawalId"
        return try {
            val uid = requireUserId()
            require(withdrawalId.isNotBlank()) { "Withdrawal ID required." }
            val docRef = db.collection("users").document(uid).collection("withdrawals").document(withdrawalId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "status" to newStatus.name,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }
}
