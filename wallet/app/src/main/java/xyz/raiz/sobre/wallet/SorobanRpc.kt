package xyz.raiz.sobre.wallet

import android.util.Log
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Minimal Soroban JSON-RPC client (sendTransaction / getTransaction) plus
 * friendbot funding, over HttpURLConnection — no SDK dependency; submission
 * stays in Kotlin per the architecture decision. Call from Dispatchers.IO.
 *
 * Response-shape note (project gotcha #5: shapes vary between RPC versions):
 * we read only fields verified against the REAL testnet RPC during Session 5
 * (2026-08-03): sendTransaction -> result.{status, hash, errorResultXdr?};
 * getTransaction -> result.{status, ledger?, resultXdr?}. Reads are tolerant
 * (opt*) and the raw JSON is logged on anything unexpected.
 *
 * Every call retries with exponential backoff (project gotcha #3: testnet is
 * flaky in bursts).
 */
class SorobanRpc(
    private val rpcUrl: String = CtConfig.RPC_URL,
    private val friendbotUrl: String = CtConfig.FRIENDBOT_URL,
) {

    data class SendResult(val status: String, val hash: String, val errorResultXdr: String?)
    data class TxResult(val status: String, val ledger: Long?, val resultXdr: String?, val raw: String)

    class RpcException(message: String) : IOException(message)

    /** POST sendTransaction. PENDING/DUPLICATE = accepted; anything else throws. */
    fun sendTransaction(signedXdrBase64: String): SendResult {
        val res = rpcCall("sendTransaction", JSONObject().put("transaction", signedXdrBase64))
        val status = res.optString("status")
        val send = SendResult(status, res.optString("hash"), res.optString("errorResultXdr").ifEmpty { null })
        if (status != "PENDING" && status != "DUPLICATE") {
            // errorResultXdr is friction-report material — keep it verbatim.
            throw RpcException("sendTransaction rejected: status=$status errorResultXdr=${send.errorResultXdr} raw=$res")
        }
        return send
    }

    fun getTransaction(hashHex: String): TxResult {
        val res = rpcCall("getTransaction", JSONObject().put("hash", hashHex))
        val ledger = res.optLong("ledger", -1L)
        return TxResult(
            status = res.optString("status"),
            ledger = if (ledger >= 0) ledger else null,
            resultXdr = res.optString("resultXdr").ifEmpty { null },
            raw = res.toString(),
        )
    }

    /** Poll until SUCCESS (returned) or FAILED (throws, with the raw JSON). */
    fun pollUntilComplete(
        hashHex: String,
        timeoutMs: Long = 120_000,
        intervalMs: Long = 2_000,
    ): TxResult {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(intervalMs)
            val res = getTransaction(hashHex)
            when (res.status) {
                "SUCCESS" -> return res
                "FAILED" -> throw RpcException("tx $hashHex FAILED on-chain: ${res.raw}")
                "NOT_FOUND" -> Unit // keep polling
                else -> Log.w(TAG, "getTransaction unexpected status '${res.status}': ${res.raw}")
            }
        }
        throw RpcException("tx $hashHex not confirmed within $timeoutMs ms")
    }

    /** Fund via friendbot; 400 = already funded (fine). Idempotent. */
    fun friendbotFund(accountId: String) {
        retrying("friendbot") {
            val url = URL("$friendbotUrl/?addr=${URLEncoder.encode(accountId, "UTF-8")}")
            val conn = url.openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 15_000
                conn.readTimeout = 60_000
                val code = conn.responseCode
                if (code != 200 && code != 400) throw IOException("friendbot HTTP $code")
                Log.i(TAG, "friendbot(${accountId.take(5)}…) -> HTTP $code")
            } finally {
                conn.disconnect()
            }
        }
    }

    // -----------------------------------------------------------------------

    private fun rpcCall(method: String, params: JSONObject): JSONObject =
        retrying(method) {
            val body = JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", 1)
                .put("method", method)
                .put("params", params)
                .toString()
            val conn = URL(rpcUrl).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 15_000
                conn.readTimeout = 60_000
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
                if (code !in 200..299) throw IOException("$method HTTP $code: ${text.take(400)}")
                val json = JSONObject(text)
                json.optJSONObject("error")?.let {
                    throw RpcException("$method JSON-RPC error: $it")
                }
                json.optJSONObject("result")
                    ?: throw RpcException("$method: no result in response: ${text.take(400)}")
            } finally {
                conn.disconnect()
            }
        }

    /** Backoff for transport-level failures; RpcExceptions carrying an on-chain
     *  verdict (send rejected, tx FAILED) propagate immediately. */
    private fun <T> retrying(label: String, tries: Int = 4, baseDelayMs: Long = 2_000, block: () -> T): T {
        var last: Exception? = null
        for (i in 1..tries) {
            try {
                return block()
            } catch (e: RpcException) {
                throw e
            } catch (e: Exception) {
                last = e
                if (i < tries) {
                    val delay = baseDelayMs shl (i - 1)
                    Log.w(TAG, "$label attempt $i/$tries failed (${e.message}); retrying in ${delay}ms")
                    Thread.sleep(delay)
                }
            }
        }
        throw IOException("$label failed after $tries attempts: ${last?.message}", last)
    }

    private companion object {
        const val TAG = "SobreSpike"
    }
}
