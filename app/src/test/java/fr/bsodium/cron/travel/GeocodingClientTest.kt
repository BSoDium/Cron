package fr.bsodium.cron.travel

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GeocodingClientTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    /** GeocodingClient hardcodes the maps.googleapis.com host, so requests are redirected to the
     *  MockWebServer via an interceptor that keeps the original path and query intact. */
    private fun client(): GeocodingClient {
        val http = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val redirected = chain.request().url.newBuilder()
                    .scheme("http")
                    .host(server.hostName)
                    .port(server.port)
                    .build()
                chain.proceed(chain.request().newBuilder().url(redirected).build())
            }
            .build()
        return GeocodingClient(apiKey = "test-key", http = http)
    }

    @Test
    fun successful_response_returns_first_result() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {"results":[{"formatted_address":"1 Rue de Rivoli, Paris","geometry":{"location":{"lat":48.8566,"lng":2.3522}}}]}
                """.trimIndent(),
            ),
        )

        val result = client().geocode("1 Rue de Rivoli").getOrThrow()
        assertEquals(48.8566, result.lat, 0.0001)
        assertEquals(2.3522, result.lng, 0.0001)
        assertEquals("1 Rue de Rivoli, Paris", result.formattedAddress)

        val request = server.takeRequest()
        assertTrue(request.path.orEmpty().contains("address=1"))
        assertTrue(request.path.orEmpty().contains("key=test-key"))
    }

    @Test
    fun bias_adds_a_bounds_parameter() = runTest {
        server.enqueue(MockResponse().setBody("""{"results":[{"geometry":{"location":{"lat":1.0,"lng":2.0}}}]}"""))

        client().geocode("Hauptbahnhof", bias = LatLng(46.624, 14.308)).getOrThrow()

        val request = server.takeRequest()
        assertTrue(request.path.orEmpty().contains("bounds="))
    }

    @Test
    fun missing_results_key_is_a_failure() = runTest {
        server.enqueue(MockResponse().setBody("""{"status":"ZERO_RESULTS"}"""))
        val result = client().geocode("nowhere")
        assertTrue(result.isFailure)
    }

    @Test
    fun empty_results_array_is_a_failure() = runTest {
        server.enqueue(MockResponse().setBody("""{"results":[]}"""))
        val result = client().geocode("nowhere")
        assertTrue(result.isFailure)
    }

    @Test
    fun http_error_response_is_a_failure() = runTest {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error_message":"key invalid"}"""))
        val result = client().geocode("somewhere")
        assertTrue(result.isFailure)
    }

    @Test
    fun malformed_json_is_a_failure() = runTest {
        server.enqueue(MockResponse().setBody("not json"))
        val result = client().geocode("somewhere")
        assertTrue(result.isFailure)
    }

    @Test
    fun formatted_address_falls_back_to_the_query_when_missing() = runTest {
        server.enqueue(MockResponse().setBody("""{"results":[{"geometry":{"location":{"lat":1.0,"lng":2.0}}}]}"""))
        val result = client().geocode("Some Place").getOrThrow()
        assertEquals("Some Place", result.formattedAddress)
    }
}
