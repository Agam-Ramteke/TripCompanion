package com.tripcompanion.app.data.network.railradar

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One HTTP door to RailRadar, shared by the live-status provider and the PNR lookup service.
 *
 * The train-side twin of [com.tripcompanion.app.data.network.NominatimLocationSearchProvider]:
 * HTTP and this one vendor's address shape live here, the JSON lives in [RailRadarParser], and
 * everything above the data layer is told only about trains and tickets. Two consumers means the
 * transport is written once here rather than twice.
 *
 * Two things about this API shape the code, both simpler than the vendor this replaced:
 *
 * **The key travels in a header, not the URL.** `X-API-Key: <key>`. So an error message may name
 * the endpoint path freely — it carries no secret — which is why failures here are more legible
 * than the old provider's, whose path *was* the key.
 *
 * **It is TLS-only.** RailRadar serves `https://` exclusively, so there is no cleartext fallback
 * and no host exception in `network_security_config.xml`. The whole app stays TLS-only.
 *
 * Failures are reported as [RailRadarException] with a [RailRadarErrorKind]; turning that into a
 * user-facing error is each consumer's job, because the two consumers answer to different screens.
 */
@Singleton
class RailRadarClient @Inject constructor(
    @RailRadarApiKey private val apiKey: String
) {

    /** False when no key is configured, which is the shipping default. */
    val isConfigured: Boolean get() = apiKey.isNotBlank()

    /**
     * One GET against [path], which is appended to the versioned base — e.g. `/trains/12345/live`.
     *
     * Returns the raw body; parsing is [RailRadarParser]'s job. Throws [RailRadarException] for
     * every classified failure, and rethrows [CancellationException] untouched so structured
     * concurrency stays intact when a screen moves on mid-request.
     */
    suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        request("$BASE$path", path)
    }

    private fun request(url: String, endpoint: String): String {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                // Header auth: the key never reaches the URL, a log, or an exception message.
                setRequestProperty("X-API-Key", apiKey)
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
            }

            when (val code = connection.responseCode) {
                in 200..299 -> Unit
                HTTP_TOO_MANY_REQUESTS -> throw RailRadarException(
                    RailRadarErrorKind.RATE_LIMITED,
                    "Request limit reached"
                )
                HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN ->
                    throw RailRadarException(
                        RailRadarErrorKind.KEY_REJECTED,
                        "The API key was rejected"
                    )
                HttpURLConnection.HTTP_NOT_FOUND -> throw RailRadarException(
                    RailRadarErrorKind.NOT_FOUND,
                    "$endpoint has no record"
                )
                else -> throw RailRadarException(
                    RailRadarErrorKind.PROVIDER_ERROR,
                    "$endpoint returned HTTP $code"
                )
            }

            return BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
        } catch (e: RailRadarException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw RailRadarException(RailRadarErrorKind.TIMEOUT, "$endpoint timed out", e)
        } catch (e: UnknownHostException) {
            throw RailRadarException(RailRadarErrorKind.NETWORK, cause = e)
        } catch (e: IOException) {
            throw RailRadarException(RailRadarErrorKind.NETWORK, cause = e)
        } catch (e: Exception) {
            throw RailRadarException(RailRadarErrorKind.UNKNOWN, cause = e)
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        private const val BASE = "https://api.railradar.in/v1"
        private const val USER_AGENT = "TripCompanion/1.0 (Android)"

        /** Longer than the search timeouts: this endpoint is slower and less pressing. */
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 8_000
        private const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
