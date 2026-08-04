package com.supernutrition.inference.remote

import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The only HTTP-aware type in the client.
 *
 * It performs the multipart POST and returns status + body verbatim. All
 * interpretation — consent binding, envelope mapping, error taxonomy — lives in
 * [RemoteInferenceEngine], so contract behaviour stays testable without a
 * network and no transport type escapes this file (AD-4, AD-13).
 *
 * NOTE ON THE HTTP STACK. The architecture calls for Ktor/Ktorfit, and both are
 * already in the version catalog. `ktor-client-okhttp` is NOT in
 * `gradle/verification-metadata.xml`, so adding it fails strict dependency
 * verification — a build-policy change owned by a different slice (AD-26).
 * `HttpURLConnection` needs no new dependency and satisfies the same port, so
 * the swap to Ktor is a one-file change once the checksums are added.
 */
class HttpGatewayTransport(
    private val baseUrl: String,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 30_000,
    /**
     * Blocking I/O is confined here rather than left to the caller. The host
     * launches analysis on the Android main dispatcher, and `HttpURLConnection`
     * blocks, so without this every device call dies with
     * NetworkOnMainThreadException — which the engine then reports as an
     * ordinary transport fault, hiding the real cause. A suspend function that
     * blocks owns its dispatcher.
     */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : GatewayTransport {

    override suspend fun postInferencePhoto(call: GatewayCall): GatewayHttpResponse =
        withContext(ioDispatcher) { post(call) }

    private fun post(call: GatewayCall): GatewayHttpResponse {
        val boundary = "----SuperNutrition${System.nanoTime()}"
        val connection = (URL("$baseUrl/gw/v1/inference/photo").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("Authorization", call.authorization)
            setRequestProperty("X-Contract-Version", call.contractVersion)
            setRequestProperty("X-Request-Id", call.requestId)
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

        try {
            DataOutputStream(connection.outputStream).use { out ->
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"context\"\r\n\r\n")
                out.write(call.contextJson.toByteArray(Charsets.UTF_8))
                out.writeBytes("\r\n--$boundary\r\n")
                out.writeBytes(
                    "Content-Disposition: form-data; name=\"payload\"; filename=\"meal.jpg\"\r\n",
                )
                out.writeBytes("Content-Type: ${call.contentType}\r\n\r\n")
                out.write(call.payload)
                out.writeBytes("\r\n--$boundary--\r\n")
                out.flush()
            }

            val status = connection.responseCode
            // A non-2xx body arrives on the error stream; both carry a contract
            // envelope, so both are read rather than only the success path.
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return GatewayHttpResponse(status, body)
        } finally {
            connection.disconnect()
        }
    }
}
