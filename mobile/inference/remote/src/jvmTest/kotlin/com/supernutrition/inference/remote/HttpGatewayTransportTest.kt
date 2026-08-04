package com.supernutrition.inference.remote

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The transport against a real socket.
 *
 * Uses the JDK's own `HttpServer` rather than a mock: the behaviour worth
 * pinning here — that a non-2xx body arrives on the error stream and must still
 * be read — only exists in `HttpURLConnection`, so a fake would assert the
 * mock's behaviour instead of the platform's.
 */
class HttpGatewayTransportTest {

    private fun withServer(handler: (HttpExchange) -> Unit, body: (baseUrl: String) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/gw/v1/inference/photo") { exchange ->
            try {
                handler(exchange)
            } finally {
                exchange.close()
            }
        }
        server.start()
        try {
            body("http://127.0.0.1:${server.address.port}")
        } finally {
            server.stop(0)
        }
    }

    private fun call(baseUrl: String) = runBlocking {
        HttpGatewayTransport(baseUrl).postInferencePhoto(
            GatewayCall(
                contextJson = """{"contractVersion":"mobile-api/v1"}""",
                payload = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()),
                contentType = "image/jpeg",
                requestId = "req_0000000000000001",
                contractVersion = "mobile-api/v1",
                authorization = "Bearer test.installation-0001",
            ),
        )
    }

    @Test
    fun readsASuccessBodyFromTheInputStream() {
        withServer({ exchange ->
            val payload = """{"ok":true}""".toByteArray()
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.write(payload)
        }) { baseUrl ->
            val response = call(baseUrl)
            assertEquals(200, response.status)
            assertEquals("""{"ok":true}""", response.body)
        }
    }

    @Test
    fun readsAnErrorBodyFromTheErrorStream() {
        // The contract envelope arrives on 4xx too, and HttpURLConnection puts
        // it on a different stream. Reading only inputStream would throw here
        // and turn a typed gateway error into a generic network failure.
        withServer({ exchange ->
            val payload = """{"error":{"gatewayCode":"rate-limited"}}""".toByteArray()
            exchange.sendResponseHeaders(429, payload.size.toLong())
            exchange.responseBody.write(payload)
        }) { baseUrl ->
            val response = call(baseUrl)
            assertEquals(429, response.status)
            assertTrue("rate-limited" in response.body, response.body)
        }
    }

    @Test
    fun toleratesAnEmptyBodyWithoutThrowing() {
        withServer({ exchange -> exchange.sendResponseHeaders(204, -1) }) { baseUrl ->
            val response = call(baseUrl)
            assertEquals(204, response.status)
            assertEquals("", response.body)
        }
    }

    @Test
    fun sendsTheEnvelopeAndPayloadAsDistinctMultipartParts() {
        var received = ""
        var authorization: String? = null
        var contractVersion: String? = null

        withServer({ exchange ->
            authorization = exchange.requestHeaders.getFirst("Authorization")
            contractVersion = exchange.requestHeaders.getFirst("X-Contract-Version")
            received = exchange.requestBody.readBytes().toString(Charsets.ISO_8859_1)
            exchange.sendResponseHeaders(200, 0)
        }) { baseUrl -> call(baseUrl) }

        assertEquals("Bearer test.installation-0001", authorization)
        assertEquals("mobile-api/v1", contractVersion)
        assertTrue("""name="context"""" in received, received)
        assertTrue("""name="payload"""" in received, received)
        assertTrue("Content-Type: image/jpeg" in received, received)
        // The JPEG magic must survive as bytes rather than being re-encoded.
        assertTrue("ÿØÿ" in received, "payload bytes were altered")
    }

    @Test
    fun surfacesAConnectionFailureAsAThrownFaultForTheEngineToType() {
        val closedPort = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            .also { it.start() }
            .let { server -> server.address.port.also { server.stop(0) } }

        val thrown = runCatching { call("http://127.0.0.1:$closedPort") }.exceptionOrNull()

        assertTrue(thrown != null, "expected a transport fault")
    }
}
