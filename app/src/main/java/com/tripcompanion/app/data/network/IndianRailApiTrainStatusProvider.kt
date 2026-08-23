package com.tripcompanion.app.data.network

import com.tripcompanion.app.core.time.TimeProvider
import com.tripcompanion.app.domain.model.Train
import com.tripcompanion.app.domain.model.TrainRunStatus
import com.tripcompanion.app.domain.model.TrainStop
import com.tripcompanion.app.domain.service.TrainStatusError
import com.tripcompanion.app.domain.service.TrainStatusException
import com.tripcompanion.app.domain.service.TrainStatusProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import java.net.UnknownServiceException
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException

/**
 * Live running status from indianrailapi.com.
 *
 * The train-side twin of [NominatimLocationSearchProvider]: HTTP and one vendor's URL shape
 * live here, the JSON lives in [IndianRailApiParser], and everything above this class is told
 * only about trains. Swapping vendors means writing one more file, not touching a screen.
 *
 * Two things about this API shape the code:
 *
 * **The key travels in the URL path.** Not a header, not a query parameter. So no request URL
 * is ever logged, built into an exception message, or attached to a crash report — an error
 * string containing the URL would leak the key into logcat, which any app on the device can
 * read. Failures name the endpoint, never the address.
 *
 * **It is served over cleartext.** The vendor publishes `http://` endpoints. Every request
 * tries `https://` first and only falls back to `http://` when TLS is actually unavailable,
 * so a working handshake is never thrown away. The cleartext exception in
 * `res/xml/network_security_config.xml` is scoped to this one host; the rest of the app,
 * including Nominatim and the map tiles, stays TLS-only.
 */
@Singleton
class IndianRailApiTrainStatusProvider @Inject constructor(
    @IndianRailApiKey private val apiKey: String,
    private val timeProvider: TimeProvider
) : TrainStatusProvider {

    override val providerName: String = PROVIDER_NAME

    override val isLive: Boolean = true

    override suspend fun fetchStatus(train: Train, schedule: List<TrainStop>): TrainRunStatus {
        val number = train.number.trim()
        requireUsable(number)

        // The date the *run* began, taken from the booking rather than the clock. An overnight
        // train boarded at 23:40 is still yesterday's train at 01:00, and asking about today
        // would return a different service or nothing at all.
        val runDate = train.departureTime.toLocalDate()

        val body = get(
            "livetrainstatus/apikey/$apiKey/trainnumber/${encode(number)}" +
                "/date/${IndianRailApiParser.formatRequestDate(runDate)}/"
        )

        return IndianRailApiParser.parseLiveStatus(
            json = body,
            trainId = train.id,
            fetchedAt = timeProvider.now(),
            requestedDate = runDate
        )
    }

    override suspend fun fetchSchedule(trainNumber: String): List<TrainStop> {
        val number = trainNumber.trim()
        requireUsable(number)

        val body = get("TrainSchedule/apikey/$apiKey/TrainNumber/${encode(number)}/")
        return IndianRailApiParser.parseSchedule(body)
    }

    /**
     * Fails fast on the two inputs that cannot produce a request.
     *
     * A blank key is a build-configuration mistake rather than a network problem, and reporting
     * it as one saves someone reading a stack trace about DNS.
     */
    private fun requireUsable(trainNumber: String) {
        if (apiKey.isBlank()) {
            throw TrainStatusException(
                TrainStatusError.PROVIDER_ERROR,
                "No API key configured for $PROVIDER_NAME"
            )
        }
        if (trainNumber.isEmpty()) {
            throw TrainStatusException(
                TrainStatusError.TRAIN_NOT_FOUND,
                "This train has no number recorded"
            )
        }
    }

    /**
     * One GET, over TLS if the host will take it.
     *
     * [path] carries the API key, so it never reaches a log or an exception message. The
     * `endpoint` label passed to [request] is the path with the key removed, which is what
     * appears in errors.
     */
    private suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        val label = path.substringBefore("/apikey/")
        try {
            request("$HTTPS_BASE/$path", label)
        } catch (_: SSLException) {
            // The vendor does not consistently serve TLS. One retry in cleartext, permitted
            // for this host alone by the network security config.
            request("$HTTP_BASE/$path", label)
        }
    }

    private fun request(url: String, endpoint: String): String {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
            }

            when (val code = connection.responseCode) {
                in 200..299 -> Unit
                HTTP_TOO_MANY_REQUESTS -> throw TrainStatusException(
                    TrainStatusError.RATE_LIMITED,
                    "Daily request limit reached"
                )
                HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN ->
                    throw TrainStatusException(
                        TrainStatusError.PROVIDER_ERROR,
                        "The API key was rejected"
                    )
                HttpURLConnection.HTTP_NOT_FOUND -> throw TrainStatusException(
                    TrainStatusError.TRAIN_NOT_FOUND,
                    "$endpoint has no record for that train"
                )
                else -> throw TrainStatusException(
                    TrainStatusError.PROVIDER_ERROR,
                    "$endpoint returned HTTP $code"
                )
            }

            return BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
        } catch (e: TrainStatusException) {
            throw e
        } catch (e: CancellationException) {
            // The screen moved on. Not a failure, and rethrowing keeps structured
            // concurrency intact.
            throw e
        } catch (e: SSLException) {
            // Rethrown unwrapped so [get] can decide whether to retry in cleartext.
            throw e
        } catch (e: SocketTimeoutException) {
            throw TrainStatusException(TrainStatusError.TIMEOUT, "$endpoint timed out", e)
        } catch (e: UnknownHostException) {
            throw TrainStatusException(TrainStatusError.NETWORK_UNAVAILABLE, cause = e)
        } catch (e: UnknownServiceException) {
            // Android refused a cleartext request, which means the network security config
            // does not cover this host. A configuration fault, not a connectivity one.
            throw TrainStatusException(
                TrainStatusError.PROVIDER_ERROR,
                "Cleartext requests to $endpoint are not permitted",
                e
            )
        } catch (e: IOException) {
            throw TrainStatusException(TrainStatusError.NETWORK_UNAVAILABLE, cause = e)
        } catch (e: Exception) {
            throw TrainStatusException(TrainStatusError.UNKNOWN, cause = e)
        } finally {
            connection?.disconnect()
        }
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object {
        const val PROVIDER_NAME = "IndianRailAPI"

        private const val HTTPS_BASE = "https://indianrailapi.com/api/v2"
        private const val HTTP_BASE = "http://indianrailapi.com/api/v2"
        private const val USER_AGENT = "TripCompanion/1.0 (Android)"

        /** Longer than the search timeouts: this endpoint is slower and less pressing. */
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val READ_TIMEOUT_MS = 8_000
        private const val HTTP_TOO_MANY_REQUESTS = 429
    }
}
