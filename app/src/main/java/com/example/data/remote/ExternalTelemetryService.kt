package com.example.data.remote

import android.util.Base64
import com.example.data.model.GitHubRepoInfo
import com.example.data.model.OnChainWalletBalance
import com.example.data.model.UsdInrRateInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.util.concurrent.TimeUnit

class ExternalTelemetryService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun fetchConwayRepoInfo(): Result<GitHubRepoInfo> = withContext(Dispatchers.IO) {
        try {
            val repoReq = Request.Builder()
                .url("https://api.github.com/repos/Conway-Research/automaton")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "AutomatonDesk-Android")
                .build()

            val repoBody = client.newCall(repoReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("GitHub API HTTP ${resp.code}"))
                }
                resp.body?.string().orEmpty()
            }
            val json = JSONObject(repoBody)

            var readmeText = ""
            try {
                val readmeReq = Request.Builder()
                    .url("https://api.github.com/repos/Conway-Research/automaton/readme")
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "AutomatonDesk-Android")
                    .build()
                client.newCall(readmeReq).execute().use { rResp ->
                    if (rResp.isSuccessful) {
                        val rJson = JSONObject(rResp.body?.string().orEmpty())
                        val encoded = rJson.optString("content", "").replace("\n", "")
                        if (encoded.isNotBlank()) {
                            val decoded = String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
                            readmeText = decoded.lineSequence().take(28).joinToString("\n")
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // README snippet is optional if rate-limited
            }

            Result.success(
                GitHubRepoInfo(
                    fullName = json.optString("full_name", "Conway-Research/automaton"),
                    description = json.optString(
                        "description",
                        "Sovereign autonomous AI agent framework with Base wallet & x402 survival loop."
                    ),
                    stars = json.optInt("stargazers_count", 0),
                    forks = json.optInt("forks_count", 0),
                    openIssues = json.optInt("open_issues_count", 0),
                    language = json.optString("language", "TypeScript"),
                    defaultBranch = json.optString("default_branch", "main"),
                    pushedAt = json.optString("pushed_at", "").take(10),
                    htmlUrl = json.optString("html_url", "https://github.com/Conway-Research/automaton"),
                    readmeSnippet = readmeText
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchLiveUsdToInrRate(): Result<UsdInrRateInfo> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://api.frankfurter.app/latest?from=USD&to=INR")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body?.string().orEmpty())
                    val rates = json.optJSONObject("rates")
                    val inr = rates?.optDouble("INR", 0.0) ?: 0.0
                    if (inr > 10.0) {
                        return@withContext Result.success(
                            UsdInrRateInfo(
                                rate = inr,
                                source = "Frankfurter ECB FX",
                                dateLabel = json.optString("date", "Live")
                            )
                        )
                    }
                }
            }

            // Fallback live FX endpoint
            val fallbackReq = Request.Builder()
                .url("https://open.er-api.com/v6/latest/USD")
                .build()
            client.newCall(fallbackReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("FX API HTTP ${resp.code}"))
                }
                val json = JSONObject(resp.body?.string().orEmpty())
                val inr = json.optJSONObject("rates")?.optDouble("INR", 0.0) ?: 0.0
                if (inr <= 10.0) {
                    return@withContext Result.failure(IllegalStateException("Invalid INR rate returned"))
                }
                Result.success(
                    UsdInrRateInfo(
                        rate = inr,
                        source = "Open Exchange Rates",
                        dateLabel = "Live"
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchBaseMainnetUsdcBalance(
        walletAddress: String,
        usdToInrRate: Double
    ): Result<OnChainWalletBalance> = withContext(Dispatchers.IO) {
        try {
            val cleaned = walletAddress.trim().lowercase()
            if (!cleaned.matches(Regex("^0x[0-9a-f]{40}$"))) {
                return@withContext Result.failure(
                    IllegalArgumentException("Enter a valid 42-character Base/EVM 0x address.")
                )
            }
            // ERC-20 balanceOf(address) selector: 0x70a08231 + 24 zero hex chars + 40 address hex chars
            val paddedCallData = "0x70a08231000000000000000000000000" + cleaned.removePrefix("0x")
            val callObj = JSONObject().apply {
                put("to", BASE_USDC_CONTRACT)
                put("data", paddedCallData)
            }
            val params = JSONArray().apply {
                put(callObj)
                put("latest")
            }
            val rpcBody = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "eth_call")
                put("params", params)
            }.toString()

            val request = Request.Builder()
                .url(BASE_MAINNET_RPC)
                .post(rpcBody.toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext Result.failure(IllegalStateException("Base RPC HTTP ${resp.code}"))
                }
                val json = JSONObject(resp.body?.string().orEmpty())
                if (json.has("error")) {
                    val errMsg = json.getJSONObject("error").optString("message", "Base RPC error")
                    return@withContext Result.failure(IllegalStateException(errMsg))
                }
                val hexResult = json.optString("result", "0x0").removePrefix("0x").ifBlank { "0" }
                val rawUnits = BigInteger(hexResult, 16)
                // Base USDC has 6 decimals
                val usdcBalance = rawUnits.toDouble() / 1_000_000.0
                val inrVal = usdcBalance * usdToInrRate
                Result.success(
                    OnChainWalletBalance(
                        address = walletAddress.trim(),
                        usdcBalance = usdcBalance,
                        inrEquivalent = inrVal
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private companion object {
        const val BASE_MAINNET_RPC = "https://mainnet.base.org"
        const val BASE_USDC_CONTRACT = "0x833589fCD6eDb6E08f4c7C32D4f71b54bdA02913"
    }
}
